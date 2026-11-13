package app.koma.ui.shell;

import javafx.scene.Parent;

/** A screen. Built in its constructor; {@link #dispose()} releases listeners, files and animations. */
public interface View {
    Parent root();

    /** Window title suffix, e.g. "Paper Ronin". Null uses the screen title. */
    default String title() {
        return null;
    }

    default void onShown() {}

    default void dispose() {}
}
