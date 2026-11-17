package app.koma.dev;

import app.koma.AppContext;
import app.koma.library.Series;
import app.koma.library.Volume;
import app.koma.ui.importer.ImportView;
import app.koma.ui.shell.Screen;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Developer tour: drives the real UI through every screen and saves screenshots, then exits.
 * Enabled only with -Dkoma.devtour=&lt;screenshot dir&gt; (optionally -Dkoma.devimport=&lt;folder of CBZs&gt;).
 * Use with -Dkoma.home=&lt;temp dir&gt; so it never touches your real library.
 */
public final class DevTour {
    private final AppContext ctx;
    private final Path out;
    private final Deque<Runnable> steps = new ArrayDeque<>();

    private DevTour(AppContext ctx, Path out) {
        this.ctx = ctx;
        this.out = out;
    }

    public static void startIfRequested(AppContext ctx) {
        String dir = System.getProperty("koma.devtour");
        if (dir == null) return;
        new DevTour(ctx, Path.of(dir)).run();
    }

    private void step(double seconds, Runnable r) {
        steps.add(() -> {
            PauseTransition p = new PauseTransition(Duration.seconds(seconds));
            p.setOnFinished(e -> {
                try {
                    r.run();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                next();
            });
            p.play();
        });
    }

    private void next() {
        Runnable r = steps.poll();
        if (r != null) r.run();
    }

    private void run() {
        String imp = System.getProperty("koma.devimport");
        ctx.theme.setTheme("light");
        if (imp != null) {
            step(1.0, () -> ctx.router.navigate(Screen.IMPORT, List.of(Path.of(imp))));
            step(3.0, () -> shot("01-import-light"));
            step(0.2, () -> {
                if (ctx.router.currentView() instanceof ImportView iv) iv.addAllReady();
            });
            step(2.5, () -> shot("02-import-added"));
        }
        step(0.5, () -> openFirst(paged()));
        step(2.5, () -> shot("03-reader-single"));
        for (int i = 0; i < 3; i++) step(0.1, () -> key(KeyCode.EQUALS));
        step(1.0, () -> shot("03b-reader-zoom-175"));
        for (int i = 0; i < 6; i++) step(0.1, () -> key(KeyCode.MINUS));
        step(1.0, () -> shot("03c-reader-zoom-25"));
        step(0.1, () -> key(KeyCode.DIGIT0));
        step(0.8, () -> shot("03d-reader-zoom-reset"));
        step(0.1, () -> key(KeyCode.LEFT));
        step(0.1, () -> key(KeyCode.LEFT));
        step(0.1, () -> key(KeyCode.LEFT));
        step(0.1, () -> key(KeyCode.DIGIT1));
        step(1.5, () -> shot("04-reader-spread"));
        step(0.1, () -> key(KeyCode.LEFT));
        step(1.0, () -> shot("05-reader-spread-next"));
        step(0.1, () -> key(KeyCode.F));
        step(2.5, () -> shot("05b-reader-fullscreen"));
        step(0.1, () -> key(KeyCode.ESCAPE));
        step(0.1, () -> ctx.router.back());
        step(0.5, () -> openFirst(strip()));
        step(2.0, () -> shot("06-reader-strip"));
        step(0.1, () -> key(KeyCode.DIGIT3));
        step(1.5, () -> shot("06a-reader-real-strip"));
        for (int i = 0; i < 2; i++) step(0.1, () -> key(KeyCode.EQUALS));
        step(1.2, () -> shot("06b-reader-strip-zoom-150"));
        for (int i = 0; i < 4; i++) step(0.1, () -> key(KeyCode.MINUS));
        step(1.2, () -> shot("06c-reader-strip-zoom-50"));
        step(0.1, () -> key(KeyCode.DIGIT0));
        step(0.8, () -> shot("06d-reader-strip-reset"));
        step(0.1, () -> key(KeyCode.PAGE_DOWN));
        step(0.1, () -> key(KeyCode.PAGE_DOWN));
        step(1.5, () -> shot("07-reader-strip-scrolled"));
        step(0.1, () -> ctx.router.navigate(Screen.HOME, null));
        step(2.5, () -> shot("08-home-light"));
        step(0.1, () -> ctx.router.navigate(Screen.LIBRARY, null));
        step(1.5, () -> shot("09-library-light"));
        step(0.1, () -> ctx.router.navigate(Screen.SERIES, paged()));
        step(1.5, () -> shot("10-series-light"));
        step(0.1, () -> ctx.router.navigate(Screen.SETTINGS, null));
        step(1.0, () -> shot("11-settings-light"));
        step(0.1, () -> {
            ctx.theme.setTheme("dark");
            ctx.router.navigate(Screen.HOME, null);
        });
        step(2.5, () -> shot("12-home-dark"));
        step(0.1, this::scrollToBottom);
        step(1.0, () -> shot("12b-home-dark-bottom"));
        step(0.1, () -> ctx.router.navigate(Screen.LIBRARY, null));
        step(1.5, () -> shot("13-library-dark"));
        step(0.1, () -> ctx.router.navigate(Screen.SERIES, paged()));
        step(1.5, () -> shot("13b-series-dark"));
        step(0.1, () -> clickButton("RENAME"));
        step(0.8, () -> shot("13b1-series-rename"));
        step(0.1, () -> clickButton("CANCEL"));
        step(0.1, () -> clickButton("REMOVE"));
        step(0.5, () -> shot("13b1b-series-remove-confirm"));
        step(0.1, this::scrollToBottom);
        step(1.0, () -> shot("13b2-series-dark-bottom"));
        step(0.1, () -> ctx.router.navigate(Screen.SETTINGS, null));
        step(1.0, () -> shot("13c-settings-dark"));
        step(0.1, this::scrollToBottom);
        step(0.8, () -> shot("13c2-settings-dark-bottom"));
        step(0.1, () -> openFirst(paged()));
        step(2.5, () -> shot("14-reader-dark"));
        step(0.1, () -> ctx.router.navigate(Screen.IMPORT, null));
        step(1.0, () -> shot("15-import-dark"));
        step(0.1, () -> {
            ctx.theme.setTheme("system");
            Platform.exit();
        });
        next();
    }

    /** First paged (non long-strip) series, else the first series. */
    private String paged() {
        return ctx.store.series().stream().filter(x -> !"ttb".equals(x.direction)).findFirst()
                .or(() -> ctx.store.series().stream().findFirst()).map(x -> x.id).orElse(null);
    }

    /** First long-strip series, else any. */
    private String strip() {
        return ctx.store.series().stream().filter(x -> "ttb".equals(x.direction)).findFirst()
                .or(() -> ctx.store.series().stream().findFirst()).map(x -> x.id).orElse(null);
    }

    private void openFirst(String seriesId) {
        Series s = seriesId == null ? null : ctx.store.findSeries(seriesId);
        if (s == null) {
            System.err.println("DevTour: no series " + seriesId);
            return;
        }
        Volume v = s.sortedVolumes().stream().min(Comparator.comparingDouble(x -> x.number)).orElseThrow();
        ctx.router.navigate(Screen.READER, v.id);
    }

    private void clickButton(String text) {
        ctx.stage.getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof javafx.scene.control.Button b && text.equals(b.getText()))
                .findFirst().ifPresent(n -> ((javafx.scene.control.Button) n).fire());
    }

    private void scrollToBottom() {
        if (ctx.router.currentView().root() instanceof javafx.scene.control.ScrollPane sp) sp.setVvalue(1);
    }

    private void key(KeyCode code) {
        var root = ctx.stage.getScene().getRoot();
        Event.fireEvent(root, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    private void shot(String name) {
        try {
            WritableImage img = ctx.stage.getScene().snapshot(null);
            int w = (int) img.getWidth(), h = (int) img.getHeight();
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            PixelReader pr = img.getPixelReader();
            int[] row = new int[w];
            for (int y = 0; y < h; y++) {
                pr.getPixels(0, y, w, 1, javafx.scene.image.PixelFormat.getIntArgbInstance(), row, 0, w);
                bi.setRGB(0, y, w, 1, row, 0, w);
            }
            Files.createDirectories(out);
            ImageIO.write(bi, "png", out.resolve(name + ".png").toFile());
            System.out.println("DevTour: " + name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
