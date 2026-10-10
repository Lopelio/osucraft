package com.osucraft.client;

import java.nio.file.Path;

import com.osucraft.core.Config;
import com.osucraft.core.Library;
import com.osucraft.core.Mods;

/** Acceso global a la configuración del cliente. */
public final class Cfg {
    private static final Config CONFIG = new Config();
    private static boolean loaded;
    private static final Mods MODS = new Mods();

    private Cfg() { }

    private static Path file() {
        return Library.root.resolve("config.properties");
    }

    public static Config get() {
        if (!loaded) {
            loaded = true;
            CONFIG.load(file());
        }
        return CONFIG;
    }

    /** Mods elegidos en el selector de canciones (valen solo mientras el juego esté abierto). */
    public static Mods mods() {
        return MODS;
    }

    public static void save() {
        CONFIG.save(file());
    }
}
