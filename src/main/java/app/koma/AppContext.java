package app.koma;

import app.koma.core.Settings;
import app.koma.core.ThemeManager;
import app.koma.imaging.PageCache;
import app.koma.imaging.ThumbCache;
import app.koma.library.LibraryStore;
import app.koma.ui.shell.MainWindow;
import app.koma.ui.shell.Router;
import javafx.stage.Stage;

/** Everything a screen needs, passed explicitly instead of through singletons. */
public final class AppContext {
    public final Stage stage;
    public final Settings settings;
    public final LibraryStore store;
    public final ThumbCache thumbs;
    /** Decoded reader pages, weighted by bytes (Settings → cache size). */
    public final PageCache pages;
    /** Small pages for the reader's thumbnail strip. */
    public final PageCache pageThumbs = new PageCache(48L << 20);
    public ThemeManager theme;
    public Router router;
    public MainWindow window;
    /** Watched folders → files waiting on the Import screen. */
    public app.koma.ui.importer.WatchedImports watched;

    public AppContext(Stage stage, Settings settings, LibraryStore store, ThumbCache thumbs) {
        this.stage = stage;
        this.settings = settings;
        this.store = store;
        this.thumbs = thumbs;
        this.pages = new PageCache((long) settings.cacheMb << 20);
    }
}
