package com.osucraft.gui;

import net.minecraft.client.gui.DrawContext;

public final class OsuMenuScreen extends OsuScreen {
    public OsuMenuScreen(net.minecraft.client.gui.screen.Screen parent) {
        super("osu!", parent);
    }

    @Override
    protected void init() {
        int w = 170;
        int h = 20;
        int x = this.width / 2 - w / 2;
        int y = Math.max(70, this.height / 2 - 52);
        int gap = 24;
        button("Jugar", x, y, w, h, () -> this.client.setScreen(new SongSelectScreen(this)));
        button("Importar mapas (.osz)", x, y + gap, w, h, () -> this.client.setScreen(new ImportScreen(this)));
        button("Crear nivel", x, y + gap * 2, w, h, () -> this.client.setScreen(new CreatorScreen(this)));
        button("Teclas", x, y + gap * 3, w, h, () -> this.client.setScreen(new KeysScreen(this)));
        button("Opciones", x, y + gap * 4, w, h, () -> this.client.setScreen(new OptionsScreen(this)));
        button("Cerrar", x, y + gap * 5 + 6, w, h, this::close);
    }

    @Override
    protected void drawBack(DrawContext c, int mx, int my) {
        int cx = this.width / 2;
        int ty = Math.max(14, this.height / 2 - 52 - 52);
        Ui.disc(c, cx - 62, ty + 14, 14, Ui.PINK);
        Ui.disc(c, cx - 62, ty + 14, 10, Ui.BG);
        Ui.disc(c, cx - 62, ty + 14, 5, Ui.PINK);
        Ui.big(c, this.textRenderer, "osu!", cx + 6, ty, 3.0f, Ui.PINK);
        Ui.centered(c, this.textRenderer, "en Minecraft", cx, ty + 30, Ui.GRAY);
    }
}
