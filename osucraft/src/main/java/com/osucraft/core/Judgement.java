package com.osucraft.core;

/** Resultado de un golpe. El orden importa: mientras mayor el ordinal, peor el resultado. */
public enum Judgement {
    MAX(300, "MAX"), J300(300, "300"), J200(200, "200"), J100(100, "100"), J50(50, "50"), MISS(0, "MISS");

    public final int value;
    public final String label;

    Judgement(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public static Judgement worse(Judgement a, Judgement b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
