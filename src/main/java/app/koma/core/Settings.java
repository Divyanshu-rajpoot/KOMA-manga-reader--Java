package app.koma.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** settings.json, next to library.json. Public fields keep the Jackson mapping trivial. */
public class Settings {
    /** "system", "light" or "dark". */
    public String theme = "system";
    /** "red" or "orange". */
    public String accent = "red";
    public boolean reduceMotion = false;
    /** Page cache budget in MB. */
    public int cacheMb = 1024;
    public int preloadAhead = 6;
    public int preloadBehind = 2;
    /** "rtl" or "ltr": default direction for new manga series. */
    public String defaultDirection = "rtl";
    /** "spread", "single" or "strip". */
    public String readerMode = "single";
    /** "linked" (keep in place) or "copied". */
    public String importStorage = "linked";
    public String libraryFolder = AppPaths.defaultLibraryFolder().toString();
    /** Folders watched for new CBZ files. */
    public List<String> watchFolders = new ArrayList<>();
    public boolean pageTurnAnimation = true;

    private static transient Path file;

    public static Settings load(Path path) {
        file = path;
        return Json.read(path, Settings.class, Settings::new);
    }

    /** Serialises on the caller's thread (cheap), writes on an I/O thread. */
    public void save() {
        if (file == null) return;
        byte[] data = Json.bytes(this);
        Path target = file;
        Exec.io(() -> {
            try {
                Json.writeAtomic(target, data);
            } catch (IOException e) {
                System.err.println("KOMA: could not save settings: " + e.getMessage());
            }
        });
    }
}
