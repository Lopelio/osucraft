package com.osucraft.core;

/** Mods al estilo osu!: EZ, HR, HT, DT, HD, NF. */
public final class Mods {
    public boolean ez;
    public boolean hr;
    public boolean ht;
    public boolean dt;
    public boolean hd;
    public boolean nf;

    public Mods copy() {
        Mods m = new Mods();
        m.ez = ez;
        m.hr = hr;
        m.ht = ht;
        m.dt = dt;
        m.hd = hd;
        m.nf = nf;
        return m;
    }

    public boolean any() {
        return ez || hr || ht || dt || hd || nf;
    }

    /** Velocidad de la canción (DT = 1.5x, HT = 0.75x). */
    public double rate() {
        return dt ? 1.5 : ht ? 0.75 : 1.0;
    }

    public double multiplier() {
        double m = 1.0;
        if (ez) m *= 0.5;
        if (nf) m *= 0.5;
        if (ht) m *= 0.3;
        if (hr) m *= 1.06;
        if (dt) m *= 1.12;
        if (hd) m *= 1.06;
        return m;
    }

    public String label() {
        StringBuilder sb = new StringBuilder();
        if (ez) sb.append("EZ ");
        if (nf) sb.append("NF ");
        if (ht) sb.append("HT ");
        if (hr) sb.append("HR ");
        if (dt) sb.append("DT ");
        if (hd) sb.append("HD ");
        return sb.toString().trim();
    }

    public void toggleEz() {
        ez = !ez;
        if (ez) hr = false;
    }

    public void toggleHr() {
        hr = !hr;
        if (hr) ez = false;
    }

    public void toggleHt() {
        ht = !ht;
        if (ht) dt = false;
    }

    public void toggleDt() {
        dt = !dt;
        if (dt) ht = false;
    }

    public void toggleHd() {
        hd = !hd;
    }

    public void toggleNf() {
        nf = !nf;
    }

    /**
     * Aplica EZ/HR a la dificultad del mapa (HR además espeja el campo en Standard).
     * Debe llamarse una sola vez sobre un mapa recién leído.
     */
    public void apply(Beatmap bm, boolean flipY) {
        if (!ez && !hr) return;
        bm.ar = bm.ar();
        boolean keepCs = bm.mode == 3; // en mania CircleSize son los carriles
        if (ez) {
            if (!keepCs) bm.cs *= 0.5;
            bm.ar *= 0.5;
            bm.od *= 0.5;
            bm.hp *= 0.5;
        }
        if (hr) {
            if (!keepCs) bm.cs = Math.min(10, bm.cs * 1.3);
            bm.ar = Math.min(10, bm.ar * 1.4);
            bm.od = Math.min(10, bm.od * 1.4);
            bm.hp = Math.min(10, bm.hp * 1.4);
            if (flipY) {
                for (HitObject o : bm.objects) {
                    o.y = 384 - o.y;
                    for (double[] p : o.points) p[1] = 384 - p[1];
                }
                bm.prepare();
            }
        }
    }
}
