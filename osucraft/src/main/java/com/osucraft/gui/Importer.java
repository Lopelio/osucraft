package com.osucraft.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.MinecraftClient;

/** Ejecuta importaciones en segundo plano y avisa en el hilo del cliente. */
final class Importer {
    private Importer() { }

    static void run(Function<List<String>, Integer> task, Consumer<String> done) {
        CompletableFuture.runAsync(() -> {
            List<String> errors = new ArrayList<>();
            int ok;
            try {
                ok = task.apply(errors);
            } catch (Throwable t) {
                ok = 0;
                errors.add(String.valueOf(t.getMessage()));
            }
            final int count = ok;
            String msg = "Importados: " + count + (errors.isEmpty() ? "" : "  |  Errores: " + String.join("; ", errors));
            MinecraftClient.getInstance().execute(() -> done.accept(msg));
        });
    }
}
