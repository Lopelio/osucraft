package com.osucraft.gui;

import java.io.IOException;
import java.nio.file.Path;

import com.osucraft.core.Beatmap;
import com.osucraft.core.Library;
import com.osucraft.core.LevelGenerator;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Crea niveles básicos: generados automáticamente o grabados tocando al ritmo del metrónomo. */
public final class CreatorScreen extends OsuScreen {
    private static final int[] BARS = {8, 16, 32, 64};

    private String name = "Mi nivel";
    private String bpmText = "120";
    private boolean mania = true;
    private int keys = 4;
    private int barsIdx = 1;
    private LevelGenerator.Pattern pattern = LevelGenerator.Pattern.ESCALERA;
    private String status = "";

    private TextFieldWidget nameField;
    private TextFieldWidget bpmField;
    private ButtonWidget keysBtn;
    private ButtonWidget recordBtn;

    public CreatorScreen(Screen parent) {
        super("Crear nivel", parent);
    }

    public void setStatus(String s) {
        this.status = s;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 30;
        nameField = new TextFieldWidget(this.textRenderer, cx - 30, y, 130, 16, Text.literal("Nombre"));
        nameField.setMaxLength(32);
        nameField.setText(name);
        nameField.setChangedListener(s -> name = s);
        this.addDrawableChild(nameField);

        bpmField = new TextFieldWidget(this.textRenderer, cx - 30, y + 24, 60, 16, Text.literal("BPM"));
        bpmField.setMaxLength(6);
        bpmField.setTextPredicate(s -> s.matches("\\d{0,3}(\\.\\d?)?"));
        bpmField.setText(bpmText);
        bpmField.setChangedListener(s -> bpmText = s);
        this.addDrawableChild(bpmField);

        button(modeLabel(), cx - 100, y + 52, 200, 18, () -> {
            mania = !mania;
            this.clearAndInit();
        });
        keysBtn = button(keysLabel(), cx - 100, y + 74, 200, 18, () -> {
            keys = keys % 10 + 1;
            keysBtn.setMessage(Text.literal(keysLabel()));
        });
        keysBtn.active = mania;
        button("Patrón: " + pattern.label, cx - 100, y + 96, 200, 18, () -> {
            pattern = pattern.next();
            this.clearAndInit();
        });
        button("Duración: " + BARS[barsIdx] + " compases", cx - 100, y + 118, 200, 18, () -> {
            barsIdx = (barsIdx + 1) % BARS.length;
            this.clearAndInit();
        });
        button("Generar y guardar nivel", cx - 100, y + 146, 200, 20, this::generate);
        recordBtn = button("Grabar nivel tocando (Mania + metrónomo)", cx - 100, y + 170, 200, 20, this::record);
        recordBtn.active = mania;
        button("Volver", cx - 100, y + 198, 200, 18, this::close);
    }

    private String modeLabel() {
        return "Modo: " + (mania ? "Mania (carriles)" : "Standard (círculos)");
    }

    private String keysLabel() {
        return "Carriles: " + keys;
    }

    private double bpm() {
        try {
            double v = Double.parseDouble(bpmText);
            return Math.max(40, Math.min(300, v));
        } catch (NumberFormatException e) {
            return 120;
        }
    }

    private String cleanName() {
        String n = name == null ? "" : name.trim();
        return n.isEmpty() ? "Mi nivel" : n;
    }

    private void generate() {
        try {
            Beatmap bm = LevelGenerator.generate(mania ? 3 : 0, cleanName(), bpm(), keys, BARS[barsIdx], pattern, System.nanoTime());
            Path file = Library.saveNew(bm, "Custom - " + cleanName());
            status = "Guardado: " + file.getParent().getFileName() + "  (" + bm.objects.size() + " objetos). Buscalo en Jugar.";
        } catch (IOException | RuntimeException e) {
            status = "Error al guardar: " + e.getMessage();
        }
    }

    private void record() {
        this.client.setScreen(new GameScreen(this, new GameScreen.RecordSetup(cleanName(), bpm(), keys, BARS[barsIdx])));
    }

    @Override
    protected void drawFront(DrawContext c, int mx, int my) {
        int cx = this.width / 2;
        Ui.centered(c, this.textRenderer, "Crear nivel", cx, 12, Ui.PINK);
        Ui.text(c, this.textRenderer, "Nombre:", cx - 100, 35, Ui.WHITE);
        Ui.text(c, this.textRenderer, "BPM:", cx - 100, 59, Ui.WHITE);
        if (!status.isEmpty()) {
            Ui.centered(c, this.textRenderer, Ui.trim(this.textRenderer, status, this.width - 16), cx, 30 + 222, Ui.YELLOW);
        }
        Ui.centered(c, this.textRenderer, "Los niveles se guardan como .osu normales en la carpeta songs.", cx, this.height - 12, Ui.GRAY);
    }
}
