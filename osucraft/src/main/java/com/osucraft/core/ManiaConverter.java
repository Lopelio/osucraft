package com.osucraft.core;

import java.util.ArrayList;
import java.util.List;

/** Convierte cualquier beatmap en notas de carriles (mania). Los mapas standard se "adaptan" por posición X. */
public final class ManiaConverter {
    private ManiaConverter() { }

    public static List<ManiaEngine.Note> convert(Beatmap bm, int keys) {
        List<ManiaEngine.Note> out = new ArrayList<>();
        int[] busy = new int[keys];
        java.util.Arrays.fill(busy, Integer.MIN_VALUE / 2);
        boolean native3 = bm.mode == 3;

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        for (HitObject o : bm.objects) {
            minX = Math.min(minX, o.x);
            maxX = Math.max(maxX, o.x);
        }
        boolean flat = !native3 && (maxX - minX) < 50;

        int idx = 0;
        for (HitObject o : bm.objects) {
            idx++;
            int base;
            boolean hold = false;
            int end = o.time;
            if (native3) {
                base = Math.max(0, Math.min(keys - 1, o.column));
                if (o.kind == HitObject.Kind.HOLD && o.endTime - o.time >= 30) {
                    hold = true;
                    end = o.endTime;
                }
            } else {
                if (flat) {
                    base = (idx * 3 + idx / keys) % keys;
                } else {
                    base = Math.max(0, Math.min(keys - 1, (int) Math.floor(o.x * keys / 512.0)));
                }
                if (o.kind == HitObject.Kind.SPINNER) base = keys / 2;
                int dur = o.endTime - o.time;
                if ((o.kind == HitObject.Kind.SLIDER || o.kind == HitObject.Kind.SPINNER || o.kind == HitObject.Kind.HOLD) && dur >= 120) {
                    hold = true;
                    end = o.endTime;
                }
            }
            int lane = -1;
            for (int d = 0; d < keys && lane < 0; d++) {
                int a = base - d;
                int b = base + d;
                if (a >= 0 && o.time >= busy[a] + 30) lane = a;
                else if (b < keys && o.time >= busy[b] + 30) lane = b;
                if (native3) break; // en mania nativo no se mueven las notas
            }
            if (lane < 0) continue;
            busy[lane] = hold ? end : o.time;
            out.add(new ManiaEngine.Note(lane, o.time, end, hold));
        }
        return out;
    }
}
