package com.osucraft.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Configuración del mod (teclas, velocidad, volumen). Se guarda como config.properties. */
public final class Config {
    // Códigos de tecla GLFW
    public static final int KEY_Z = 90;
    public static final int KEY_X = 88;

    public int scrollTime = 650;
    public int offset = 0;
    public int musicVolume = 70;
    public int sfxVolume = 60;
    public boolean hitsounds = true;
    public boolean mouseButtons = true;
    public int convertKeys = 4;
    public int[] stdKeys = {KEY_Z, KEY_X};
    private final int[][] maniaKeys = new int[11][];

    public static int[] defaultKeys(int k) {
        return switch (k) {
            case 1 -> new int[]{32};
            case 2 -> new int[]{70, 74};
            case 3 -> new int[]{70, 32, 74};
            case 4 -> new int[]{68, 70, 74, 75};
            case 5 -> new int[]{68, 70, 32, 74, 75};
            case 6 -> new int[]{83, 68, 70, 74, 75, 76};
            case 7 -> new int[]{83, 68, 70, 32, 74, 75, 76};
            case 8 -> new int[]{65, 83, 68, 70, 74, 75, 76, 59};
            case 9 -> new int[]{65, 83, 68, 70, 32, 74, 75, 76, 59};
            default -> new int[]{65, 83, 68, 70, 86, 78, 74, 75, 76, 59};
        };
    }

    public int[] maniaKeys(int k) {
        k = Math.max(1, Math.min(10, k));
        int[] cur = maniaKeys[k];
        if (cur == null || cur.length != k) {
            cur = defaultKeys(k);
            maniaKeys[k] = cur;
        }
        return cur;
    }

    public void setManiaKey(int k, int index, int key) {
        int[] arr = maniaKeys(k);
        if (index >= 0 && index < arr.length) arr[index] = key;
    }

    public void resetManiaKeys(int k) {
        maniaKeys[Math.max(1, Math.min(10, k))] = defaultKeys(k);
    }

    public void resetStdKeys() {
        stdKeys = new int[]{KEY_Z, KEY_X};
    }

    public void load(Path file) {
        if (!Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | IllegalArgumentException e) {
            return;
        }
        scrollTime = clamp(getInt(p, "scrollTime", scrollTime), 200, 2000);
        offset = clamp(getInt(p, "offset", offset), -300, 300);
        musicVolume = clamp(getInt(p, "musicVolume", musicVolume), 0, 100);
        sfxVolume = clamp(getInt(p, "sfxVolume", sfxVolume), 0, 100);
        hitsounds = Boolean.parseBoolean(p.getProperty("hitsounds", Boolean.toString(hitsounds)));
        mouseButtons = Boolean.parseBoolean(p.getProperty("mouseButtons", Boolean.toString(mouseButtons)));
        convertKeys = clamp(getInt(p, "convertKeys", convertKeys), 1, 10);
        int[] s = parseKeys(p.getProperty("std.keys"), 2);
        if (s != null) stdKeys = s;
        for (int k = 1; k <= 10; k++) {
            int[] arr = parseKeys(p.getProperty("mania." + k), k);
            if (arr != null) maniaKeys[k] = arr;
        }
    }

    public void save(Path file) {
        Properties p = new Properties();
        p.setProperty("scrollTime", Integer.toString(scrollTime));
        p.setProperty("offset", Integer.toString(offset));
        p.setProperty("musicVolume", Integer.toString(musicVolume));
        p.setProperty("sfxVolume", Integer.toString(sfxVolume));
        p.setProperty("hitsounds", Boolean.toString(hitsounds));
        p.setProperty("mouseButtons", Boolean.toString(mouseButtons));
        p.setProperty("convertKeys", Integer.toString(convertKeys));
        p.setProperty("std.keys", join(stdKeys));
        for (int k = 1; k <= 10; k++) p.setProperty("mania." + k, join(maniaKeys(k)));
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                p.store(w, "OsuCraft");
            }
        } catch (IOException ignored) {
            // no es crítico
        }
    }

    private static int getInt(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, Integer.toString(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String join(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(a[i]);
        }
        return sb.toString();
    }

    private static int[] parseKeys(String s, int expected) {
        if (s == null || s.isBlank()) return null;
        String[] parts = s.split(",");
        if (parts.length != expected) return null;
        int[] r = new int[expected];
        try {
            for (int i = 0; i < expected; i++) r[i] = Integer.parseInt(parts[i].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return r;
    }
}
