package com.osucraft.gui;

import java.nio.file.Path;
import java.util.List;

import com.osucraft.core.Library;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public final class ImportScreen extends OsuScreen {
    private String status = "";
    private boolean busy;

    public ImportScreen(Screen parent) {
        super("Importar", parent);
    }

    @Override
    protected void init() {
        int w = 200;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 + 10;
        button("Importar los .osz de la carpeta import", x, y, w, 20, this::importFolder);
        button("Volver", x, y + 28, w, 20, this::close);
    }

    private void importFolder() {
        if (busy) return;
        busy = true;
        status = "Importando...";
        Importer.run(Library::importPending, msg -> {
            busy = false;
            status = msg;
        });
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        if (busy) return;
        busy = true;
        status = "Importando...";
        Importer.run(errs -> Library.importPaths(paths, errs), msg -> {
            busy = false;
            status = msg;
        });
    }

    @Override
    protected void drawFront(DrawContext c, int mx, int my) {
        int cx = this.width / 2;
        int y = Math.max(12, this.height / 2 - 80);
        Ui.centered(c, this.textRenderer, "Importar mapas de osu!", cx, y, Ui.PINK);
        Ui.centered(c, this.textRenderer, "1) Arrastrá un archivo .osz (o la carpeta de un mapa) a esta ventana", cx, y + 22, Ui.WHITE);
        Ui.centered(c, this.textRenderer, "2) O copiá tus .osz en esta carpeta y tocá el botón:", cx, y + 38, Ui.WHITE);
        String path = Library.imports().toAbsolutePath().toString();
        Ui.centered(c, this.textRenderer, Ui.trim(this.textRenderer, path, this.width - 20), cx, y + 52, Ui.YELLOW);
        Ui.centered(c, this.textRenderer, "Descargá mapas en osu.ppy.sh (los .osz se pueden usar tal cual).", cx, y + 68, Ui.GRAY);
        if (!status.isEmpty()) {
            Ui.centered(c, this.textRenderer, Ui.trim(this.textRenderer, status, this.width - 20), cx, this.height / 2 + 62, Ui.WHITE);
        }
    }
}
