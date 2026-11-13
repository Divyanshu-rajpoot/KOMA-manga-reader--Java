package app.koma.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where KOMA keeps its files.
 * <ul>
 *   <li>{@code %APPDATA%\Koma}: library.json, settings.json</li>
 *   <li>{@code %LOCALAPPDATA%\Koma\thumbs}: cover thumbnails</li>
 *   <li>{@code Documents\KOMA Library}: default folder for "copy into KOMA library"</li>
 * </ul>
 * Set {@code -Dkoma.home=<dir>} to keep everything under one folder (tests, portable mode).
 */
public final class AppPaths {
    private AppPaths() {}

    private static Path override() {
        String home = System.getProperty("koma.home");
        return home == null || home.isBlank() ? null : Path.of(home);
    }

    public static Path configDir() {
        Path o = override();
        if (o != null) return ensure(o.resolve("config"));
        String appData = System.getenv("APPDATA");
        Path base = appData != null ? Path.of(appData) : Path.of(System.getProperty("user.home"), ".config");
        return ensure(base.resolve("Koma"));
    }

    public static Path thumbsDir() {
        Path o = override();
        if (o != null) return ensure(o.resolve("thumbs"));
        String local = System.getenv("LOCALAPPDATA");
        Path base = local != null ? Path.of(local) : Path.of(System.getProperty("user.home"), ".cache");
        return ensure(base.resolve("Koma").resolve("thumbs"));
    }

    public static Path defaultLibraryFolder() {
        Path o = override();
        if (o != null) return o.resolve("library");
        return Path.of(System.getProperty("user.home"), "Documents", "KOMA Library");
    }

    public static Path libraryFile() {
        return configDir().resolve("library.json");
    }

    public static Path settingsFile() {
        return configDir().resolve("settings.json");
    }

    private static Path ensure(Path p) {
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create " + p, e);
        }
        return p;
    }
}
