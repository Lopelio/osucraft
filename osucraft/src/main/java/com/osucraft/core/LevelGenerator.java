package com.osucraft.core;

import java.util.Random;

/** Generador de niveles básicos (sin música: se usa metrónomo). */
public final class LevelGenerator {
    private LevelGenerator() { }

    public enum Pattern {
        ESCALERA("Escalera"), SALTOS("Saltos"), STREAM("Stream"), ALEATORIO("Aleatorio");

        public final String label;

        Pattern(String label) {
            this.label = label;
        }

        public Pattern next() {
            Pattern[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public static final int START_OFFSET = 2000;

    public static Beatmap generate(int mode, String name, double bpm, int keys, int bars, Pattern pattern, long seed) {
        bpm = Math.max(40, Math.min(300, bpm));
        bars = Math.max(1, Math.min(200, bars));
        keys = Math.max(1, Math.min(10, keys));
        Beatmap bm = new Beatmap();
        bm.title = name;
        bm.artist = "OsuCraft";
        bm.creator = "OsuCraft";
        bm.version = pattern.label;
        bm.mode = mode == 3 ? 3 : 0;
        bm.audioFile = "";
        bm.cs = bm.mode == 3 ? keys : 4;
        bm.od = bm.mode == 3 ? 8 : 6;
        bm.ar = 7;
        bm.hp = 5;
        bm.audioLeadIn = 0;
        double beat = 60000.0 / bpm;
        TimingPoint tp = new TimingPoint();
        tp.time = START_OFFSET;
        tp.beatLength = beat;
        tp.uninherited = true;
        tp.meter = 4;
        bm.timing.add(tp);
        Random rnd = new Random(seed);
        if (bm.mode == 3) maniaPattern(bm, beat, keys, bars, pattern, rnd);
        else stdPattern(bm, beat, bars, pattern, rnd);
        bm.prepare();
        bm.objectCount = bm.objects.size();
        return bm;
    }

    private static HitObject tap(Beatmap bm, int keys, int lane, int time) {
        HitObject o = new HitObject();
        o.kind = HitObject.Kind.CIRCLE;
        o.column = lane;
        o.x = (lane * 512.0 + 256.0) / keys;
        o.y = 192;
        o.time = time;
        o.endTime = time;
        bm.objects.add(o);
        return o;
    }

    private static void hold(Beatmap bm, int keys, int lane, int time, int end) {
        HitObject o = tap(bm, keys, lane, time);
        o.kind = HitObject.Kind.HOLD;
        o.endTime = end;
    }

    private static void maniaPattern(Beatmap bm, double beat, int keys, int bars, Pattern pattern, Random rnd) {
        double step;
        int steps;
        switch (pattern) {
            case SALTOS -> { step = beat; steps = bars * 4; }
            case STREAM -> { step = beat / 4; steps = bars * 16; }
            default -> { step = beat / 2; steps = bars * 8; }
        }
        double[] busy = new double[keys];
        int prev = -1;
        int prev2 = -1;
        for (int i = 0; i < steps; i++) {
            int time = (int) Math.round(START_OFFSET + beat * 2 + i * step);
            switch (pattern) {
                case ESCALERA -> {
                    int period = Math.max(1, 2 * (keys - 1));
                    int idx = i % period;
                    int lane = keys == 1 ? 0 : (idx < keys ? idx : period - idx);
                    tap(bm, keys, lane, time);
                }
                case SALTOS -> {
                    int pairs = Math.max(1, keys / 2);
                    int p = i % pairs;
                    int a = p;
                    int b = keys - 1 - p;
                    tap(bm, keys, a, time);
                    if (b != a) tap(bm, keys, b, time);
                }
                case STREAM -> {
                    int lane;
                    int guard = 0;
                    do {
                        lane = rnd.nextInt(keys);
                        guard++;
                    } while (keys > 2 && (lane == prev || lane == prev2) && guard < 20);
                    prev2 = prev;
                    prev = lane;
                    tap(bm, keys, lane, time);
                }
                default -> {
                    int lane;
                    int guard = 0;
                    do {
                        lane = rnd.nextInt(keys);
                        guard++;
                    } while ((lane == prev && keys > 1 || busy[lane] > time - 40) && guard < 30);
                    if (busy[lane] > time - 40) continue;
                    prev = lane;
                    if (i % 8 == 7) {
                        int end = (int) Math.round(time + beat);
                        hold(bm, keys, lane, time, end);
                        busy[lane] = end;
                    } else {
                        tap(bm, keys, lane, time);
                        busy[lane] = time;
                    }
                }
            }
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void stdPattern(Beatmap bm, double beat, int bars, Pattern pattern, Random rnd) {
        double step;
        int steps;
        switch (pattern) {
            case SALTOS -> { step = beat; steps = bars * 4; }
            case STREAM -> { step = beat / 4; steps = bars * 16; }
            default -> { step = beat / 2; steps = bars * 8; }
        }
        double px = 256;
        double py = 192;
        int count = 0;
        for (int i = 0; i < steps; i++) {
            int time = (int) Math.round(START_OFFSET + beat * 2 + i * step);
            double x;
            double y;
            switch (pattern) {
                case ESCALERA -> {
                    double a = i * 0.55;
                    x = 256 + Math.cos(a) * 170;
                    y = 192 + Math.sin(a) * 120;
                }
                case SALTOS -> {
                    x = (i % 2 == 0) ? 100 : 412;
                    y = 192 + Math.sin(i * 0.9) * 110;
                }
                case STREAM -> {
                    x = 60 + ((i * 14) % 392);
                    y = 192 + Math.sin(i * 0.35) * 110;
                }
                default -> {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    double d = 70 + rnd.nextDouble() * 90;
                    x = px + Math.cos(a) * d;
                    y = py + Math.sin(a) * d;
                }
            }
            x = clamp(x, 40, 472);
            y = clamp(y, 40, 344);
            HitObject o = new HitObject();
            o.x = x;
            o.y = y;
            o.time = time;
            o.endTime = time;
            o.newCombo = count % 4 == 0;
            count++;
            if (pattern == Pattern.ALEATORIO && i % 6 == 5) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double tx = clamp(x + Math.cos(ang) * 150, 40, 472);
                double ty = clamp(y + Math.sin(ang) * 150, 40, 344);
                o.kind = HitObject.Kind.SLIDER;
                o.curveType = 'L';
                o.points.add(new double[]{tx, ty});
                o.slides = 1;
                o.pixelLength = Math.hypot(tx - x, ty - y);
                bm.objects.add(o);
                px = tx;
                py = ty;
                double dur = o.pixelLength / (100.0 * bm.sliderMultiplier) * beat;
                i += (int) Math.ceil(dur / step);
            } else {
                o.kind = HitObject.Kind.CIRCLE;
                bm.objects.add(o);
                px = x;
                py = y;
            }
        }
    }
}
