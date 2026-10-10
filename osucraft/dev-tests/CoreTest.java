import com.osucraft.core.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;

public class CoreTest {
    static int fails = 0;
    static void check(boolean c, String m) { if (!c) { fails++; System.out.println("FAIL: " + m); } else System.out.println("ok: " + m); }

    public static void main(String[] a) throws Exception {
        // 1) parse a hand-written std map
        String std = "\uFEFFosu file format v14\n\n[General]\nAudioFilename: song.mp3\nMode: 0\n\n[Metadata]\nTitle:Test Song\nArtist:Tester\nCreator:me\nVersion:Easy\n\n[Difficulty]\nHPDrainRate:5\nCircleSize:4\nOverallDifficulty:6\nApproachRate:8\nSliderMultiplier:1.4\nSliderTickRate:1\n\n[TimingPoints]\n1000,500,4,1,0,100,1,0\n3000,-50,4,1,0,100,0,0\n\n[HitObjects]\n100,100,1000,5,0,0:0:0:0:\n200,100,1500,1,0,0:0:0:0:\n256,192,2000,2,0,B|300:200|350:150|400:100,2,200,0|0|0,0:0|0:0,0:0:0:0:\n100,300,4000,2,0,P|200:350|300:300,1,150\n256,192,6000,12,0,7500,0:0:0:0:\n50,50,8000,1,0,0:0:0:0:\n";
        List<String> lines = Arrays.asList(std.split("\n"));
        Beatmap bm = OsuParser.parse(lines, true);
        check(bm.title.equals("Test Song") && bm.artist.equals("Tester"), "metadata");
        check(bm.objects.size() == 6, "6 objects: " + bm.objects.size());
        HitObject sl = bm.objects.get(2);
        check(sl.kind == HitObject.Kind.SLIDER && sl.slides == 2, "slider parsed");
        // slider duration: 200 / (100*1.4*1) * 500 = 714.28 per slide
        check(Math.abs(sl.slideDuration - 714.2857) < 0.01, "slider dur " + sl.slideDuration);
        check(Math.abs(sl.path.length - 200) < 0.5, "slider length " + sl.path.length);
        check(bm.objects.get(3).path != null && Math.abs(bm.objects.get(3).path.length - 150) < 0.5, "perfect circle length " + bm.objects.get(3).path.length);
        check(bm.objects.get(4).kind == HitObject.Kind.SPINNER && bm.objects.get(4).endTime == 7500, "spinner");
        check(bm.objects.get(0).comboNumber == 1 && bm.objects.get(1).comboNumber == 2, "combo numbering");
        check(bm.objects.get(5).comboNumber == 1, "combo reset after spinner");
        // slider 2 (at 4000) uses sv from green line -50 => 2.0 => faster
        double d2 = bm.objects.get(3).slideDuration;
        check(Math.abs(d2 - 150 / (100 * 1.4 * 2.0) * 500) < 0.01, "inherited sv: " + d2);

        // 2) perfect std play
        StdEngine se = new StdEngine(bm);
        double cx = 0, cy = 0;
        double[] tmp = new double[2];
        for (StdEngine.Obj o : se.objects) {
            HitObject h = o.src;
            if (h.kind == HitObject.Kind.CIRCLE) {
                se.press(h.time, h.x, h.y);
                se.update(h.time + 5, h.x, h.y, false);
            } else if (h.kind == HitObject.Kind.SLIDER) {
                se.press(h.time, h.x, h.y);
                for (double t = h.time; t <= o.endExact + 50; t += 8) {
                    se.ballPos(o, Math.min(t, o.endExact), tmp);
                    se.update(t, tmp[0], tmp[1], true);
                }
            } else if (h.kind == HitObject.Kind.SPINNER) {
                for (double t = h.time; t <= h.endTime + 20; t += 8) {
                    double ang = (t - h.time) / 1000.0 * 2 * Math.PI * 4;
                    se.update(t, 256 + 100 * Math.cos(ang), 192 + 100 * Math.sin(ang), true);
                }
            }
        }
        se.update(9000, 0, 0, false);
        Score s = se.score;
        System.out.println("std: total=" + s.total + " acc=" + s.accuracy() + " rank=" + s.rank() + " score=" + s.score() + " maxCombo=" + s.maxCombo);
        check(s.total == 6 && s.counts[Judgement.J300.ordinal()] == 6, "std perfect play = six 300s");
        check(s.accuracy() == 1.0 && s.rank().equals("SS"), "std SS");
        check(se.allDone(), "std all done");

        // 3) std all-miss
        StdEngine se2 = new StdEngine(bm);
        se2.update(10000, 0, 0, false);
        check(se2.score.counts[Judgement.MISS.ordinal()] == 6 && se2.score.rank().equals("D"), "std all miss");

        // 3b) click outside circle does nothing
        StdEngine se3 = new StdEngine(bm);
        se3.press(1000, 400, 300);
        check(se3.score.total == 0, "click off-target ignored");
        se3.press(1020, 100, 100);
        check(se3.score.total == 1, "click on circle hits");

        // 4) mania: generate + convert + perfect play
        for (LevelGenerator.Pattern p : LevelGenerator.Pattern.values()) {
            Beatmap g = LevelGenerator.generate(3, "Gen", 120, 4, 8, p, 42);
            List<ManiaEngine.Note> notes = ManiaConverter.convert(g, 4);
            ManiaEngine me = new ManiaEngine(4, notes, g.od);
            List<double[]> ev = new ArrayList<>();
            for (ManiaEngine.Note n : me.notes) {
                ev.add(new double[]{n.time, 1, n.lane});
                if (n.hold) ev.add(new double[]{n.end, 0, n.lane});
            }
            ev.sort((x, y) -> x[0] != y[0] ? Double.compare(x[0], y[0]) : Double.compare(x[1], y[1]));
            for (double[] e : ev) { if (e[1] == 1) me.press((int) e[2], e[0]); else me.release((int) e[2], e[0]); }
            me.update(1e7);
            check(me.score.total == notes.size() && me.score.accuracy() == 1.0 && me.score.counts[Judgement.MISS.ordinal()] == 0,
                    "mania " + p + ": " + notes.size() + " notes perfect, score=" + me.score.score());
        }
        // mania late hit and early release
        List<ManiaEngine.Note> nn = new ArrayList<>();
        nn.add(new ManiaEngine.Note(0, 1000, 1000, false));
        nn.add(new ManiaEngine.Note(1, 1000, 2000, true));
        ManiaEngine me = new ManiaEngine(2, nn, 8);
        me.press(0, 1030);
        me.press(1, 1000);
        me.release(1, 1200);
        me.update(5000);
        check(me.score.counts[Judgement.MISS.ordinal()] == 1 && me.score.total == 2, "early release = miss");
        ManiaEngine me2 = new ManiaEngine(1, List.of(new ManiaEngine.Note(0, 1000, 1000, false)), 8);
        me2.update(1200);
        check(me2.score.counts[Judgement.MISS.ordinal()] == 1, "auto miss");

        // 5) convert std map to mania
        Beatmap big = LevelGenerator.generate(0, "StdGen", 150, 4, 16, LevelGenerator.Pattern.ALEATORIO, 7);
        List<ManiaEngine.Note> conv = ManiaConverter.convert(big, 4);
        check(!conv.isEmpty(), "std->mania conversion gives " + conv.size() + " of " + big.objects.size());

        // 6) write -> parse roundtrip
        Beatmap g = LevelGenerator.generate(3, "Round Trip", 140, 7, 4, LevelGenerator.Pattern.ALEATORIO, 3);
        String text = OsuWriter.toText(g);
        Beatmap back = OsuParser.parse(Arrays.asList(text.split("\n")), true);
        check(back.mode == 3 && back.keys() == 7 && back.objects.size() == g.objects.size(), "roundtrip mania " + back.objects.size());
        boolean colsOk = true;
        for (int i = 0; i < g.objects.size(); i++) colsOk &= g.objects.get(i).column == back.objects.get(i).column && g.objects.get(i).time == back.objects.get(i).time;
        check(colsOk, "roundtrip columns/times");
        Beatmap gs = LevelGenerator.generate(0, "RT std", 140, 4, 6, LevelGenerator.Pattern.ALEATORIO, 11);
        Beatmap bs = OsuParser.parse(Arrays.asList(OsuWriter.toText(gs).split("\n")), true);
        check(bs.objects.size() == gs.objects.size(), "roundtrip std " + bs.objects.size());
        long sliders = bs.objects.stream().filter(o -> o.kind == HitObject.Kind.SLIDER).count();
        System.out.println("sliders in std gen: " + sliders);

        // 7) recorder
        ManiaRecorder rec = new ManiaRecorder(4, 120, 2000);
        rec.press(0, 2010); rec.release(0, 2030);
        rec.press(1, 2260); rec.release(1, 2800);
        Beatmap rb = rec.build("rec", 8);
        check(rb.objects.size() == 2 && rb.objects.get(0).time == 2000 && rb.objects.get(1).kind == HitObject.Kind.HOLD, "recorder " + rb.objects.size() + " hold end=" + rb.objects.get(1).endTime);

        // 8) osz import
        Path root = Files.createTempDirectory("osutest");
        Library.root = root;
        Library.ensureDirs();
        Path osz = root.resolve("12345 Tester - Test Song.osz");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(osz))) {
            z.putNextEntry(new ZipEntry("Tester - Test Song (me) [Easy].osu")); z.write(std.getBytes("UTF-8")); z.closeEntry();
            z.putNextEntry(new ZipEntry("song.mp3")); z.write(new byte[]{1,2,3}); z.closeEntry();
            z.putNextEntry(new ZipEntry("bg.jpg")); z.write(new byte[]{1,2,3}); z.closeEntry();
            z.putNextEntry(new ZipEntry("../../evil.osu")); z.write("x".getBytes()); z.closeEntry();
        }
        String folder = Library.importOsz(osz);
        List<Beatmap> lib = Library.scan();
        check(lib.size() == 2 || lib.size() == 1, "library scan " + lib.size());
        check(!Files.exists(root.resolve("evil.osu")) && !Files.exists(root.getParent().resolve("evil.osu")), "zip-slip blocked");
        Beatmap hdr = lib.get(0);
        check(hdr.title.equals("Test Song") && hdr.objectCount == 6, "header scan: " + hdr.displayName() + " objs=" + hdr.objectCount);
        check(Library.audioPath(hdr) != null, "audio path found");
        // 9) config roundtrip
        Config c = new Config(); c.scrollTime = 900; c.setManiaKey(4, 0, 65); c.stdKeys = new int[]{81, 87};
        c.save(root.resolve("c.properties"));
        Config c2 = new Config(); c2.load(root.resolve("c.properties"));
        check(c2.scrollTime == 900 && c2.maniaKeys(4)[0] == 65 && c2.stdKeys[0] == 81 && c2.maniaKeys(7).length == 7, "config roundtrip");

        // 10) HP y fallar
        StdEngine seHp = new StdEngine(bm);
        seHp.score.hpDrain = 5;
        for (int i = 0; i < 20; i++) seHp.score.add(Judgement.MISS);
        check(seHp.score.hp <= 0.0, "hp llega a 0 con misses: " + seHp.score.hp);
        check(se.score.hp > 0.99, "hp se mantiene lleno en partida perfecta: " + se.score.hp);

        // 11) mods
        Mods mm = new Mods();
        mm.toggleDt();
        check(mm.rate() == 1.5 && mm.label().equals("DT"), "DT rate");
        mm.toggleHt();
        check(mm.rate() == 0.75 && !mm.dt, "HT reemplaza a DT");
        mm.toggleHr(); mm.toggleEz();
        check(mm.ez && !mm.hr, "EZ reemplaza a HR");
        mm.toggleHr();
        Beatmap hrMap = OsuParser.parse(Arrays.asList(std.split("\n")), true);
        double y0 = hrMap.objects.get(0).y; double od0 = hrMap.od; double cs0 = hrMap.cs;
        Mods hr = new Mods(); hr.toggleHr(); hr.apply(hrMap, true);
        check(hrMap.objects.get(0).y == 384 - y0 && hrMap.od > od0 && hrMap.cs > cs0 && hrMap.od <= 10, "HR espeja y sube dificultad");
        check(hrMap.objects.get(2).path != null && Math.abs(hrMap.objects.get(2).path.length - 200) < 0.5, "slider se reconstruye tras HR");
        Mods ez = new Mods(); ez.toggleEz();
        Beatmap ezMap = LevelGenerator.generate(3, "EZ", 120, 7, 4, LevelGenerator.Pattern.STREAM, 1);
        ez.apply(ezMap, false);
        check(ezMap.keys() == 7, "EZ no cambia los carriles de mania");
        Score ms = new Score(true); ms.multiplier = 0.5; ms.add(Judgement.MAX);
        check(ms.score() == 500000, "multiplicador de mods en el puntaje: " + ms.score());
        System.out.println(fails == 0 ? "ALL PASSED" : ("FAILURES: " + fails));
    }
}
