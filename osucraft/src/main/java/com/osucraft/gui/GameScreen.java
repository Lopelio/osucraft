package com.osucraft.gui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.osucraft.audio.AudioDecoder;
import com.osucraft.audio.AudioSession;
import com.osucraft.client.Cfg;
import com.osucraft.core.Beatmap;
import com.osucraft.core.Config;
import com.osucraft.core.HitObject;
import com.osucraft.core.Judgement;
import com.osucraft.core.LevelGenerator;
import com.osucraft.core.Library;
import com.osucraft.core.ManiaConverter;
import com.osucraft.core.ManiaEngine;
import com.osucraft.core.ManiaRecorder;
import com.osucraft.core.Mods;
import com.osucraft.core.OsuParser;
import com.osucraft.core.Score;
import com.osucraft.core.StdEngine;
import com.osucraft.core.TimingPoint;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Pantalla de juego: modo Standard (círculos/sliders/spinners) y Mania (carriles), además de grabación de niveles. */
public final class GameScreen extends Screen {
    public record RecordSetup(String name, double bpm, int keys, int bars) { }

    private enum Phase { LOADING, PLAYING, PAUSED, ERROR, DONE }

    private static final class Loaded {
        Beatmap bm;
        AudioDecoder.Pcm pcm;
        String warn = "";
        String error;
    }

    private static final int[] COMBO_COLORS = {0x66CCFF, 0xFF7AA8, 0x7AFFB0, 0xFFD86B, 0xB08CFF};

    private final Screen parent;
    private final Beatmap header;
    private final int mode;
    private final int keys;
    private final RecordSetup rec;
    private final Mods mods;
    private final Config cfg = Cfg.get();
    private final CompletableFuture<Loaded> future;

    private Phase phase = Phase.LOADING;
    private String error = "";
    private String note = "";

    private Beatmap bm;
    private StdEngine std;
    private ManiaEngine mania;
    private ManiaRecorder recorder;
    private AudioSession audio;

    // reloj
    private double rate = 1.0;
    private long baseNano;
    private double baseTime;
    private double pausedAt;
    private double startTime;
    private double endTime;
    private int lastObjTime;
    private boolean musicStarted;

    // metrónomo
    private boolean metro;
    private double metroOrigin;
    private double metroBeat = 500;
    private int metroMeter = 4;
    private int metroIndex;

    // entrada
    private int[] laneKeys = new int[0];
    private boolean[] laneHeld = new boolean[0];
    private final boolean[] stdKeyDown = new boolean[2];
    private final boolean[] mouseDown = new boolean[2];
    private int stdHeld;

    // dibujo
    private int visStart;
    private int[] comboIdx = new int[0];
    private double[][][] sliderPts = new double[0][][];
    private double scale = 1;
    private double ox;
    private double oy;
    private final double[] tmp = new double[2];

    public GameScreen(Screen parent, Beatmap header, int mode, int keys) {
        this(parent, header, mode, keys, Cfg.mods().copy());
    }

    public GameScreen(Screen parent, Beatmap header, int mode, int keys, Mods mods) {
        super(Text.literal("osu!"));
        this.parent = parent;
        this.header = header;
        this.mode = mode;
        this.keys = keys;
        this.rec = null;
        this.mods = mods;
        this.future = CompletableFuture.supplyAsync(this::load);
    }

    public GameScreen(Screen parent, RecordSetup rec) {
        super(Text.literal("osu!"));
        this.parent = parent;
        this.header = null;
        this.mode = 3;
        this.keys = rec.keys();
        this.rec = rec;
        this.mods = new Mods();
        Loaded l = new Loaded();
        Beatmap b = new Beatmap();
        b.title = rec.name();
        b.version = "Grabado";
        b.mode = 3;
        b.cs = rec.keys();
        b.od = 8;
        TimingPoint tp = new TimingPoint();
        tp.time = LevelGenerator.START_OFFSET;
        tp.beatLength = 60000.0 / rec.bpm();
        b.timing.add(tp);
        l.bm = b;
        this.future = CompletableFuture.completedFuture(l);
    }

    private Loaded load() {
        Loaded l = new Loaded();
        try {
            l.bm = OsuParser.parse(header.file, true);
            if (l.bm.objects.isEmpty()) {
                l.error = "El mapa no tiene objetos.";
                return l;
            }
            Path ap = Library.audioPath(l.bm);
            if (ap != null) {
                try {
                    l.pcm = AudioDecoder.decode(ap);
                } catch (Throwable t) {
                    l.warn = "Sin música: " + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : " - " + t.getMessage());
                }
            } else if (l.bm.hasAudio()) {
                l.warn = "No se encontró el archivo de audio del mapa.";
            }
        } catch (Exception e) {
            l.error = "No se pudo leer el mapa: " + e.getMessage();
        }
        return l;
    }

    // ------------------------------------------------------------------ inicio

    private void startGame(Loaded l) {
        if (l == null || l.error != null) {
            phase = Phase.ERROR;
            error = l == null ? "Error desconocido" : l.error;
            return;
        }
        bm = l.bm;
        mods.apply(bm, mode == 0 && bm.mode == 0);
        rate = mods.rate();
        note = l.warn;
        audio = new AudioSession();
        audio.setSfxVolume(cfg.sfxVolume);
        if (l.pcm != null) {
            if (audio.isOk()) {
                audio.setMusic(l.pcm, cfg.musicVolume);
                audio.setMusicRate((float) rate);
            }
            if (!audio.hasMusic()) note = "Sin música: OpenAL no está disponible.";
        }
        laneKeys = cfg.maniaKeys(keys);
        double lead;
        int firstTime;
        if (rec != null) {
            double beat = 60000.0 / rec.bpm();
            recorder = new ManiaRecorder(keys, rec.bpm(), LevelGenerator.START_OFFSET);
            laneHeld = new boolean[keys];
            lastObjTime = (int) (LevelGenerator.START_OFFSET + rec.bars() * 4 * beat);
            firstTime = (int) (LevelGenerator.START_OFFSET - 4 * beat);
            lead = 800;
            metro = true;
            metroOrigin = LevelGenerator.START_OFFSET;
            metroBeat = beat;
            metroMeter = 4;
        } else if (mode == 3) {
            List<ManiaEngine.Note> notes = ManiaConverter.convert(bm, keys);
            if (notes.isEmpty()) {
                phase = Phase.ERROR;
                error = "No se pudo adaptar este mapa a " + keys + " carriles.";
                return;
            }
            mania = new ManiaEngine(keys, notes, bm.od);
            laneHeld = new boolean[keys];
            firstTime = mania.notes.get(0).time;
            int last = 0;
            for (ManiaEngine.Note n : mania.notes) last = Math.max(last, n.end);
            lastObjTime = last;
            lead = cfg.scrollTime + 800;
        } else {
            std = new StdEngine(bm);
            firstTime = bm.objects.get(0).time;
            lastObjTime = bm.lastTime();
            lead = std.preempt + 800;
            comboIdx = new int[std.objects.size()];
            sliderPts = new double[std.objects.size()][][];
            int ci = -1;
            boolean prevSpinner = true;
            for (int i = 0; i < std.objects.size(); i++) {
                HitObject h = std.objects.get(i).src;
                if (i == 0 || h.newCombo || prevSpinner) ci++;
                comboIdx[i] = Math.max(0, ci);
                prevSpinner = h.kind == HitObject.Kind.SPINNER;
            }
        }
        if (rec == null && !bm.hasAudio()) {
            metro = true;
            metroOrigin = firstUninherited();
            metroBeat = bm.beatLengthAt(firstTime);
            metroMeter = 4;
        }
        Score sc0 = score();
        if (sc0 != null) {
            sc0.hpDrain = bm.hp;
            sc0.multiplier = mods.multiplier();
        }
        startTime = Math.min(-1500, firstTime - lead);
        endTime = lastObjTime + 1500;
        metroIndex = (int) Math.ceil((startTime - metroOrigin) / metroBeat);
        baseTime = startTime;
        baseNano = System.nanoTime();
        phase = Phase.PLAYING;
    }

    private double firstUninherited() {
        for (TimingPoint tp : bm.timing) {
            if (tp.uninherited && tp.beatLength > 0) return tp.time;
        }
        return 0;
    }

    // ------------------------------------------------------------------ reloj

    private double now() {
        if (phase == Phase.PAUSED) return pausedAt;
        return baseTime + (System.nanoTime() - baseNano) / 1_000_000.0 * rate;
    }

    private double tickClock() {
        double t = now();
        if (audio != null && audio.hasMusic()) {
            if (!musicStarted && t >= 0) {
                audio.playMusic();
                musicStarted = true;
                baseTime = 0;
                baseNano = System.nanoTime();
                t = 0;
            } else if (musicStarted && audio.musicPlaying()) {
                double pos = audio.musicPositionMs();
                if (pos >= 0 && Math.abs(pos - t) > 45) {
                    baseTime = pos;
                    baseNano = System.nanoTime();
                    t = pos;
                }
            }
        }
        if (metro && audio != null && audio.isOk() && metroBeat > 0) {
            while (metroOrigin + metroIndex * metroBeat <= t) {
                double bt = metroOrigin + metroIndex * metroBeat;
                if (t - bt < 120) audio.metronome(Math.floorMod(metroIndex, metroMeter) == 0);
                metroIndex++;
            }
        }
        return t;
    }

    // ------------------------------------------------------------------ pausa / fin

    private void pause() {
        if (phase != Phase.PLAYING) return;
        pausedAt = now();
        releaseAll(pausedAt - cfg.offset);
        phase = Phase.PAUSED;
        if (audio != null) audio.pauseMusic();
    }

    private void resume() {
        if (phase != Phase.PAUSED) return;
        baseTime = pausedAt;
        baseNano = System.nanoTime();
        phase = Phase.PLAYING;
        if (audio != null) audio.resumeMusic();
    }

    private void releaseAll(double tj) {
        for (int i = 0; i < laneHeld.length; i++) {
            if (laneHeld[i]) {
                laneHeld[i] = false;
                if (recorder != null) recorder.release(i, tj);
                else if (mania != null) mania.release(i, tj);
            }
        }
        stdKeyDown[0] = false;
        stdKeyDown[1] = false;
        mouseDown[0] = false;
        mouseDown[1] = false;
        stdHeld = 0;
    }

    private void restart() {
        this.client.setScreen(new GameScreen(parent, header, mode, keys, mods.copy()));
    }

    private void exit() {
        this.client.setScreen(parent);
    }

    private void finish() {
        if (phase == Phase.DONE) return;
        phase = Phase.DONE;
        if (audio != null) audio.stopMusic();
        if (rec != null) {
            saveRecording();
            return;
        }
        showResults(false);
    }

    private void fail() {
        if (phase == Phase.DONE) return;
        phase = Phase.DONE;
        if (audio != null) audio.stopMusic();
        showResults(true);
    }

    private void showResults(boolean failed) {
        Score s = std != null ? std.score : mania.score;
        final Screen p = parent;
        final Beatmap h = header;
        final int m = mode;
        final int k = keys;
        final Mods md = mods;
        this.client.setScreen(new ResultsScreen(p, bm.displayName(), s,
                () -> new GameScreen(p, h, m, k, md.copy()), md.label(), failed));
    }

    private void saveRecording() {
        String msg;
        try {
            Beatmap out = recorder.build(rec.name(), 8);
            if (out.objects.isEmpty()) {
                msg = "No grabaste ninguna nota, así que no se guardó nada.";
            } else {
                Path f = Library.saveNew(out, "Custom - " + rec.name());
                msg = "Grabadas " + out.objects.size() + " notas en " + f.getParent().getFileName() + ". Buscalo en Jugar.";
            }
        } catch (IOException | RuntimeException e) {
            msg = "Error al guardar: " + e.getMessage();
        }
        this.client.setScreen(parent);
        if (parent instanceof CreatorScreen cs) cs.setStatus(msg);
    }

    @Override
    public void removed() {
        if (audio != null) {
            audio.close();
            audio = null;
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ------------------------------------------------------------------ entrada

    private int laneOf(int key) {
        for (int i = 0; i < laneKeys.length; i++) {
            if (laneKeys[i] == key) return i;
        }
        return -1;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        switch (phase) {
            case LOADING, ERROR, DONE -> {
                if (key == GLFW.GLFW_KEY_ESCAPE) exit();
                return true;
            }
            case PAUSED -> {
                if (key == GLFW.GLFW_KEY_ESCAPE) resume();
                else if (key == GLFW.GLFW_KEY_R && rec == null) restart();
                else if (key == GLFW.GLFW_KEY_Q) exit();
                else if (key == GLFW.GLFW_KEY_G && rec != null) finish();
                return true;
            }
            default -> { }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            pause();
            return true;
        }
        double tj = now() - cfg.offset;
        if (recorder != null || mania != null) {
            int lane = laneOf(key);
            if (lane >= 0 && !laneHeld[lane]) {
                laneHeld[lane] = true;
                if (recorder != null) {
                    recorder.press(lane, tj);
                    if (cfg.hitsounds && audio != null) audio.hit();
                } else {
                    mania.press(lane, tj);
                    if (cfg.hitsounds && audio != null && mania.lastJudgementTime == tj
                            && mania.lastJudgement != null && mania.lastJudgement != Judgement.MISS) {
                        audio.hit();
                    }
                }
            }
        } else if (std != null) {
            for (int i = 0; i < 2; i++) {
                if (key == cfg.stdKeys[i] && !stdKeyDown[i]) {
                    stdKeyDown[i] = true;
                    stdHeld++;
                    stdPress(tj);
                }
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        if (phase != Phase.PLAYING) return true;
        int key = input.key();
        double tj = now() - cfg.offset;
        if (recorder != null || mania != null) {
            int lane = laneOf(key);
            if (lane >= 0 && laneHeld[lane]) {
                laneHeld[lane] = false;
                if (recorder != null) recorder.release(lane, tj);
                else mania.release(lane, tj);
            }
        } else if (std != null) {
            for (int i = 0; i < 2; i++) {
                if (key == cfg.stdKeys[i] && stdKeyDown[i]) {
                    stdKeyDown[i] = false;
                    stdHeld = Math.max(0, stdHeld - 1);
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (phase == Phase.PLAYING && std != null && cfg.mouseButtons) {
            int b = click.button();
            if ((b == 0 || b == 1) && !mouseDown[b]) {
                mouseDown[b] = true;
                stdHeld++;
                stdPress(now() - cfg.offset);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        int b = click.button();
        if ((b == 0 || b == 1) && mouseDown[b]) {
            mouseDown[b] = false;
            stdHeld = Math.max(0, stdHeld - 1);
        }
        return true;
    }

    private void stdPress(double tj) {
        updateLayout();
        double[] c = cursorOsu();
        int before = std.hitEvents;
        std.press(tj, c[0], c[1]);
        if (cfg.hitsounds && audio != null && std.hitEvents != before) audio.hit();
    }

    private void updateLayout() {
        scale = Math.min(this.width * 0.86 / 512.0, (this.height - 30) * 0.92 / 384.0);
        ox = (this.width - 512 * scale) / 2.0;
        oy = 22 + ((this.height - 22) - 384 * scale) / 2.0;
    }

    private double[] cursorOsu() {
        double mx = Ui.mouseX(this.client);
        double my = Ui.mouseY(this.client);
        return new double[]{(mx - ox) / scale, (my - oy) / scale};
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, Ui.BG);
        if (phase == Phase.LOADING) {
            if (future.isDone()) {
                startGame(future.getNow(null));
            } else {
                Ui.centered(ctx, this.textRenderer, "Cargando mapa y música...", this.width / 2, this.height / 2 - 4, Ui.WHITE);
                Ui.centered(ctx, this.textRenderer, "Esc para cancelar", this.width / 2, this.height / 2 + 10, Ui.GRAY);
                return;
            }
        }
        if (phase == Phase.ERROR) {
            Ui.centered(ctx, this.textRenderer, "No se pudo jugar este mapa", this.width / 2, this.height / 2 - 14, 0xFFFF5555);
            Ui.centered(ctx, this.textRenderer, Ui.trim(this.textRenderer, error, this.width - 20), this.width / 2, this.height / 2, Ui.WHITE);
            Ui.centered(ctx, this.textRenderer, "Esc para volver", this.width / 2, this.height / 2 + 16, Ui.GRAY);
            return;
        }
        if (phase == Phase.DONE) return;

        double t = phase == Phase.PAUSED ? pausedAt : tickClock();
        double tj = t - cfg.offset;
        if (phase == Phase.PLAYING) {
            if (std != null) {
                updateLayout();
                double[] c = cursorOsu();
                std.update(tj, c[0], c[1], stdHeld > 0);
            } else if (mania != null) {
                mania.update(tj);
            }
            Score live = score();
            if (live != null && !mods.nf && live.hp <= 0.0) {
                fail();
                return;
            }
            boolean engineDone = (std != null && std.allDone()) || (mania != null && mania.allDone());
            if (t >= endTime || (engineDone && t > lastObjTime + 800)) {
                finish();
                return;
            }
        }

        if (std != null) drawStd(ctx, t, tj);
        else if (mania != null) drawLanes(ctx, t, tj, mania.notes, true);
        else if (recorder != null) drawLanes(ctx, t, tj, recorder.notes, false);
        drawHud(ctx, t, tj);

        if (phase == Phase.PAUSED) {
            ctx.fill(0, 0, this.width, this.height, 0xB0000000);
            int cx = this.width / 2;
            int cy = this.height / 2;
            Ui.big(ctx, this.textRenderer, "PAUSA", cx, cy - 40, 3.0f, Ui.PINK);
            Ui.centered(ctx, this.textRenderer, "Esc: continuar", cx, cy - 4, Ui.WHITE);
            if (rec == null) Ui.centered(ctx, this.textRenderer, "R: reiniciar", cx, cy + 10, Ui.WHITE);
            else Ui.centered(ctx, this.textRenderer, "G: terminar y guardar", cx, cy + 10, Ui.YELLOW);
            Ui.centered(ctx, this.textRenderer, "Q: salir" + (rec != null ? " sin guardar" : ""), cx, cy + 24, Ui.WHITE);
        }
    }

    private Score score() {
        if (std != null) return std.score;
        if (mania != null) return mania.score;
        return null;
    }

    private void drawHud(DrawContext ctx, double t, double tj) {
        Score sc = score();
        int w = this.width;
        String title = bm.title + (bm.version.isEmpty() ? "" : " [" + bm.version + "]");
        Ui.text(ctx, this.textRenderer, Ui.trim(this.textRenderer, title, w / 2 - 10), 4, 5, Ui.GRAY);
        double prog = Math.max(0, Math.min(1, (t - startTime) / Math.max(1, endTime - startTime)));
        Ui.rect(ctx, 0, 0, w, 2, 0xFF2A2142);
        Ui.rect(ctx, 0, 0, (int) (w * prog), 2, Ui.PINK);
        if (sc != null) {
            Ui.right(ctx, this.textRenderer, String.format(Locale.ROOT, "%07d", sc.score()), w - 4, 5, Ui.WHITE);
            Ui.right(ctx, this.textRenderer, String.format(Locale.ROOT, "%.2f%%", sc.accuracy() * 100.0), w - 4, 16, Ui.GRAY);
            int cy = 28;
            for (Judgement j : Judgement.values()) {
                if (std != null && (j == Judgement.MAX || j == Judgement.J200)) continue;
                Ui.right(ctx, this.textRenderer, j.label + " " + sc.counts[j.ordinal()], w - 4, cy, Ui.judgementColor(j));
                cy += 10;
            }
            int barW = 110;
            Ui.rect(ctx, 4, 16, 4 + barW, 21, 0xFF2A2142);
            int hc = sc.hp > 0.5 ? 0xFF6BFF84 : sc.hp > 0.25 ? 0xFFFFC857 : 0xFFFF5555;
            Ui.rect(ctx, 4, 16, 4 + (int) (barW * sc.hp), 21, mods.nf ? 0xFF6A6A8A : hc);
            if (mods.any()) Ui.centered(ctx, this.textRenderer, mods.label(), w / 2, 16, Ui.YELLOW);
            if (std != null && sc.combo > 1) {
                Ui.big(ctx, this.textRenderer, sc.combo + "x", 36, this.height - 30, 2.0f, Ui.WHITE);
            }
        } else if (recorder != null) {
            Ui.right(ctx, this.textRenderer, "GRABANDO  " + recorder.notes.size() + " notas", w - 4, 5, 0xFFFF5555);
        }
        if (!note.isEmpty()) {
            Ui.text(ctx, this.textRenderer, Ui.trim(this.textRenderer, note, w - 8), 4, this.height - 11, 0xFFFFAA55);
        }
        if (t < 0 && phase == Phase.PLAYING && rec == null) {
            Ui.centered(ctx, this.textRenderer, "¡Preparate!", w / 2, this.height / 2 - 4, Ui.withAlpha(0xFFFFFF, Math.min(1.0, -t / 600.0)));
        }
    }

    // ------------------------------------------------------------------ mania

    private static int noteColor(int lane, int k) {
        if (k % 2 == 1 && lane == k / 2 && k > 1) return Ui.YELLOW;
        return lane % 2 == 0 ? 0xFFFFFFFF : 0xFF7FC8FF;
    }

    private void drawLanes(DrawContext ctx, double t, double tj, List<ManiaEngine.Note> notes, boolean engine) {
        int k = keys;
        int laneW = Math.max(12, Math.min(28, (int) (this.width * 0.5 / k)));
        int totalW = laneW * k;
        int left = (this.width - totalW) / 2;
        int hitY = (int) (this.height * 0.82);
        int topY = 4;
        double pxPerMs = (hitY - topY) / (double) cfg.scrollTime;

        Ui.rect(ctx, left - 2, topY, left + totalW + 2, this.height, 0xFF0B0814);
        for (int i = 0; i <= k; i++) Ui.rect(ctx, left + i * laneW - (i == k ? 1 : 0), topY, left + i * laneW + (i == k ? 0 : 1), this.height, 0xFF2A2142);

        // líneas de tiempo (metrónomo)
        if (metro && metroBeat > 0) {
            int first = (int) Math.ceil((t - 400) / metroBeat - metroOrigin / metroBeat);
            for (int b = first; ; b++) {
                double bt = metroOrigin + b * metroBeat;
                int y = hitY - (int) ((bt - t) * pxPerMs);
                if (y < topY) break;
                if (y > this.height) continue;
                boolean bar = Math.floorMod(b, metroMeter) == 0;
                Ui.rect(ctx, left, y, left + totalW, y + 1, bar ? 0xFF4A3A6E : 0xFF2A2142);
            }
        }

        for (int i = 0; i < k; i++) {
            if (laneHeld[i]) Ui.rect(ctx, left + i * laneW + 1, hitY - 36, left + (i + 1) * laneW, hitY, 0x40FF66AA);
        }

        if (engine) {
            while (visStart < notes.size() && notes.get(visStart).state == ManiaEngine.DONE) visStart++;
        }
        for (int i = engine ? visStart : 0; i < notes.size(); i++) {
            ManiaEngine.Note n = notes.get(i);
            double dt = n.time - t;
            if (dt > cfg.scrollTime + 100) break;
            if (n.state == ManiaEngine.DONE) continue;
            int x = left + n.lane * laneW;
            int yHead = hitY - (int) (dt * pxPerMs);
            int color = noteColor(n.lane, k);
            boolean hide = mods.hd && mania != null && dt < cfg.scrollTime * 0.35;
            if (n.hold) {
                int yEnd = hitY - (int) ((n.end - t) * pxPerMs);
                int yh = n.state == ManiaEngine.HOLDING ? Math.min(yHead, hitY) : yHead;
                Ui.rect(ctx, x + 4, Math.max(yEnd, topY), x + laneW - 4, yh, (color & 0x00FFFFFF) | 0x90000000);
                if (!hide) Ui.rect(ctx, x + 1, yh - 5, x + laneW - 1, yh, color);
                Ui.rect(ctx, x + 1, Math.max(yEnd, topY), x + laneW - 1, Math.max(yEnd, topY) + 3, color);
            } else if (!hide && yHead - 5 < this.height) {
                Ui.rect(ctx, x + 1, yHead - 5, x + laneW - 1, yHead, color);
            }
        }

        Ui.rect(ctx, left, hitY, left + totalW, hitY + 3, Ui.PINK);
        for (int i = 0; i < k; i++) {
            String nm = Ui.keyName(laneKeys[i]);
            if (nm.length() > 3) nm = nm.substring(0, 3);
            Ui.centered(ctx, this.textRenderer, nm, left + i * laneW + laneW / 2, hitY + 8, laneHeld[i] ? Ui.WHITE : Ui.DARK_GRAY);
        }

        if (mania != null) {
            if (mania.lastJudgement != null && tj - mania.lastJudgementTime < 600 && tj >= mania.lastJudgementTime) {
                Ui.centered(ctx, this.textRenderer, mania.lastJudgement.label, this.width / 2, hitY - 70, Ui.judgementColor(mania.lastJudgement));
            }
            int combo = mania.score.combo;
            if (combo > 1) Ui.big(ctx, this.textRenderer, combo + "x", this.width / 2, (int) (this.height * 0.35), 2.0f, Ui.WHITE);
        }
    }

    // ------------------------------------------------------------------ standard

    private void drawStd(DrawContext ctx, double t, double tj) {
        int x1 = (int) ox;
        int y1 = (int) oy;
        int x2 = (int) (ox + 512 * scale);
        int y2 = (int) (oy + 384 * scale);
        Ui.rect(ctx, x1 - 2, y1 - 2, x2 + 2, y2 + 2, 0xFF2A2142);
        Ui.rect(ctx, x1, y1, x2, y2, Ui.FIELD_BG);

        List<StdEngine.Obj> objs = std.objects;
        while (visStart < objs.size() && objs.get(visStart).state == StdEngine.DONE && tj - objs.get(visStart).doneTime > 300) visStart++;
        int last = visStart;
        while (last < objs.size() && objs.get(last).src.time - t <= std.preempt) last++;
        for (int i = last - 1; i >= visStart; i--) drawStdObject(ctx, i, objs.get(i), t);

        for (StdEngine.Popup p : std.popups) {
            double age = tj - p.time;
            if (age < 0 || age > 600) continue;
            Ui.centered(ctx, this.textRenderer, p.judgement.label, (int) (ox + p.x * scale), (int) (oy + p.y * scale - 14 - age / 40.0), Ui.judgementColor(p.judgement));
        }
    }

    private void drawStdObject(DrawContext ctx, int idx, StdEngine.Obj o, double t) {
        if (o.state == StdEngine.DONE) return;
        HitObject h = o.src;
        double a = Math.max(0, Math.min(1, (t - (h.time - std.preempt)) / std.fadeIn));
        if (a <= 0) return;
        if (mods.hd) {
            if (h.kind == HitObject.Kind.CIRCLE) {
                double s = h.time - std.preempt * 0.55;
                double e = h.time - std.preempt * 0.15;
                if (t > s) a *= Math.max(0.0, 1.0 - (t - s) / (e - s));
            } else if (h.kind == HitObject.Kind.SLIDER && t >= h.time) {
                a *= Math.max(0.0, 1.0 - (t - h.time) / Math.max(1.0, o.endExact - h.time));
            }
            if (a <= 0.03) return;
        }
        int col = COMBO_COLORS[comboIdx[idx] % COMBO_COLORS.length];
        double r = std.radius * scale;
        switch (h.kind) {
            case SPINNER -> drawSpinner(ctx, o, t, a);
            case SLIDER -> drawSlider(ctx, idx, o, t, a, col, r);
            default -> drawCircle(ctx, h.x, h.y, h.comboNumber, t, h.time, a, col, r, !mods.hd);
        }
    }

    private void drawCircle(DrawContext ctx, double x, double y, int number, double t, double hitTime, double a, int col, double r, boolean approach) {
        double sx = ox + x * scale;
        double sy = oy + y * scale;
        Ui.disc(ctx, sx, sy, r, Ui.fade(0xFFFFFF, a));
        Ui.disc(ctx, sx, sy, r * 0.86, Ui.fade(col, a));
        if (a > 0.5) Ui.centered(ctx, this.textRenderer, String.valueOf(number), (int) sx, (int) sy - 4, Ui.WHITE);
        if (approach && t < hitTime) {
            double p = Math.min(1.0, (hitTime - t) / std.preempt);
            Ui.ring(ctx, sx, sy, r * (1 + 2 * p), 1.5, Ui.fade(0xFFFFFF, a));
        }
    }

    private void drawSlider(DrawContext ctx, int idx, StdEngine.Obj o, double t, double a, int col, double r) {
        HitObject h = o.src;
        double[][] pts = sliderPts[idx];
        if (pts == null) {
            pts = h.path.sample(std.radius * 0.45);
            sliderPts[idx] = pts;
        }
        int border = Ui.fade(0xD0D0D0, a);
        int inner = Ui.fade(0x2B2440, a);
        for (double[] p : pts) Ui.disc(ctx, ox + p[0] * scale, oy + p[1] * scale, r, border);
        for (double[] p : pts) Ui.disc(ctx, ox + p[0] * scale, oy + p[1] * scale, r * 0.84, inner);
        double[] end = pts[pts.length - 1];
        Ui.ring(ctx, ox + end[0] * scale, oy + end[1] * scale, r * 0.55, 1.5, Ui.fade(0xFFFFFF, a));
        if (h.slides > 2) Ui.ring(ctx, ox + h.x * scale, oy + h.y * scale, r * 0.55, 1.5, Ui.fade(0xFFFFFF, a));
        for (int k = o.cpIndex; k < o.cpTime.length; k++) {
            if (o.cpType[k] != 0) continue;
            std.ballPos(o, o.cpTime[k], tmp);
            Ui.disc(ctx, ox + tmp[0] * scale, oy + tmp[1] * scale, 1.5, Ui.fade(0xFFFFFF, a));
        }
        if (o.state == StdEngine.NEW) drawCircle(ctx, h.x, h.y, h.comboNumber, t, h.time, a, col, r, !mods.hd);
        if (t >= h.time && t <= o.endExact) {
            std.ballPos(o, t, tmp);
            double bx = ox + tmp[0] * scale;
            double by = oy + tmp[1] * scale;
            Ui.disc(ctx, bx, by, r * 0.9, Ui.fade(col, 1.0));
            Ui.ring(ctx, bx, by, std.followRadius * scale * (o.tracking ? 1.0 : 0.8), 1.5, o.tracking ? Ui.fade(0xFFFFFF, 1.0) : Ui.fade(0xFF5555, 0.8));
        }
    }

    private void drawSpinner(DrawContext ctx, StdEngine.Obj o, double t, double a) {
        HitObject h = o.src;
        double cx = ox + 256 * scale;
        double cy = oy + 192 * scale;
        double radius = 80 * scale;
        Ui.ring(ctx, cx, cy, radius, 2, Ui.fade(0xFFFFFF, a));
        if (t >= h.time) {
            double prog = Math.max(0, Math.min(1, o.rotations / o.needed));
            Ui.ring(ctx, cx, cy, radius * (0.2 + 0.8 * prog), 2, Ui.fade(0xFF66AA, 1.0));
            if (o.angleValid) {
                Ui.disc(ctx, cx + Math.cos(o.lastAngle) * radius, cy + Math.sin(o.lastAngle) * radius, 3, Ui.fade(0xFFFFFF, 1.0));
            }
            Ui.centered(ctx, this.textRenderer, "¡GIRA!", (int) cx, (int) cy - 4, Ui.WHITE);
            Ui.centered(ctx, this.textRenderer, "(mantené un botón y girá el mouse)", (int) cx, (int) (cy + radius + 6), Ui.GRAY);
        } else {
            Ui.centered(ctx, this.textRenderer, "spinner", (int) cx, (int) cy - 4, Ui.GRAY);
        }
    }
}
