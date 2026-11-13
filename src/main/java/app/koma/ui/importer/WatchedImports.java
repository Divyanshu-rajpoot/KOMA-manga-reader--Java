package app.koma.ui.importer;

import app.koma.AppContext;
import app.koma.core.Exec;
import app.koma.library.FolderWatcher;
import app.koma.library.ImportService;
import app.koma.ui.shell.Screen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Connects the watched folders from Settings to the Import screen. New files are never added
 * silently: they wait in the Import queue and a toast says so.
 */
public final class WatchedImports {
    private final AppContext ctx;
    private final Set<Path> pending = new LinkedHashSet<>();
    private FolderWatcher watcher;

    public WatchedImports(AppContext ctx) {
        this.ctx = ctx;
    }

    /** (Re)starts watching Settings.watchFolders and scans them once for files not yet in the library. */
    public void restart() {
        stop();
        List<Path> folders = ctx.settings.watchFolders.stream().map(Path::of).toList();
        if (folders.isEmpty()) return;
        Exec.io(() -> {
            try {
                watcher = new FolderWatcher(folders, found -> Exec.fx(() -> offer(found)));
            } catch (Exception e) {
                System.err.println("KOMA: folder watcher failed: " + e.getMessage());
            }
            List<Path> existing = ImportService.expand(folders);
            Exec.fx(() -> offer(existing));
        });
    }

    private void offer(List<Path> found) {
        List<Path> fresh = new ArrayList<>();
        for (Path p : found) {
            String abs = p.toAbsolutePath().toString();
            if (!ctx.store.containsPath(abs) && pending.add(p.toAbsolutePath())) fresh.add(p.toAbsolutePath());
        }
        if (fresh.isEmpty()) return;
        if (ctx.router.currentScreen() == Screen.IMPORT && ctx.router.currentView() instanceof ImportView iv) {
            iv.addPaths(fresh);
            pending.removeAll(fresh);
            return;
        }
        ctx.window.toast(fresh.size() + (fresh.size() == 1 ? " new CBZ" : " new CBZ files")
                + " in your watched folders. Open Import to add them.");
    }

    /** Files waiting for the Import screen; clears the list. */
    public List<Path> take() {
        List<Path> out = new ArrayList<>(pending);
        pending.clear();
        return out;
    }

    public void stop() {
        if (watcher != null) watcher.close();
        watcher = null;
    }
}
