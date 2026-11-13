package app.koma.ui.shared;

import javafx.scene.Parent;

/** Attaches a screen's own stylesheet: /app/koma/ui/&lt;package&gt;/&lt;name&gt;.css next to the view class. */
public final class Css {
    private Css() {}

    public static void add(Parent node, Class<?> owner, String name) {
        String path = "/" + owner.getPackageName().replace('.', '/') + "/" + name + ".css";
        var url = owner.getResource(path);
        if (url == null) throw new IllegalStateException("Missing stylesheet " + path);
        node.getStylesheets().add(url.toExternalForm());
    }
}
