package com.osucraft.gui;

import com.osucraft.client.Cfg;
import com.osucraft.core.Config;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class OptionsScreen extends OsuScreen {
    private final Config cfg = Cfg.get();
    private ButtonWidget hitBtn;
    private ButtonWidget mouseBtn;

    public OptionsScreen(Screen parent) {
        super("Opciones", parent);
    }

    private void adjuster(int y, Runnable minus, Runnable plus) {
        int cx = this.width / 2;
        button("-", cx + 70, y, 20, 18, () -> {
            minus.run();
            Cfg.save();
        });
        button("+", cx + 94, y, 20, 18, () -> {
            plus.run();
            Cfg.save();
        });
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 34;
        int g = 24;
        adjuster(y, () -> cfg.scrollTime = Math.max(200, cfg.scrollTime - 50), () -> cfg.scrollTime = Math.min(2000, cfg.scrollTime + 50));
        adjuster(y + g, () -> cfg.offset = Math.max(-300, cfg.offset - 5), () -> cfg.offset = Math.min(300, cfg.offset + 5));
        adjuster(y + g * 2, () -> cfg.musicVolume = Math.max(0, cfg.musicVolume - 10), () -> cfg.musicVolume = Math.min(100, cfg.musicVolume + 10));
        adjuster(y + g * 3, () -> cfg.sfxVolume = Math.max(0, cfg.sfxVolume - 10), () -> cfg.sfxVolume = Math.min(100, cfg.sfxVolume + 10));
        hitBtn = button(hitLabel(), cx - 120, y + g * 4, 234, 18, () -> {
            cfg.hitsounds = !cfg.hitsounds;
            Cfg.save();
            hitBtn.setMessage(Text.literal(hitLabel()));
        });
        mouseBtn = button(mouseLabel(), cx - 120, y + g * 5, 234, 18, () -> {
            cfg.mouseButtons = !cfg.mouseButtons;
            Cfg.save();
            mouseBtn.setMessage(Text.literal(mouseLabel()));
        });
        button("Volver", cx - 60, y + g * 6 + 8, 120, 20, this::close);
    }

    private String hitLabel() {
        return "Sonido al golpear: " + (cfg.hitsounds ? "SI" : "NO");
    }

    private String mouseLabel() {
        return "Click del mouse golpea (Standard): " + (cfg.mouseButtons ? "SI" : "NO");
    }

    @Override
    protected void drawFront(DrawContext c, int mx, int my) {
        int cx = this.width / 2;
        Ui.centered(c, this.textRenderer, "Opciones", cx, 12, Ui.PINK);
        int y = 34;
        int g = 24;
        Ui.text(c, this.textRenderer, "Tiempo de caída (Mania): " + cfg.scrollTime + " ms", cx - 120, y + 5, Ui.WHITE);
        Ui.text(c, this.textRenderer, "Offset de audio: " + cfg.offset + " ms", cx - 120, y + g + 5, Ui.WHITE);
        Ui.text(c, this.textRenderer, "Volumen música: " + cfg.musicVolume + "%", cx - 120, y + g * 2 + 5, Ui.WHITE);
        Ui.text(c, this.textRenderer, "Volumen efectos: " + cfg.sfxVolume + "%", cx - 120, y + g * 3 + 5, Ui.WHITE);
        Ui.centered(c, this.textRenderer, "Menos tiempo de caída = notas más rápidas. Si pegás siempre tarde, subí el offset.", cx, y + g * 6 + 40, Ui.GRAY);
    }
}
