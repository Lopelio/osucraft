package com.osucraft.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Biblioteca de mapas en disco: carpeta songs/, import/ e importación de .osz. */
public final class Library {
    private static final long MAX_ENTRY = 300L * 1024 * 1024;
    private static final long MAX_TOTAL = 1500L * 1024 * 1024;

    public static Path root;

    private Library() { }

    public static Path songs() {
        return root.resolve("songs");
    }

    public static Path imports() {
        return root.resolve("import");
    }

    public static void ensureDirs() {
        try {
            Files.createDirectories(songs());
            Files.createDirectories(imports());
        } catch (IOException ignored) {
            // se reporta al intentar usarlas
        }
    }

    private static boolean allowed(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".osu") || n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".wav");
    }

    public static String sanitize(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_' || c == '(' || c == ')' || c == '[' || c == ']' || c == '.') sb.append(c);
            else sb.append('_');
        }
        String r = sb.toString().trim();
        while (r.startsWith(".")) r = r.substring(1);
        if (r.length() > 80) r = r.substring(0, 80).trim();
        return r.isEmpty() ? "mapa" : r;
    }

    private static Path uniqueDir(String name) throws IOException {
        Files.createDirectories(songs());
        Path d = songs().resolve(name);
        int i = 2;
        while (Files.exists(d)) {
            d = songs().resolve(name + " (" + i + ")");
            i++;
        }
        Files.createDirectories(d);
        return d;
    }

    /** Lista todos los mapas de la biblioteca (solo cabeceras). */
    public static List<Beatmap> scan() {
        List<Beatmap> out = new ArrayList<>();
        ensureDirs();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(songs())) {
            for (Path d : dirs) {
                if (!Files.isDirectory(d)) continue;
                try (DirectoryStream<Path> files = Files.newDirectoryStream(d, "*.osu")) {
                    for (Path f : files) {
                        try {
                            out.add(OsuParser.parse(f, false));
                        } catch (IOException | RuntimeException ignored) {
                            // archivo roto: se omite
                        }
                    }
                } catch (IOException ignored) {
                    // carpeta ilegible
                }
            }
        } catch (IOException ignored) {
            // sin biblioteca
        }
        out.sort(Comparator.comparing((Beatmap b) -> b.artist.toLowerCase(Locale.ROOT))
                .thenComparing(b -> b.title.toLowerCase(Locale.ROOT))
                .thenComparingDouble(b -> b.cs + b.od));
        return out;
    }

    /** Importa un .osz. Devuelve el nombre de la carpeta creada. */
    public static String importOsz(Path osz) throws IOException {
        ZipFile zip;
        try {
            zip = new ZipFile(osz.toFile(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            zip = new ZipFile(osz.toFile(), Charset.forName("ISO-8859-1"));
        }
        String base = osz.getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        Path dest = uniqueDir(sanitize(base));
        int osuCount = 0;
        long total = 0;
        try (ZipFile z = zip) {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String entryName = e.getName().replace('\\', '/');
                String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                if (fileName.isEmpty() || !allowed(fileName)) continue;
                Path target = dest.resolve(sanitizeFile(fileName)).normalize();
                if (!target.startsWith(dest)) continue; // protección zip-slip
                try (InputStream in = z.getInputStream(e); OutputStream out = Files.newOutputStream(target)) {
                    byte[] buf = new byte[65536];
                    long entryTotal = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        entryTotal += n;
                        total += n;
                        if (entryTotal > MAX_ENTRY || total > MAX_TOTAL) throw new IOException("Archivo demasiado grande");
                        out.write(buf, 0, n);
                    }
                }
                if (fileName.toLowerCase(Locale.ROOT).endsWith(".osu")) osuCount++;
            }
        } catch (IOException | RuntimeException ex) {
            deleteRecursive(dest);
            throw ex;
        }
        if (osuCount == 0) {
            deleteRecursive(dest);
            throw new IOException("El .osz no contiene mapas (.osu)");
        }
        return dest.getFileName().toString();
    }

    private static String sanitizeFile(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot) : "";
        String stem = dot >= 0 ? name.substring(0, dot) : name;
        return sanitize(stem) + ext.toLowerCase(Locale.ROOT);
    }

    /** Importa una carpeta de mapa ya descomprimida (por ejemplo de osu!stable). */
    public static String importDirectory(Path dir) throws IOException {
        Path dest = uniqueDir(sanitize(dir.getFileName().toString()));
        int osuCount = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path f : files) {
                if (!Files.isRegularFile(f) || !allowed(f.getFileName().toString())) continue;
                Files.copy(f, dest.resolve(sanitizeFile(f.getFileName().toString())), StandardCopyOption.REPLACE_EXISTING);
                if (f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".osu")) osuCount++;
            }
        }
        if (osuCount == 0) {
            deleteRecursive(dest);
            throw new IOException("La carpeta no contiene mapas (.osu)");
        }
        return dest.getFileName().toString();
    }

    /** Importa archivos arrastrados a la ventana. Devuelve cuántos se importaron bien. */
    public static int importPaths(List<Path> paths, List<String> errors) {
        int ok = 0;
        for (Path p : paths) {
            try {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isDirectory(p)) {
                    importDirectory(p);
                    ok++;
                } else if (n.endsWith(".osz") || n.endsWith(".zip")) {
                    importOsz(p);
                    ok++;
                } else {
                    errors.add(p.getFileName() + ": no es un .osz");
                }
            } catch (IOException | RuntimeException e) {
                errors.add(p.getFileName() + ": " + e.getMessage());
            }
        }
        return ok;
    }

    /** Importa todos los .osz que haya en la carpeta import/. */
    public static int importPending(List<String> errors) {
        ensureDirs();
        List<Path> found = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(imports(), "*.osz")) {
            for (Path p : ds) found.add(p);
        } catch (IOException e) {
            errors.add(e.getMessage());
            return 0;
        }
        int ok = 0;
        Path done = imports().resolve("importados");
        for (Path p : found) {
            try {
                importOsz(p);
                Files.createDirectories(done);
                Files.move(p, done.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                ok++;
            } catch (IOException | RuntimeException e) {
                errors.add(p.getFileName() + ": " + e.getMessage());
            }
        }
        return ok;
    }

    /** Guarda un beatmap generado en una carpeta nueva dentro de songs/. */
    public static Path saveNew(Beatmap bm, String folderName) throws IOException {
        Path dir = uniqueDir(sanitize(folderName));
        Path file = dir.resolve(sanitize(bm.title + " [" + bm.version + "]") + ".osu");
        OsuWriter.write(bm, file);
        return file;
    }

    public static void deleteRecursive(Path p) {
        try {
            if (Files.isDirectory(p)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
                    for (Path c : ds) deleteRecursive(c);
                }
            }
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // mejor esfuerzo
        }
    }

    /** Busca el archivo de audio de un mapa dentro de su carpeta. */
    public static Path audioPath(Beatmap bm) {
        if (bm.dir == null || !bm.hasAudio()) return null;
        String name = bm.audioFile.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        Path direct = bm.dir.resolve(sanitizeFile(name));
        if (Files.isRegularFile(direct)) return direct;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(bm.dir)) {
            for (Path p : ds) {
                if (p.getFileName().toString().equalsIgnoreCase(sanitizeFile(name))) return p;
            }
        } catch (IOException ignored) {
            // sin audio
        }
        return null;
    }
}
