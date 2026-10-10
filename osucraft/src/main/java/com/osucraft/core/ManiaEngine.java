package com.osucraft.core;

import java.util.ArrayList;
import java.util.List;

/** Lógica de juego tipo osu!mania: carriles, notas largas y juicios por ventanas de tiempo (ms). */
public final class ManiaEngine {
    public static final int PENDING = 0;
    public static final int HOLDING = 1;
    public static final int DONE = 2;

    public static final class Note {
        public int lane;
        public int time;
        public int end;
        public boolean hold;
        public int state = PENDING;
        public Judgement head;
        public Judgement result;

        public Note(int lane, int time, int end, boolean hold) {
            this.lane = lane;
            this.time = time;
            this.end = end;
            this.hold = hold;
        }
    }

    public final int keys;
    public final List<Note> notes;
    public final Score score = new Score(true);
    public final double wMax;
    public final double w300;
    public final double w200;
    public final double w100;
    public final double w50;
    public final double wMiss;
    public static final double TAIL_SCALE = 1.5;

    public Judgement lastJudgement;
    public double lastJudgementTime = -1e9;
    public final double[] laneFlash;

    private final List<List<Note>> byLane = new ArrayList<>();
    private final int[] laneHead;

    public ManiaEngine(int keys, List<Note> source, double od) {
        this.keys = keys;
        this.notes = new ArrayList<>(source);
        this.notes.sort((a, b) -> a.time != b.time ? Integer.compare(a.time, b.time) : Integer.compare(a.lane, b.lane));
        double d = Math.max(0, Math.min(10, od));
        wMax = 16;
        w300 = 64 - 3 * d;
        w200 = 97 - 3 * d;
        w100 = 127 - 3 * d;
        w50 = 151 - 3 * d;
        wMiss = 188 - 3 * d;
        laneHead = new int[keys];
        laneFlash = new double[keys];
        for (int i = 0; i < keys; i++) {
            byLane.add(new ArrayList<>());
            laneFlash[i] = -1e9;
        }
        for (Note n : notes) {
            if (n.lane >= 0 && n.lane < keys) byLane.get(n.lane).add(n);
        }
    }

    private Note head(int lane) {
        List<Note> l = byLane.get(lane);
        int h = laneHead[lane];
        return h < l.size() ? l.get(h) : null;
    }

    public Judgement judge(double absDiff) {
        if (absDiff <= wMax) return Judgement.MAX;
        if (absDiff <= w300) return Judgement.J300;
        if (absDiff <= w200) return Judgement.J200;
        if (absDiff <= w100) return Judgement.J100;
        return Judgement.J50;
    }

    public void press(int lane, double t) {
        if (lane < 0 || lane >= keys) return;
        update(t);
        laneFlash[lane] = t;
        Note n = head(lane);
        if (n == null || n.state != PENDING) return;
        double dt = t - n.time;
        if (-dt > wMiss) return; // demasiado temprano: se ignora
        double ad = Math.abs(dt);
        if (ad <= w50) {
            Judgement j = judge(ad);
            if (!n.hold) {
                finish(n, j, t);
            } else {
                n.state = HOLDING;
                n.head = j;
                lastJudgement = j;
                lastJudgementTime = t;
            }
        } else {
            finish(n, Judgement.MISS, t);
        }
    }

    public void release(int lane, double t) {
        if (lane < 0 || lane >= keys) return;
        update(t);
        Note n = head(lane);
        if (n == null || n.state != HOLDING) return;
        double dt = t - n.end;
        double scaled = Math.abs(dt) / TAIL_SCALE;
        if (scaled <= w50) {
            finish(n, Judgement.worse(n.head, judge(scaled)), t);
        } else if (dt < 0) {
            finish(n, Judgement.MISS, t); // soltó demasiado pronto
        } else {
            finish(n, Judgement.worse(n.head, Judgement.J50), t);
        }
    }

    public void update(double t) {
        for (int lane = 0; lane < keys; lane++) {
            Note n;
            while ((n = head(lane)) != null) {
                if (n.state == PENDING && t - n.time > w50) {
                    finish(n, Judgement.MISS, t);
                } else if (n.state == HOLDING && t > n.end + w50 * TAIL_SCALE) {
                    finish(n, Judgement.worse(n.head, Judgement.J50), t);
                } else {
                    break;
                }
            }
        }
    }

    private void finish(Note n, Judgement j, double t) {
        n.state = DONE;
        n.result = j;
        score.add(j);
        lastJudgement = j;
        lastJudgementTime = t;
        laneHead[n.lane]++;
    }

    public boolean allDone() {
        for (int i = 0; i < keys; i++) {
            if (head(i) != null) return false;
        }
        return true;
    }
}
