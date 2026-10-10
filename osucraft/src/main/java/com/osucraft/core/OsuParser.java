package com.osucraft.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Lector de archivos .osu (formatos v3 a v14). */
public final class OsuParser {
    private OsuParser() { }

    public static Beatmap parse(Path file, boolean full) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Beatmap bm = parse(lines, full);
        bm.file = file;
        bm.dir = file.getParent();
        return bm;
    }

    public static Beatmap parse(List<String> lines, boolean full) {
        Beatmap bm = new Beatmap();
        String section = "";
        boolean first = true;
        boolean valid = false;
        for (String raw : lines) {
            String line = raw;
            if (first) {
                first = false;
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
                if (line.trim().startsWith("osu file format")) valid = true;
            }
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1);
                continue;
            }
            try {
                switch (section) {
                    case "General", "Metadata", "Difficulty" -> keyValue(bm, section, line);
                    case "TimingPoints" -> timing(bm, line);
                    case "HitObjects" -> {
                        if (full) hitObject(bm, line);
                        else bm.objectCount++;
                    }
                    default -> { }
                }
            } catch (RuntimeException ignored) {
                // línea corrupta: se ignora
            }
        }
        if (!valid && bm.title.isEmpty() && bm.objects.isEmpty() && bm.objectCount == 0) {
            throw new IllegalArgumentException("No es un archivo .osu válido");
        }
        if (full) {
            bm.objectCount = bm.objects.size();
            bm.prepare();
        }
        return bm;
    }

    private static void keyValue(Beatmap bm, String section, String line) {
        int idx = line.indexOf(':');
        if (idx < 0) return;
        String k = line.substring(0, idx).trim();
        String v = line.substring(idx + 1).trim();
        switch (section) {
            case "General" -> {
                switch (k) {
                    case "AudioFilename" -> bm.audioFile = v;
                    case "AudioLeadIn" -> bm.audioLeadIn = (int) Double.parseDouble(v);
                    case "PreviewTime" -> bm.previewTime = (int) Double.parseDouble(v);
                    case "Mode" -> bm.mode = Integer.parseInt(v);
                    default -> { }
                }
            }
            case "Metadata" -> {
                switch (k) {
                    case "Title" -> bm.title = v;
                    case "Artist" -> bm.artist = v;
                    case "Creator" -> bm.creator = v;
                    case "Version" -> bm.version = v;
                    default -> { }
                }
            }
            case "Difficulty" -> {
                double d = Double.parseDouble(v);
                switch (k) {
                    case "HPDrainRate" -> bm.hp = d;
                    case "CircleSize" -> bm.cs = d;
                    case "OverallDifficulty" -> bm.od = d;
                    case "ApproachRate" -> bm.ar = d;
                    case "SliderMultiplier" -> bm.sliderMultiplier = d;
                    case "SliderTickRate" -> bm.sliderTickRate = d;
                    default -> { }
                }
            }
            default -> { }
        }
    }

    private static void timing(Beatmap bm, String line) {
        String[] p = line.split(",");
        if (p.length < 2) return;
        TimingPoint tp = new TimingPoint();
        tp.time = Double.parseDouble(p[0].trim());
        tp.beatLength = Double.parseDouble(p[1].trim());
        if (p.length > 2) tp.meter = (int) Double.parseDouble(p[2].trim());
        tp.uninherited = p.length > 6 ? !p[6].trim().equals("0") : tp.beatLength >= 0;
        bm.timing.add(tp);
    }

    private static void hitObject(Beatmap bm, String line) {
        String[] p = line.split(",");
        if (p.length < 4) return;
        HitObject o = new HitObject();
        o.x = Double.parseDouble(p[0].trim());
        o.y = Double.parseDouble(p[1].trim());
        o.time = (int) Math.round(Double.parseDouble(p[2].trim()));
        int type = Integer.parseInt(p[3].trim());
        o.newCombo = (type & 4) != 0;
        if ((type & 128) != 0) {
            o.kind = HitObject.Kind.HOLD;
            String end = p.length > 5 ? p[5].split(":")[0].trim() : "";
            o.endTime = end.isEmpty() ? o.time : (int) Math.round(Double.parseDouble(end));
        } else if ((type & 8) != 0) {
            o.kind = HitObject.Kind.SPINNER;
            o.endTime = p.length > 5 ? (int) Math.round(Double.parseDouble(p[5].trim())) : o.time + 1000;
        } else if ((type & 2) != 0 && p.length >= 8) {
            o.kind = HitObject.Kind.SLIDER;
            String[] c = p[5].split("\\|");
            o.curveType = c.length > 0 && !c[0].isEmpty() ? c[0].charAt(0) : 'L';
            for (int i = 1; i < c.length; i++) {
                String[] xy = c[i].split(":");
                if (xy.length < 2) continue;
                o.points.add(new double[]{Double.parseDouble(xy[0]), Double.parseDouble(xy[1])});
            }
            o.slides = Math.max(1, Integer.parseInt(p[6].trim()));
            o.pixelLength = Double.parseDouble(p[7].trim());
            if (o.points.isEmpty()) {
                o.kind = HitObject.Kind.CIRCLE;
            }
        } else {
            o.kind = HitObject.Kind.CIRCLE;
        }
        bm.objects.add(o);
    }
}
