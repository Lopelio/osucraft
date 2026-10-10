package com.osucraft.gui;

import java.util.Locale;
import java.util.function.Supplier;

import com.osucraft.core.Judgement;
import com.osucraft.core.Score;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public final class ResultsScreen extends OsuScreen {
    private final String title;
    private final Score score;
    private final Supplier<Screen> retry;
    private final String mods;
    private final boolean failed;

    public ResultsScreen(Screen parent, String title, Score score, Supplier<Screen> retry, String mods, boolean failed) {
        super("Resultados", parent);
        this.title = title;
        this.score = score;
        this.retry = retry;
        this.mods = mods;
        this.failed = failed;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        button("Reintentar", cx - 102, this.height - 36, 100, 20, () -> this.client.setScreen(retry.get()));
        button("Volver", cx + 2, this.height - 36, 100, 20, this::close);
    }

    @Override
    protected void drawFront(DrawContext c, int mx, int my) {
        int cx = this.width / 2;
        Ui.centered(c, this.textRenderer, Ui.trim(this.textRenderer, title, this.width - 20), cx, 10, Ui.PINK);
        if (!mods.isEmpty()) Ui.centered(c, this.textRenderer, "Mods: " + mods, cx, 21, Ui.YELLOW);
        if (failed) {
            Ui.big(c, this.textRenderer, "FALLASTE", cx, 34, 3.0f, 0xFFFF5555);
        } else {
            String rank = score.rank();
            int rankColor = switch (rank) {
                case "SS" -> 0xFFFFE066;
                case "S" -> 0xFFFFC857;
                case "A" -> 0xFF6BFF84;
                case "B" -> 0xFF6CD8FF;
                case "C" -> 0xFFB08CFF;
                default -> 0xFFFF5555;
            };
            Ui.big(c, this.textRenderer, rank, cx, 30, 5.0f, rankColor);
        }
        int y = 82;
        Ui.centered(c, this.textRenderer, "Puntaje: " + score.score(), cx, y, Ui.WHITE);
        Ui.centered(c, this.textRenderer, String.format(Locale.ROOT, "Precisión: %.2f%%   Combo máx: %dx", score.accuracy() * 100.0, score.maxCombo), cx, y + 14, Ui.WHITE);
        int ly = y + 34;
        for (Judgement j : Judgement.values()) {
            if (!score.mania && (j == Judgement.MAX || j == Judgement.J200)) continue;
            Ui.centered(c, this.textRenderer, j.label + ":  " + score.counts[j.ordinal()], cx, ly, Ui.judgementColor(j));
            ly += 12;
        }
    }
}
