package com.osucraft.core;

/** Puntaje, combo y precisión. */
public final class Score {
    public final boolean mania;
    public final int[] counts = new int[Judgement.values().length];
    public int combo;
    public int maxCombo;
    public int total;
    public long stdScore;
    public double hp = 1.0;
    public double hpDrain = 5;
    public double multiplier = 1.0;
    private double maniaWeighted;

    public Score(boolean mania) {
        this.mania = mania;
    }

    private void changeHp(double d) {
        hp = Math.max(0.0, Math.min(1.0, hp + d));
    }

    private double hpDelta(Judgement j) {
        return switch (j) {
            case MAX -> 0.03;
            case J300 -> 0.025;
            case J200 -> 0.015;
            case J100 -> 0.005;
            case J50 -> -0.01;
            case MISS -> -(0.04 + 0.01 * hpDrain);
        };
    }

    public void add(Judgement j) {
        counts[j.ordinal()]++;
        total++;
        changeHp(hpDelta(j));
        if (mania) {
            switch (j) {
                case MAX -> maniaWeighted += 1.0;
                case J300 -> maniaWeighted += 300.0 / 320.0;
                case J200 -> maniaWeighted += 200.0 / 320.0;
                case J100 -> maniaWeighted += 100.0 / 320.0;
                case J50 -> maniaWeighted += 50.0 / 320.0;
                default -> { }
            }
        } else if (j != Judgement.MISS) {
            stdScore += Math.round(j.value * (1.0 + Math.min(combo, 400) / 25.0));
        }
        if (j == Judgement.MISS) {
            combo = 0;
        } else {
            combo++;
            if (combo > maxCombo) maxCombo = combo;
        }
    }

    /** Cuenta un resultado en las estadísticas sin tocar el combo (usado por el final de los sliders). */
    public void addSilent(Judgement j) {
        counts[j.ordinal()]++;
        total++;
        changeHp(hpDelta(j));
        if (!mania && j != Judgement.MISS) {
            stdScore += Math.round(j.value * (1.0 + Math.min(combo, 400) / 25.0));
        }
    }

    /** Puntos extra por ticks de slider (no cuentan como golpe en las estadísticas). */
    public void addTick(boolean hit, int points) {
        changeHp(hit ? 0.004 : -0.02);
        if (hit) {
            stdScore += points;
            combo++;
            if (combo > maxCombo) maxCombo = combo;
        } else {
            combo = 0;
        }
    }

    public long score() {
        if (mania) {
            return total == 0 ? 0 : Math.round(1_000_000.0 * maniaWeighted / total * multiplier);
        }
        return Math.round(stdScore * multiplier);
    }

    /** Precisión de 0 a 1. */
    public double accuracy() {
        if (total == 0) return 1.0;
        if (mania) {
            double sum = counts[Judgement.MAX.ordinal()] * 300.0 + counts[Judgement.J300.ordinal()] * 300.0
                    + counts[Judgement.J200.ordinal()] * 200.0 + counts[Judgement.J100.ordinal()] * 100.0
                    + counts[Judgement.J50.ordinal()] * 50.0;
            return sum / (300.0 * total);
        }
        double sum = counts[Judgement.J300.ordinal()] * 300.0 + counts[Judgement.J100.ordinal()] * 100.0
                + counts[Judgement.J50.ordinal()] * 50.0;
        return sum / (300.0 * total);
    }

    public String rank() {
        double acc = accuracy();
        boolean perfect = counts[Judgement.J100.ordinal()] + counts[Judgement.J50.ordinal()]
                + counts[Judgement.MISS.ordinal()] + counts[Judgement.J200.ordinal()] == 0;
        if (total > 0 && perfect) return "SS";
        if (acc >= 0.95) return "S";
        if (acc >= 0.90) return "A";
        if (acc >= 0.80) return "B";
        if (acc >= 0.70) return "C";
        return "D";
    }
}
