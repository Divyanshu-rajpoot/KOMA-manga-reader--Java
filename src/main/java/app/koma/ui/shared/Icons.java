package app.koma.ui.shared;

import javafx.scene.layout.Region;

import java.util.Map;

/** Small square glyphs drawn as Region shapes, colored by CSS (.glyph { -fx-background-color }). */
public final class Icons {
    private Icons() {}

    private static final Map<String, String> PATHS = Map.ofEntries(
            Map.entry("minimize", "M4 11h16v2H4z"),
            Map.entry("maximize", "M4 4h16v16H4z M6 6v12h12V6z"),
            Map.entry("restore", "M8 4h12v12h-3v-2h1V6h-8v1H8z M4 8h12v12H4z M6 10v8h8v-8z"),
            Map.entry("close", "M5.4 4 12 10.6 18.6 4 20 5.4 13.4 12 20 18.6 18.6 20 12 13.4 5.4 20 4 18.6 10.6 12 4 5.4z"),
            Map.entry("search", "M10 3a7 7 0 1 0 4.2 12.6l5.1 5.1 1.4-1.4-5.1-5.1A7 7 0 0 0 10 3zm0 2a5 5 0 1 1 0 10 5 5 0 0 1 0-10z"),
            Map.entry("plus", "M11 4h2v7h7v2h-7v7h-2v-7H4v-2h7z"),
            Map.entry("play", "M7 4l13 8-13 8z M9 7.6v8.8l7.2-4.4z"),
            Map.entry("left", "M15.4 4 7.4 12l8 8 1.4-1.4-6.6-6.6 6.6-6.6z"),
            Map.entry("right", "M8.6 4l8 8-8 8-1.4-1.4 6.6-6.6-6.6-6.6z"),
            Map.entry("tri-left", "M17 4v16L5 12z"),
            Map.entry("tri-right", "M7 4v16l12-8z"),
            Map.entry("moon", "M12 3a9 9 0 1 0 9 9 7 7 0 0 1-9-9z"),
            Map.entry("sun", "M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10zM11 1h2v3h-2zM11 20h2v3h-2zM1 11h3v2H1zM20 11h3v2h-3zM4.2 5.6l1.4-1.4 2.1 2.1-1.4 1.4zM16.3 17.7l1.4-1.4 2.1 2.1-1.4 1.4zM4.2 18.4l2.1-2.1 1.4 1.4-2.1 2.1zM16.3 6.3l2.1-2.1 1.4 1.4-2.1 2.1z"),
            Map.entry("settings", "M3 6h9v2H3zM18 6h3v2h-3zM12 3h6v8h-6z M14 5v4h2V5z M3 16h3v2H3zM12 16h9v2h-9zM6 13h6v8H6z M8 15v4h2v-4z"),
            Map.entry("upload", "M11 16V7.8l-3.6 3.6L6 10l6-6 6 6-1.4 1.4L13 7.8V16zM4 15h2v4h12v-4h2v6H4z"),
            Map.entry("check", "M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"),
            Map.entry("more", "M4 10h3.5v3.5H4zM10.25 10h3.5v3.5h-3.5zM16.5 10H20v3.5h-3.5z"),
            Map.entry("folder", "M3 5h7l2 2h9v12H3z"),
            Map.entry("fullscreen", "M3 3h7v2H5v5H3zM14 3h7v7h-2V5h-5zM3 14h2v5h5v2H3zM19 14h2v7h-7v-2h5z"),
            Map.entry("grid", "M3 3h8v8H3zM13 3h8v8h-8zM3 13h8v8H3zM13 13h8v8h-8z"),
            Map.entry("list", "M3 5h18v3H3zM3 10.5h18v3H3zM3 16h18v3H3z"),
            // KOMA brand mark: a bold katakana コ, same geometry as the app icon (packaging/IconGen.java)
            Map.entry("koma", "M6 6h12v12H6v-3h9V9H6z"));

    private static final String FRAME = "M0 0h.01v.01H0z M23.99 23.99h.01v.01h-.01z ";

    public static Region of(String name, double size) {
        Region r = new Region();
        r.getStyleClass().add("glyph");
        // two invisible specks pin the shape to a 24x24 frame so wide or flat glyphs aren't stretched
        r.setStyle("-fx-shape: \"" + FRAME + PATHS.getOrDefault(name, PATHS.get("more")) + "\";");
        r.setMinSize(size, size);
        r.setPrefSize(size, size);
        r.setMaxSize(size, size);
        r.setMouseTransparent(true);
        return r;
    }
}
