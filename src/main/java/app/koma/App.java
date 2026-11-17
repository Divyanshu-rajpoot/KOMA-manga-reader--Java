package app.koma;

import app.koma.core.AppPaths;
import app.koma.core.Exec;
import app.koma.core.MotionPrefs;
import app.koma.core.Settings;
import app.koma.core.ThemeManager;
import app.koma.core.WinPrefs;
import app.koma.imaging.ThumbCache;
import app.koma.library.LibraryStore;
import app.koma.ui.shell.MainWindow;
import app.koma.ui.shell.Screen;
import javafx.application.Application;
import javafx.stage.Stage;

/** KOMA entry point. Disk work happens in {@link #init()}, which runs on the launcher thread, not the FX thread. */
public class App extends Application {
    private Settings settings;
    private LibraryStore store;
    private boolean systemDark;
    private boolean systemReduce;
    private AppContext ctx;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        settings = Settings.load(AppPaths.settingsFile());
        store = LibraryStore.load(AppPaths.libraryFile());
        systemDark = WinPrefs.systemDark();
        systemReduce = WinPrefs.systemReduceMotion();
        ThemeManager.loadFonts();
        app.koma.imaging.PageDecoder.warmUp();
        ThumbCache.purgeOldVersions(AppPaths.thumbsDir());
    }

    @Override
    public void start(Stage stage) {
        MotionPrefs.init(systemReduce, settings.reduceMotion);
        ctx = new AppContext(stage, settings, store, new ThumbCache(AppPaths.thumbsDir()));
        MainWindow window = new MainWindow(ctx, systemDark);
        window.show();
        ctx.router.navigate(Screen.HOME, null);
        ctx.watched = new app.koma.ui.importer.WatchedImports(ctx);
        ctx.watched.restart();
        app.koma.dev.DevTour.startIfRequested(ctx);
        app.koma.dev.DevPerf.startIfRequested(ctx);
    }

    @Override
    public void stop() {
        if (ctx != null && ctx.watched != null) ctx.watched.stop();
        if (ctx != null && ctx.router != null) ctx.router.closeCurrent();
        if (store != null) store.saveNow();
        Exec.shutdown();
    }
}
