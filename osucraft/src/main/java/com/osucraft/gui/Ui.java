package com.osucraft.gui;

import com.osucraft.core.Judgement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

/** Utilidades de dibujo 2D y de entrada para las pantallas. */
public final class Ui {
    public static final int BG = 0xFF120D1C;
    public static final int PANEL = 0xFF1E1730;
    public static final int FIELD_BG = 0xFF0E0A18;
    public static final int PINK = 0xFFFF66AA;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFFAAAAAA;
    public static final int DARK_GRAY = 0xFF666666;
    public static final int YELLOW = 0xFFFFD86B;

    private Ui() { }

    public static void rect(DrawContext c, int x1, int y1, int x2, int y2, int color) {
        if (x2 > x1 && y2 > y1) c.fill(x1, y1, x2, y2, color);
    }

    /** Disco relleno hecho con franjas horizontales. */
    public static void disc(DrawContext c, double cx, double cy, double r, int color) {
        if (r < 0.6) return;
        int step = r > 14 ? 2 : 1;
        int ri = (int) Math.ceil(r);
        int base = (int) Math.round(cy);
        int bx = (int) Math.round(cx);
        for (int dy = -ri; dy < ri; dy += step) {
            double yy = dy + step / 2.0;
            double h2 = r * r - yy * yy;
            if (h2 <= 0) continue;
            int h = (int) Math.round(Math.sqrt(h2));
            rect(c, bx - h, base + dy, bx + h, base + dy + step, color);
        }
    }

    /** Anillo de grosor dado. */
    public static void ring(DrawContext c, double cx, double cy, double r, double thick, int color) {
        if (r < 1) return;
        double rin = Math.max(0, r - thick);
        int ri = (int) Math.ceil(r);
        int base = (int) Math.round(cy);
        int bx = (int) Math.round(cx);
        for (int dy = -ri; dy < ri; dy++) {
            double yy = dy + 0.5;
            double ho2 = r * r - yy * yy;
            if (ho2 <= 0) continue;
            int ho = (int) Math.round(Math.sqrt(ho2));
            double hi2 = rin * rin - yy * yy;
            int y1 = base + dy;
            if (hi2 <= 0) {
                rect(c, bx - ho, y1, bx + ho, y1 + 1, color);
            } else {
                int hi = (int) Math.round(Math.sqrt(hi2));
                if (hi >= ho) hi = ho - 1;
                rect(c, bx - ho, y1, bx - hi, y1 + 1, color);
                rect(c, bx + hi, y1, bx + ho, y1 + 1, color);
            }
        }
    }

    public static int withAlpha(int rgb, double a) {
        int al = (int) Math.round(Math.max(0, Math.min(1, a)) * 255);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    /** Mezcla un color con el fondo del campo; da un color opaco que simula transparencia. */
    public static int fade(int rgb, double a) {
        a = Math.max(0, Math.min(1, a));
        int bgR = 0x0E;
        int bgG = 0x0A;
        int bgB = 0x18;
        int r = (int) Math.round(bgR + (((rgb >> 16) & 0xFF) - bgR) * a);
        int g = (int) Math.round(bgG + (((rgb >> 8) & 0xFF) - bgG) * a);
        int b = (int) Math.round(bgB + ((rgb & 0xFF) - bgB) * a);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static void text(DrawContext c, TextRenderer font, String s, int x, int y, int color) {
        c.drawText(font, s, x, y, color, true);
    }

    public static void centered(DrawContext c, TextRenderer font, String s, int cx, int y, int color) {
        c.drawText(font, s, cx - font.getWidth(s) / 2, y, color, true);
    }

    public static void right(DrawContext c, TextRenderer font, String s, int rx, int y, int color) {
        c.drawText(font, s, rx - font.getWidth(s), y, color, true);
    }

    /** Texto escalado, centrado en cx. */
    public static void big(DrawContext c, TextRenderer font, String s, int cx, int y, float scale, int color) {
        c.getMatrices().pushMatrix();
        c.getMatrices().translate(cx, y);
        c.getMatrices().scale(scale, scale);
        c.drawText(font, s, -font.getWidth(s) / 2, 0, color, true);
        c.getMatrices().popMatrix();
    }

    public static String trim(TextRenderer font, String s, int maxWidth) {
        if (font.getWidth(s) <= maxWidth) return s;
        String dots = "...";
        int end = s.length();
        while (end > 0 && font.getWidth(s.substring(0, end)) + font.getWidth(dots) > maxWidth) end--;
        return s.substring(0, end) + dots;
    }

    public static double mouseX(MinecraftClient mc) {
        Window w = mc.getWindow();
        return mc.mouse.getX() * w.getScaledWidth() / (double) w.getWidth();
    }

    public static double mouseY(MinecraftClient mc) {
        Window w = mc.getWindow();
        return mc.mouse.getY() * w.getScaledHeight() / (double) w.getHeight();
    }

    public static int judgementColor(Judgement j) {
        return switch (j) {
            case MAX -> 0xFFB6F3FF;
            case J300 -> 0xFF6CD8FF;
            case J200 -> 0xFF6BFF84;
            case J100 -> 0xFFB2FF6B;
            case J50 -> 0xFFFFC857;
            case MISS -> 0xFFFF5555;
        };
    }

    public static String keyName(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_SPACE: return "Espacio";
            case GLFW.GLFW_KEY_ESCAPE: return "Esc";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_BACKSPACE: return "Retroceso";
            case GLFW.GLFW_KEY_RIGHT: return "Der";
            case GLFW.GLFW_KEY_LEFT: return "Izq";
            case GLFW.GLFW_KEY_DOWN: return "Abajo";
            case GLFW.GLFW_KEY_UP: return "Arriba";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "ShiftIzq";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "ShiftDer";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "CtrlIzq";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "CtrlDer";
            case GLFW.GLFW_KEY_LEFT_ALT: return "AltIzq";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "AltDer";
            case GLFW.GLFW_KEY_SEMICOLON: return ";";
            default: break;
        }
        if (key >= 65 && key <= 90) return String.valueOf((char) key);
        if (key >= 48 && key <= 57) return String.valueOf((char) key);
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) return "Num" + (key - GLFW.GLFW_KEY_KP_0);
        try {
            String n = GLFW.glfwGetKeyName(key, 0);
            if (n != null && !n.isEmpty()) return n.toUpperCase();
        } catch (Throwable ignored) {
            // sin nombre disponible
        }
        return "Tecla " + key;
    }

    public static String time(double ms) {
        int s = (int) Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
