package app.koma.ui.shell;

import app.koma.AppContext;
import app.koma.core.Anim;
import app.koma.core.MotionPrefs;
import app.koma.ui.home.HomeView;
import app.koma.ui.importer.ImportView;
import app.koma.ui.library.LibraryView;
import app.koma.ui.reader.ReaderView;
import app.koma.ui.series.SeriesView;
import app.koma.ui.settings.SettingsView;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Swaps screens in the content area and keeps a back stack. */
public final class Router {
    private record Entry(Screen screen, Object arg) {}

    private final AppContext ctx;
    private final StackPane host;
    private final Deque<Entry> history = new ArrayDeque<>();
    private Entry currentEntry;
    private View current;
    private final java.util.function.BiConsumer<Screen, String> onChange;

    public Router(AppContext ctx, StackPane host, java.util.function.BiConsumer<Screen, String> onChange) {
        this.ctx = ctx;
        this.host = host;
        this.onChange = onChange;
    }

    public Screen currentScreen() {
        return currentEntry == null ? null : currentEntry.screen();
    }

    public View currentView() {
        return current;
    }

    /** arg: series id (SERIES), volume id (READER), List&lt;Path&gt; (IMPORT), query string (LIBRARY). */
    public void navigate(Screen screen, Object arg) {
        if (currentEntry != null && currentEntry.screen() != Screen.READER) history.push(currentEntry);
        if (history.size() > 30) history.removeLast();
        show(new Entry(screen, arg));
    }

    /** Replace the current screen without growing the back stack (e.g. next volume in the reader). */
    public void replace(Screen screen, Object arg) {
        show(new Entry(screen, arg));
    }

    public void back() {
        Entry prev = history.isEmpty() ? new Entry(Screen.HOME, null) : history.pop();
        show(prev);
    }

    public void closeCurrent() {
        if (current != null) current.dispose();
        current = null;
    }

    @SuppressWarnings("unchecked")
    private View create(Entry e) {
        return switch (e.screen()) {
            case HOME -> new HomeView(ctx);
            case LIBRARY -> new LibraryView(ctx, (String) e.arg());
            case IMPORT -> new ImportView(ctx, (List<Path>) e.arg());
            case SERIES -> new SeriesView(ctx, (String) e.arg());
            case SETTINGS -> new SettingsView(ctx);
            case READER -> new ReaderView(ctx, (String) e.arg());
        };
    }

    private void show(Entry e) {
        View next;
        try {
            next = create(e);
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            ctx.window.toast("Could not open " + e.screen().title + ": " + ex.getMessage());
            return;
        }
        if (current != null) current.dispose();
        current = next;
        currentEntry = e;
        Parent root = next.root();
        host.getChildren().setAll(root);
        host.requestFocus(); // otherwise focus falls to the first focusable node: the search box
        onChange.accept(e.screen(), next.title());
        if (!MotionPrefs.reduced()) {
            root.setOpacity(0);
            FadeTransition f = new FadeTransition(Anim.PAGE, root);
            f.setToValue(1);
            TranslateTransition t = new TranslateTransition(Anim.PAGE, root);
            t.setFromY(10);
            t.setToY(0);
            ParallelTransition p = new ParallelTransition(f, t);
            f.setInterpolator(Anim.EASE);
            t.setInterpolator(Anim.EASE);
            p.play();
        }
        next.onShown();
    }

    /** Rebuild the current screen in place (after theme/accent changes that need repainting). */
    public void refresh() {
        if (currentEntry != null) show(currentEntry);
    }
}
