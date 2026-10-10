package com.osucraft.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Beatmap {
    public String title = "";
    public String artist = "";
    public String creator = "";
    public String version = "";
    public String audioFile = "";
    public int mode;
    public int audioLeadIn;
    public int previewTime;
    public double hp = 5;
    public double cs = 4;
    public double od = 5;
    public double ar = -1;
    public double sliderMultiplier = 1.4;
    public double sliderTickRate = 1;
    public final List<TimingPoint> timing = new ArrayList<>();
    public final List<HitObject> objects = new ArrayList<>();

    /** Ubicación en disco (si viene de la biblioteca). */
    public Path file;
    public Path dir;
    /** Cantidad de objetos cuando solo se leyó la cabecera. */
    public int objectCount;

    public double ar() {
        return ar < 0 ? od : ar;
    }

    public int keys() {
        return Math.max(1, Math.min(10, (int) Math.round(cs)));
    }

    public boolean hasAudio() {
        return audioFile != null && !audioFile.isBlank();
    }

    public double bpm() {
        double bl = beatLengthAt(objects.isEmpty() ? 0 : objects.get(0).time);
        return 60000.0 / bl;
    }

    public double beatLengthAt(double t) {
        double result = Double.NaN;
        double first = Double.NaN;
        for (TimingPoint tp : timing) {
            if (!tp.uninherited || tp.beatLength <= 0) continue;
            if (Double.isNaN(first)) first = tp.beatLength;
            if (tp.time <= t) result = tp.beatLength;
        }
        if (Double.isNaN(result)) result = Double.isNaN(first) ? 500 : first;
        return result;
    }

    public double svAt(double t) {
        double sv = 1.0;
        for (TimingPoint tp : timing) {
            if (tp.time > t) break;
            if (tp.uninherited) {
                sv = 1.0;
            } else if (tp.beatLength < 0) {
                sv = Math.max(0.1, Math.min(10.0, 100.0 / -tp.beatLength));
            }
        }
        return sv;
    }

    public int lastTime() {
        int last = 0;
        for (HitObject o : objects) last = Math.max(last, Math.max(o.time, o.endTime));
        return last;
    }

    public String modeLabel() {
        return switch (mode) {
            case 0 -> "Standard";
            case 1 -> "Taiko";
            case 2 -> "Catch";
            default -> "Mania " + keys() + "K";
        };
    }

    public String displayName() {
        String a = artist.isBlank() ? "?" : artist;
        String t = title.isBlank() ? "?" : title;
        return a + " - " + t + " [" + version + "]";
    }

    /** Ordena, calcula combos, duración de sliders y columnas de mania. Llamar luego de parsear. */
    public void prepare() {
        timing.sort(Comparator.comparingDouble(tp -> tp.time));
        objects.sort(Comparator.comparingInt(o -> o.time));
        int keys = keys();
        int combo = 0;
        boolean startNew = true;
        for (HitObject o : objects) {
            if (startNew || o.newCombo) combo = 1;
            else combo++;
            o.comboNumber = combo;
            startNew = o.kind == HitObject.Kind.SPINNER;
            switch (o.kind) {
                case SLIDER -> {
                    double sv = svAt(o.time);
                    double bl = beatLengthAt(o.time);
                    double velocity = 100.0 * sliderMultiplier * sv;
                    o.slideDuration = Math.max(1.0, o.pixelLength / velocity * bl);
                    o.endTime = o.time + (int) Math.round(o.slideDuration * o.slides);
                    o.tickDistance = velocity / Math.max(0.1, sliderTickRate);
                    o.path = SliderPath.build(o.curveType, o.x, o.y, o.points, o.pixelLength);
                }
                case CIRCLE -> o.endTime = o.time;
                default -> { }
            }
            o.column = Math.max(0, Math.min(keys - 1, (int) Math.floor(o.x * keys / 512.0)));
        }
    }
}
