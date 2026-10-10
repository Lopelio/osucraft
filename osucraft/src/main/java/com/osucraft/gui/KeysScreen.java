package com.osucraft.gui;

import com.osucraft.client.Cfg;
import com.osucraft.core.Config;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Pantalla para personalizar las teclas de Standard y de cada cantidad de carriles de Mania. */
public final class KeysScreen extends OsuScreen {
    private final Config cfg = Cfg.get();
    private boolean standard;
    private int keys = 4;
    private int listening = -1;
    private final ButtonWidget[] slotButtons = new ButtonWidget[10];

    public KeysScreen(Screen parent) {
        super("Teclas", parent);
    }

    private int slotCount() {
        return standard ? 2 : keys;
    }

    private int[] current() {
        return standard ? cfg.stdKeys : cfg.maniaKeys(keys);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = 36;
        button("Modo: " + (standard ? "Standard (2 teclas)" : "Mania"), cx - 100, top, 200, 20, () -> {
            standard = !standard;
            listening = -1;
            this.clearAndInit();
        });
        if (!standard) {
            button("-", cx - 100, top + 24, 20, 20, () -> changeKeys(-1));
            button("+", cx + 80, top + 24, 20, 20, () -> changeKeys(1));
        }
        int n = slotCount();
        int bw = Math.min(46, Math.max(26, (this.width - 40) / Math.max(n, 1) - 4));
        int total = n * (bw + 4) - 4;
        int sx = cx - total / 2;
        for (int i = 0; i < n; i++) {
            final int slot = i;
            slotButtons[i] = button(Ui.keyName(current()[i]), sx + i * (bw + 4), top + 78, bw, 24, () -> {
                listening = slot;
                refreshLabels();
            });
        }
        button("Restablecer", cx - 100, this.height - 52, 98, 20, () -> {
            if (standard) cfg.resetStdKeys();
            else cfg.resetManiaKeys(keys);
            Cfg.save();
            listening = -1;
            refreshLabels();
        });
        button("Volver", cx + 2, this.height - 52, 98, 20, this::close);
    }

    private void changeKeys(int d) {
        keys = Math.max(1, Math.min(10, keys + d));
        listening = -1;
        this.clearAndInit();
    }

    private void refreshLabels() {
        int n = slotCount();
        for (int i = 0; i < n; i++) {
            if (slotButtons[i] == null) continue;
            slotButtons[i].setMessage(Text.literal(listening == i ? "..." : Ui.keyName(current()[i])));
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listening >= 0) {
            int k = input.key();
            if (k != GLFW.GLFW_KEY_ESCAPE) {
                if (standard) cfg.stdKeys[listening] = k;
                else cfg.setManiaKey(keys, listening, k);
                Cfg.save();
            }
            listening = -1;
            refreshLabels();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    protected void drawFront(DrawContext c, int mx, int my) {
        int cx = this.width / 2;
        Ui.centered(c, this.textRenderer, "Personalizar teclas", cx, 12, Ui.PINK);
        if (!standard) {
            Ui.centered(c, this.textRenderer, "Carriles: " + keys + "K", cx, 36 + 24 + 6, Ui.WHITE);
        }
        Ui.centered(c, this.textRenderer, listening >= 0 ? "Presioná la tecla nueva (Esc cancela)" : "Tocá un botón y después la tecla que quieras", cx, 36 + 58, Ui.YELLOW);
        Ui.centered(c, this.textRenderer, standard ? "Standard: el mouse apunta y estas 2 teclas golpean" : "De izquierda a derecha, un botón por carril", cx, 36 + 112, Ui.GRAY);
    }
}
