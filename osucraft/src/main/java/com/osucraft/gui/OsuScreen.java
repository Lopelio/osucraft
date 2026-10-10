package com.osucraft.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Base de las pantallas del mod: fondo propio y utilidades para botones. */
public abstract class OsuScreen extends Screen {
    protected final Screen parent;

    protected OsuScreen(String title, Screen parent) {
        super(Text.literal(title));
        this.parent = parent;
    }

    /** El fondo se dibuja en render(); se anula el fondo/blur por defecto. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, this.width, this.height, Ui.BG);
        drawBack(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, deltaTicks);
        drawFront(context, mouseX, mouseY);
    }

    /** Dibujo debajo de los widgets. */
    protected void drawBack(DrawContext context, int mouseX, int mouseY) {
    }

    /** Dibujo encima de los widgets. */
    protected void drawFront(DrawContext context, int mouseX, int mouseY) {
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    protected ButtonWidget button(String label, int x, int y, int w, int h, Runnable action) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> action.run()).dimensions(x, y, w, h).build();
        this.addDrawableChild(b);
        return b;
    }
}
