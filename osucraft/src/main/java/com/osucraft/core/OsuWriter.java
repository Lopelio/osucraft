package com.osucraft.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Escribe un Beatmap como archivo .osu estándar (compatible con osu!). */
public final class OsuWriter {
    private OsuWriter() { }

    public static void write(Beatmap bm, Path file) throws IOException {
        Files.writeString(file, toText(bm), StandardCharsets.UTF_8);
    }

    private static String num(double d) {
        if (d == Math.rint(d)) return Long.toString((long) d);
        return String.format(Locale.ROOT, "%.4f", d);
    }

    public static String toText(Beatmap bm) {
        StringBuilder sb = new StringBuilder();
        sb.append("osu file format v14\n\n[General]\n");
        sb.append("AudioFilename: ").append(bm.audioFile == null ? "" : bm.audioFile).append('\n');
        sb.append("AudioLeadIn: ").append(bm.audioLeadIn).append('\n');
        sb.append("PreviewTime: ").append(bm.previewTime).append('\n');
        sb.append("Countdown: 0\nSampleSet: Normal\nStackLeniency: 0.7\n");
        sb.append("Mode: ").append(bm.mode).append("\nLetterboxInBreaks: 0\n\n[Editor]\nDistanceSpacing: 1\nBeatDivisor: 4\nGridSize: 4\nTimelineZoom: 1\n\n");
        sb.append("[Metadata]\n");
        sb.append("Title:").append(bm.title).append("\nTitleUnicode:").append(bm.title).append('\n');
        sb.append("Artist:").append(bm.artist).append("\nArtistUnicode:").append(bm.artist).append('\n');
        sb.append("Creator:").append(bm.creator).append("\nVersion:").append(bm.version).append("\nSource:\nTags:\n\n");
        sb.append("[Difficulty]\n");
        sb.append("HPDrainRate:").append(num(bm.hp)).append("\nCircleSize:").append(num(bm.cs)).append('\n');
        sb.append("OverallDifficulty:").append(num(bm.od)).append("\nApproachRate:").append(num(bm.ar())).append('\n');
        sb.append("SliderMultiplier:").append(num(bm.sliderMultiplier)).append("\nSliderTickRate:").append(num(bm.sliderTickRate)).append("\n\n");
        sb.append("[Events]\n//Background and Video events\n\n[TimingPoints]\n");
        for (TimingPoint tp : bm.timing) {
            sb.append(num(tp.time)).append(',').append(num(tp.beatLength)).append(',').append(tp.meter)
                    .append(",1,0,100,").append(tp.uninherited ? 1 : 0).append(",0\n");
        }
        sb.append("\n[HitObjects]\n");
        int keys = bm.keys();
        for (HitObject o : bm.objects) {
            switch (o.kind) {
                case HOLD -> {
                    int x = (int) ((o.column * 512.0 + 256.0) / keys);
                    sb.append(x).append(",192,").append(o.time).append(",128,0,").append(o.endTime).append(":0:0:0:0:\n");
                }
                case SPINNER -> sb.append("256,192,").append(o.time).append(",12,0,").append(o.endTime).append(",0:0:0:0:\n");
                case SLIDER -> {
                    sb.append((int) o.x).append(',').append((int) o.y).append(',').append(o.time)
                            .append(o.newCombo ? ",6,0," : ",2,0,").append(o.curveType);
                    for (double[] p : o.points) sb.append('|').append((int) p[0]).append(':').append((int) p[1]);
                    sb.append(',').append(o.slides).append(',').append(num(o.pixelLength)).append('\n');
                }
                default -> {
                    int x = bm.mode == 3 ? (int) ((o.column * 512.0 + 256.0) / keys) : (int) o.x;
                    int y = bm.mode == 3 ? 192 : (int) o.y;
                    sb.append(x).append(',').append(y).append(',').append(o.time)
                            .append(o.newCombo ? ",5,0,0:0:0:0:\n" : ",1,0,0:0:0:0:\n");
                }
            }
        }
        return sb.toString();
    }
}
