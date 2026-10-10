package com.osucraft.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.osucraft.client.Cfg;
import com.osucraft.core.Beatmap;
import com.osucraft.core.Config;
import com.osucraft.core.Library;
import com.osucraft.core.Mods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class SongSelectScreen extends OsuScreen {
    private static final int LIST_TOP = 42;
    private static final int ROW_H = 12;

    private List<Beatmap> all = new ArrayList<>();
    private final List<Beatmap> shown = new ArrayList<>();
    private boolean loading = true;
    private int scroll;
    private int selected = -1;
    private String filter = "";
    private boolean playStd = true;
    private String status = "";
    private boolean busy;

    private TextFieldWidget search;
    private ButtonWidget playBtn;
    private ButtonWidget modeBtn;
    private ButtonWidget keysBtn;
    private final ButtonWidget[] modBtns = new ButtonWidget[6];
    private static final String[] MOD_NAMES = {"EZ", "HR", "HT", "DT", "HD", "NF"};

    private final Config cfg = Cfg.get();

    public SongSelectScreen(Screen parent) {
        super("Canciones", parent);
        reload();
    }

    private void reload() {
        loading = true;
        CompletableFuture.supplyAsync(Library::scan).thenAccept(list ->
                MinecraftClient.getInstance().execute(() -> {
                    all = list;
                    loading = false;
                    applyFilter();
                }));
    }

    private void applyFilter() {
        Beatmap prev = sel();
        shown.clear();
        String f = filter.toLowerCase(Locale.ROOT).trim();
        for (Beatmap b : all) {
            if (f.isEmpty() || (b.artist + " " + b.title + " " + b.version + " " + b.creator).toLowerCase(Locale.ROOT).contains(f)) {
                shown.add(b);
            }
        }
        selected = shown.isEmpty() ? -1 : Math.max(0, shown.indexOf(prev));
        scroll = 0;
        clampScroll();
        updateButtons();
    }

    private Beatmap sel() {
        return selected >= 0 && selected < shown.size() ? shown.get(selected) : null;
    }

    private int listW() {
        return (int) (this.width * 0.58);
    }

    private int visibleRows() {
        return Math.max(1, (this.height - 12 - LIST_TOP) / ROW_H);
    }

    private void clampScroll() {
        int max = Math.max(0, shown.size() - visibleRows());
        scroll = Math.max(0, Math.min(max, scroll));
    }

    @Override
    protected void init() {
        int listW = listW();
        search = new TextFieldWidget(this.textRenderer, 10, 20, listW - 10, 16, Text.literal("Buscar"));
        search.setPlaceholder(Text.literal("Buscar..."));
        search.setMaxLength(60);
        search.setText(filter);
        search.setChangedListener(s -> {
            filter = s;
            applyFilter();
        });
        this.addDrawableChild(search);

        int px = listW + 20;
        int pw = Math.max(80, this.width - px - 10);
        button("Importar (.osz de la carpeta)", px, 20, pw, 16, this::importFolder);
        int mbw = Math.max(20, (pw - 4) / 3);
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            modBtns[i] = button(modLabel(i), px + (i % 3) * (mbw + 2), this.height - 150 + (i / 3) * 20, mbw, 18, () -> toggleMod(idx));
        }
        playBtn = button("Jugar", px, this.height - 100, pw, 20, this::play);
        modeBtn = button("Modo", px, this.height - 76, pw, 20, this::toggleMode);
        keysBtn = button("Teclas", px, this.height - 52, pw, 20, this::cycleKeys);
        button("Volver", px, this.height - 28, pw, 18, this::close);
        updateButtons();
    }

    private boolean modOn(int i) {
        Mods m = Cfg.mods();
        return switch (i) {
            case 0 -> m.ez;
            case 1 -> m.hr;
            case 2 -> m.ht;
            case 3 -> m.dt;
            case 4 -> m.hd;
            default -> m.nf;
        };
    }

    private String modLabel(int i) {
        return modOn(i) ? "[" + MOD_NAMES[i] + "]" : MOD_NAMES[i];
    }

    private void toggleMod(int i) {
        Mods m = Cfg.mods();
        switch (i) {
            case 0 -> m.toggleEz();
            case 1 -> m.toggleHr();
            case 2 -> m.toggleHt();
            case 3 -> m.toggleDt();
            case 4 -> m.toggleHd();
            default -> m.toggleNf();
        }
        for (int k = 0; k < 6; k++) {
            if (modBtns[k] != null) modBtns[k].setMessage(Text.literal(modLabel(k)));
        }
    }

    private void toggleMode() {
        playStd = !playStd;
        updateButtons();
    }

    private void cycleKeys() {
        cfg.convertKeys = cfg.convertKeys % 10 + 1;
        Cfg.save();
        updateButtons();
    }

    private void updateButtons() {
        if (playBtn == null) return;
        Beatmap b = sel();
        playBtn.active = b != null;
        if (b == null) {
            modeBtn.setMessage(Text.literal("Modo"));
            modeBtn.active = false;
            keysBtn.active = false;
        } else if (b.mode == 3) {
            modeBtn.setMessage(Text.literal("Modo: Mania " + b.keys() + "K"));
            modeBtn.active = false;
            keysBtn.active = false;
        } else if (b.mode == 0) {
            modeBtn.setMessage(Text.literal(playStd ? "Modo: Standard" : "Modo: Mania (adaptado)"));
            modeBtn.active = true;
            keysBtn.active = !playStd;
        } else {
            modeBtn.setMessage(Text.literal("Modo: Mania (adaptado)"));
            modeBtn.active = false;
            keysBtn.active = true;
        }
        keysBtn.setMessage(Text.literal("Teclas al adaptar: " + cfg.convertKeys));
    }

    private void play() {
        Beatmap b = sel();
        if (b == null) return;
        boolean std = b.mode == 0 && playStd;
        int keys = b.mode == 3 ? b.keys() : cfg.convertKeys;
        this.client.setScreen(new GameScreen(this, b, std ? 0 : 3, keys));
    }

    private void importFolder() {
        if (busy) return;
        busy = true;
        status = "Importando...";
        Importer.run(Library::importPending, msg -> {
            busy = false;
            status = msg;
            reload();
        });
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        if (busy) return;
        busy = true;
        status = "Importando...";
        Importer.run(errs -> Library.importPaths(paths, errs), msg -> {
            busy = false;
            status = msg;
            reload();
        });
    }

    private void move(int d) {
        if (shown.isEmpty()) return;
        selected = Math.max(0, Math.min(shown.size() - 1, selected + d));
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + visibleRows()) scroll = selected - visibleRows() + 1;
        clampScroll();
        updateButtons();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_UP) {
            move(-1);
            return true;
        }
        if (k == GLFW.GLFW_KEY_DOWN) {
            move(1);
            return true;
        }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
            play();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        double mx = Ui.mouseX(this.client);
        double my = Ui.mouseY(this.client);
        if (mx >= 10 && mx < listW() && my >= LIST_TOP && my < LIST_TOP + visibleRows() * ROW_H) {
            int idx = scroll + (int) ((my - LIST_TOP) / ROW_H);
            if (idx < shown.size()) {
                selected = idx;
                updateButtons();
                if (doubled) play();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll -= (int) Math.signum(vertical) * 3;
        clampScroll();
        return true;
    }

    @Override
    protected void drawBack(DrawContext c, int mx, int my) {
        int listW = listW();
        Ui.rect(c, 10, LIST_TOP - 2, listW, this.height - 10, Ui.PANEL);
        Ui.text(c, this.textRenderer, "Canciones", 10, 6, Ui.PINK);
        int px = listW + 20;
        Ui.rect(c, px - 4, 42, this.width - 6, this.height - 154, Ui.PANEL);
    }

    @Override
    protected void drawFront(DrawContext c, int mx, int my) {
        int listW = listW();
        if (loading) {
            Ui.text(c, this.textRenderer, "Cargando biblioteca...", 16, LIST_TOP + 4, Ui.GRAY);
        } else if (shown.isEmpty()) {
            Ui.text(c, this.textRenderer, all.isEmpty() ? "No hay mapas. Importá un .osz (arrastralo a la ventana)." : "Sin resultados.", 16, LIST_TOP + 4, Ui.GRAY);
        }
        int rows = visibleRows();
        for (int i = 0; i < rows; i++) {
            int idx = scroll + i;
            if (idx >= shown.size()) break;
            Beatmap b = shown.get(idx);
            int y = LIST_TOP + i * ROW_H;
            if (idx == selected) Ui.rect(c, 11, y, listW - 1, y + ROW_H, 0xFF4A2A5E);
            String tag = b.mode == 3 ? b.keys() + "K" : b.mode == 0 ? "STD" : b.mode == 1 ? "TAIKO" : "CATCH";
            int tagW = this.textRenderer.getWidth(tag);
            String label = Ui.trim(this.textRenderer, b.displayName(), listW - 28 - tagW);
            Ui.text(c, this.textRenderer, label, 15, y + 2, idx == selected ? Ui.WHITE : 0xFFCCCCCC);
            Ui.right(c, this.textRenderer, tag, listW - 6, y + 2, b.mode == 3 ? Ui.YELLOW : 0xFF6CD8FF);
        }
        // detalles
        Beatmap b = sel();
        int px = listW + 20;
        int pw = Math.max(80, this.width - px - 10);
        if (b != null) {
            int y = 46;
            int limit = this.height - 158;
            Ui.text(c, this.textRenderer, Ui.trim(this.textRenderer, b.title, pw), px, y, Ui.WHITE);
            Ui.text(c, this.textRenderer, Ui.trim(this.textRenderer, b.artist, pw), px, y + 11, Ui.GRAY);
            Ui.text(c, this.textRenderer, Ui.trim(this.textRenderer, "[" + b.version + "] por " + b.creator, pw), px, y + 22, Ui.PINK);
            Ui.text(c, this.textRenderer, b.modeLabel() + "  |  " + b.objectCount + " objetos", px, y + 36, Ui.YELLOW);
            if (y + 47 + 9 <= limit) {
                Ui.text(c, this.textRenderer, String.format(Locale.ROOT, "OD %.1f  CS %.1f  AR %.1f", b.od, b.cs, b.ar()), px, y + 47, Ui.GRAY);
            }
            if (b.mode != 3 && y + 58 + 9 <= limit) {
                String hint = (b.mode == 0 && playStd) ? "Mouse + 2 teclas." : "Se adapta a " + cfg.convertKeys + " carriles.";
                Ui.text(c, this.textRenderer, Ui.trim(this.textRenderer, hint, pw), px, y + 58, Ui.GRAY);
            }
        }
        if (!status.isEmpty()) {
            Ui.text(c, this.textRenderer, Ui.trim(this.textRenderer, status, this.width - 20), 10, this.height - 9, Ui.GRAY);
        }
    }
}
