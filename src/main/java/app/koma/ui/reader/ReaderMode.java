package app.koma.ui.reader;

import javafx.scene.Node;
import javafx.scene.input.KeyEvent;

/** A page layout inside the reader: paged (single / spread) or long strip. */
interface ReaderMode {
    Node node();

    /** Jump to a page (0-based). */
    void go(int page, boolean animate);

    void next();

    void prev();

    /** Current page in reading order (the first visible page). */
    int current();

    /** Viewport or zoom changed: re-request pages at the new size. */
    void relayout();

    /** Mode-specific keys; return true if handled. */
    boolean onKey(KeyEvent e);

    void dispose();
}
