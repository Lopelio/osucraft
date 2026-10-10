package com.osucraft.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de juego tipo osu!standard (círculos, sliders y spinners).
 * Todas las coordenadas del cursor están en píxeles osu (campo de 512x384).
 */
public final class StdEngine {
    public static final int NEW = 0;
    public static final int ACTIVE = 1;
    public static final int DONE = 2;

    public static final class Obj {
        public final HitObject src;
        public int state = NEW;
        public Judgement result;
        public double doneTime = -1e9;
        // sliders
        public double[] cpTime = new double[0];
        public byte[] cpType = new byte[0];
        public int cpIndex;
        public int cpHit;
        public int cpTotal;
        public boolean tracking;
        public boolean headHit;
        public double endExact;
        // spinners
        public double rotations;
        public double needed = 1;
        public double lastAngle;
        public boolean angleValid;

        Obj(HitObject src) {
            this.src = src;
        }
    }

    public static final class Popup {
        public final double x;
        public final double y;
        public final double time;
        public final Judgement judgement;

        Popup(double x, double y, double time, Judgement judgement) {
            this.x = x;
            this.y = y;
            this.time = time;
            this.judgement = judgement;
        }
    }

    public final List<Obj> objects = new ArrayList<>();
    public final List<Popup> popups = new ArrayList<>();
    public final Score score = new Score(false);
    public final double radius;
    public final double followRadius;
    public final double w300;
    public final double w100;
    public final double w50;
    public final double preempt;
    public final double fadeIn;

    public int hitEvents;
    private int firstIdx;
    private final double[] tmp = new double[2];

    public StdEngine(Beatmap bm) {
        double od = Math.max(0, Math.min(10, bm.od));
        w300 = 80 - 6 * od;
        w100 = 140 - 8 * od;
        w50 = 200 - 10 * od;
        radius = 54.4 - 4.48 * Math.max(0, Math.min(10, bm.cs));
        followRadius = radius * 2.4;
        double ar = Math.max(0, Math.min(10, bm.ar()));
        if (ar < 5) {
            preempt = 1200 + 600 * (5 - ar) / 5;
            fadeIn = 800 + 400 * (5 - ar) / 5;
        } else if (ar == 5) {
            preempt = 1200;
            fadeIn = 800;
        } else {
            preempt = 1200 - 750 * (ar - 5) / 5;
            fadeIn = 800 - 500 * (ar - 5) / 5;
        }
        for (HitObject h : bm.objects) {
            if (h.kind == HitObject.Kind.SLIDER && h.path == null) h.kind = HitObject.Kind.CIRCLE;
            if (h.kind == HitObject.Kind.HOLD) h.kind = HitObject.Kind.CIRCLE;
            Obj o = new Obj(h);
            if (h.kind == HitObject.Kind.SLIDER && h.path != null) {
                buildCheckpoints(o);
            } else if (h.kind == HitObject.Kind.SPINNER) {
                double sec = Math.max(0.1, (h.endTime - h.time) / 1000.0);
                o.needed = Math.max(1.0, sec * (1.5 + 0.15 * od));
            }
            objects.add(o);
        }
    }

    private void buildCheckpoints(Obj o) {
        HitObject s = o.src;
        double dur = s.slideDuration;
        double len = s.path.length;
        List<double[]> cps = new ArrayList<>();
        if (s.tickDistance > 0 && len > 0) {
            for (int sl = 0; sl < s.slides; sl++) {
                for (double d = s.tickDistance; d < len - 8; d += s.tickDistance) {
                    double q = d / len;
                    double within = (sl % 2 == 0) ? q : 1 - q;
                    cps.add(new double[]{s.time + (sl + within) * dur, 0});
                }
            }
        }
        for (int sl = 0; sl < s.slides - 1; sl++) {
            cps.add(new double[]{s.time + (sl + 1) * dur, 1});
        }
        o.endExact = s.time + dur * s.slides;
        double endCheck = o.endExact - Math.min(36.0, dur * s.slides / 2.0);
        cps.add(new double[]{endCheck, 2});
        cps.sort((a, b) -> Double.compare(a[0], b[0]));
        o.cpTime = new double[cps.size()];
        o.cpType = new byte[cps.size()];
        for (int i = 0; i < cps.size(); i++) {
            o.cpTime[i] = cps.get(i)[0];
            o.cpType[i] = (byte) cps.get(i)[1];
        }
        o.cpTotal = cps.size() + 1; // + la cabeza
    }

    /** Posición de la bola del slider en el instante t (escribe en out). */
    public void ballPos(Obj o, double t, double[] out) {
        HitObject s = o.src;
        double elapsed = Math.max(0, Math.min(t - s.time, s.slideDuration * s.slides));
        int slide = (int) Math.min(s.slides - 1, Math.floor(elapsed / s.slideDuration));
        double local = (elapsed - slide * s.slideDuration) / s.slideDuration;
        double progress = (slide % 2 == 0) ? local : 1 - local;
        s.path.at(progress, out);
    }

    public void press(double t, double mx, double my) {
        update(t, mx, my, true);
        for (int i = firstIdx; i < objects.size(); i++) {
            Obj o = objects.get(i);
            if (o.src.time - t > w50) break;
            if (o.state != NEW || o.src.kind == HitObject.Kind.SPINNER) continue;
            double dx = mx - o.src.x;
            double dy = my - o.src.y;
            if (dx * dx + dy * dy <= radius * radius) {
                double ad = Math.abs(t - o.src.time);
                Judgement j = ad <= w300 ? Judgement.J300 : ad <= w100 ? Judgement.J100 : Judgement.J50;
                hitHead(o, j, t);
            }
            return; // bloqueo de notas: solo se considera el objeto más antiguo
        }
    }

    private void hitHead(Obj o, Judgement j, double t) {
        hitEvents++;
        if (o.src.kind == HitObject.Kind.SLIDER) {
            o.state = ACTIVE;
            o.headHit = true;
            o.cpHit++;
            score.addTick(true, 30);
            addPopup(o.src.x, o.src.y, t, j);
        } else {
            o.state = DONE;
            o.result = j;
            o.doneTime = t;
            score.add(j);
            addPopup(o.src.x, o.src.y, t, j);
        }
    }

    public void update(double t, double mx, double my, boolean held) {
        for (int i = firstIdx; i < objects.size(); i++) {
            Obj o = objects.get(i);
            HitObject h = o.src;
            if (h.time > t) break;
            if (o.state == DONE) continue;
            switch (h.kind) {
                case SLIDER -> updateSlider(o, t, mx, my, held);
                case SPINNER -> updateSpinner(o, t, mx, my, held);
                default -> {
                    if (o.state == NEW && t - h.time > w50) {
                        o.state = DONE;
                        o.result = Judgement.MISS;
                        o.doneTime = t;
                        score.add(Judgement.MISS);
                        addPopup(h.x, h.y, t, Judgement.MISS);
                    }
                }
            }
        }
        while (firstIdx < objects.size() && objects.get(firstIdx).state == DONE) firstIdx++;
        while (!popups.isEmpty() && popups.get(0).time < t - 1200) popups.remove(0);
    }

    private void updateSlider(Obj o, double t, double mx, double my, boolean held) {
        HitObject h = o.src;
        if (o.state == NEW && t - h.time > w50) {
            o.state = ACTIVE;
            score.addTick(false, 0);
            addPopup(h.x, h.y, t, Judgement.MISS);
        }
        // seguimiento actual (para dibujar el aro)
        if (t >= h.time && t <= o.endExact) {
            ballPos(o, t, tmp);
            o.tracking = held && Math.hypot(mx - tmp[0], my - tmp[1]) <= followRadius;
        } else {
            o.tracking = false;
        }
        while (o.cpIndex < o.cpTime.length && o.cpTime[o.cpIndex] <= t) {
            double ct = o.cpTime[o.cpIndex];
            ballPos(o, ct, tmp);
            boolean ok = held && Math.hypot(mx - tmp[0], my - tmp[1]) <= followRadius;
            if (ok) {
                o.cpHit++;
                score.addTick(true, o.cpType[o.cpIndex] == 0 ? 10 : 30);
            } else {
                score.addTick(false, 0);
            }
            o.cpIndex++;
        }
        if (t >= o.endExact) {
            double ratio = o.cpTotal == 0 ? 1 : (double) o.cpHit / o.cpTotal;
            Judgement j = ratio >= 0.999 ? Judgement.J300 : ratio >= 0.5 ? Judgement.J100 : ratio > 0 ? Judgement.J50 : Judgement.MISS;
            o.state = DONE;
            o.result = j;
            o.doneTime = t;
            o.tracking = false;
            score.addSilent(j);
            ballPos(o, o.endExact, tmp);
            addPopup(tmp[0], tmp[1], t, j);
        }
    }

    private void updateSpinner(Obj o, double t, double mx, double my, boolean held) {
        HitObject h = o.src;
        if (o.state == NEW) o.state = ACTIVE;
        double dx = mx - 256;
        double dy = my - 192;
        if (Math.hypot(dx, dy) > 6) {
            double ang = Math.atan2(dy, dx);
            if (o.angleValid && held && t <= h.endTime) {
                double d = ang - o.lastAngle;
                while (d > Math.PI) d -= Math.PI * 2;
                while (d < -Math.PI) d += Math.PI * 2;
                o.rotations += Math.abs(d) / (Math.PI * 2);
            }
            o.lastAngle = ang;
            o.angleValid = true;
        }
        if (t >= h.endTime) {
            double ratio = o.rotations / o.needed;
            Judgement j = ratio >= 1 ? Judgement.J300 : ratio >= 0.75 ? Judgement.J100 : ratio >= 0.4 ? Judgement.J50 : Judgement.MISS;
            o.state = DONE;
            o.result = j;
            o.doneTime = t;
            score.add(j);
            addPopup(256, 192, t, j);
        }
    }

    private void addPopup(double x, double y, double t, Judgement j) {
        popups.add(new Popup(x, y, t, j));
    }

    public boolean allDone() {
        for (int i = firstIdx; i < objects.size(); i++) {
            if (objects.get(i).state != DONE) return false;
        }
        return true;
    }
}
