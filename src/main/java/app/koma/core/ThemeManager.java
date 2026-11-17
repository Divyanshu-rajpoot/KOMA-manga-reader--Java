package app.koma.core;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swaps the light ("paper") and dark ("ink") stylesheets on the scene and applies the accent.
 * Colors live only in the theme CSS. Code that must paint (canvas textures) asks
 * {@link #color(String)}, which resolves a looked-up color through the current stylesheets.
 */
public final class ThemeManager {
    private static final String BASE = css("/themes/base.css");
    private static final String LIGHT = css("/themes/light.css");
    private static final String DARK = css("/themes/dark.css");
    private static final String ORANGE = css("/themes/accent-orange.css");

    /**
     * Every screen's stylesheet, loaded once on the scene. Per-parent stylesheets make JavaFX register each styled
     * node's parent against them (a linear scan per node that grows with every tile ever created, about half of the
     * UI thread's time when scrolling a big library). Selectors do not overlap between screens.
     */
    private static final List<String> SCREENS = List.of(
            css("/app/koma/ui/shell/shell.css"), css("/app/koma/ui/home/home.css"),
            css("/app/koma/ui/importer/importer.css"), css("/app/koma/ui/library/library.css"),
            css("/app/koma/ui/reader/reader.css"), css("/app/koma/ui/series/series.css"),
            css("/app/koma/ui/settings/settings.css"));

    /** True if this stylesheet is already loaded on the scene (so a view doesn't need to attach it itself). */
    public static boolean isGlobal(String url) {
        return SCREENS.contains(url);
    }

    private final Scene scene;
    private final Settings settings;
    private final boolean systemDark;
    private boolean dark;
    private final IntegerProperty revision = new SimpleIntegerProperty();
    private final Map<String, Color> colorCache = new HashMap<>();

    public ThemeManager(Scene scene, Settings settings, boolean systemDark) {
        this.scene = scene;
        this.settings = settings;
        this.systemDark = systemDark;
        apply();
    }

    public static void loadFonts() {
        for (String f : List.of("Anton-Regular", "Archivo-Regular", "Archivo-Medium", "Archivo-Bold",
                "IBMPlexMono-Regular", "IBMPlexMono-Medium", "NotoSansJP")) {
            var in = ThemeManager.class.getResourceAsStream("/fonts/" + f + ".ttf");
            if (in == null) {
                System.err.println("KOMA: missing font " + f);
                continue;
            }
            try (in) {
                Font.loadFont(in, 12);
            } catch (Exception e) {
                System.err.println("KOMA: could not load font " + f + ": " + e.getMessage());
            }
        }
    }

    public void apply() {
        boolean d = switch (settings.theme) {
            case "dark" -> true;
            case "light" -> false;
            default -> systemDark;
        };
        colorCache.clear();
        var sheets = scene.getStylesheets();
        sheets.setAll(d ? DARK : LIGHT, BASE);
        sheets.addAll(SCREENS);
        if ("orange".equals(settings.accent)) sheets.add(ORANGE);
        dark = d;
        revision.set(revision.get() + 1);
    }

    public void toggle() {
        settings.theme = isDark() ? "light" : "dark";
        settings.save();
        apply();
    }

    public void setTheme(String theme) {
        settings.theme = theme;
        settings.save();
        apply();
    }

    public void setAccent(String accent) {
        settings.accent = accent;
        settings.save();
        apply();
    }

    public boolean isDark() {
        return dark;
    }

    /** Increments whenever the theme or accent changes; listen to repaint canvas textures. */
    public ReadOnlyIntegerProperty revisionProperty() {
        return revision;
    }

    /** Resolves a theme token such as "ink" or "acc" to a Color using the active stylesheets. */
    public Color color(String token) {
        return colorCache.computeIfAbsent(token, t -> {
            Region probe = new Region();
            probe.setStyle("-fx-background-color: -" + t + ";");
            Group g = new Group(probe);
            Scene s = new Scene(g);
            s.getStylesheets().setAll(scene.getStylesheets());
            g.applyCss();
            Background bg = probe.getBackground();
            if (bg != null && !bg.getFills().isEmpty()) {
                Paint p = bg.getFills().get(0).getFill();
                if (p instanceof Color c) return c;
            }
            return isDark() ? Color.web("#EDE8DD") : Color.web("#0E0E0E");
        });
    }

    private static String css(String path) {
        var url = ThemeManager.class.getResource(path);
        if (url == null) throw new IllegalStateException("Missing stylesheet " + path);
        return url.toExternalForm();
    }
}
