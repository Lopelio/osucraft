package com.osucraft.core;

import java.util.ArrayList;
import java.util.List;

/** Graba notas tocando las teclas al ritmo del metrónomo (se ajustan a 1/4 de tiempo). */
public final class ManiaRecorder {
    public final int keys;
    public final double bpm;
    public final double offset;
    public final double beat;
    public final List<ManiaEngine.Note> notes = new ArrayList<>();
    private final ManiaEngine.Note[] current;
    private final double[] pressedAt;

    public ManiaRecorder(int keys, double bpm, double offset) {
        this.keys = keys;
        this.bpm = bpm;
        this.offset = offset;
        this.beat = 60000.0 / bpm;
        this.current = new ManiaEngine.Note[keys];
        this.pressedAt = new double[keys];
    }

    private int snap(double t) {
        double q = beat / 4.0;
        return (int) Math.round(offset + Math.round((t - offset) / q) * q);
    }

    public void press(int lane, double t) {
        if (lane < 0 || lane >= keys || current[lane] != null) return;
        int st = snap(t);
        for (ManiaEngine.Note n : notes) {
            if (n.lane == lane && n.time == st) return; // ya existe
        }
        ManiaEngine.Note n = new ManiaEngine.Note(lane, st, st, false);
        notes.add(n);
        current[lane] = n;
        pressedAt[lane] = t;
    }

    public void release(int lane, double t) {
        if (lane < 0 || lane >= keys) return;
        ManiaEngine.Note n = current[lane];
        current[lane] = null;
        if (n == null) return;
        if (t - pressedAt[lane] >= beat / 2.0) {
            int end = snap(t);
            if (end > n.time) {
                n.hold = true;
                n.end = end;
            }
        }
    }

    public Beatmap build(String name, double od) {
        Beatmap bm = new Beatmap();
        bm.title = name;
        bm.artist = "OsuCraft";
        bm.creator = "OsuCraft";
        bm.version = "Grabado";
        bm.mode = 3;
        bm.cs = keys;
        bm.od = od;
        bm.audioFile = "";
        TimingPoint tp = new TimingPoint();
        tp.time = offset;
        tp.beatLength = beat;
        bm.timing.add(tp);
        List<ManiaEngine.Note> sorted = new ArrayList<>(notes);
        sorted.sort((a, b) -> Integer.compare(a.time, b.time));
        for (ManiaEngine.Note n : sorted) {
            HitObject o = new HitObject();
            o.kind = n.hold ? HitObject.Kind.HOLD : HitObject.Kind.CIRCLE;
            o.column = n.lane;
            o.x = (n.lane * 512.0 + 256.0) / keys;
            o.y = 192;
            o.time = n.time;
            o.endTime = n.hold ? n.end : n.time;
            bm.objects.add(o);
        }
        bm.prepare();
        bm.objectCount = bm.objects.size();
        return bm;
    }
}
