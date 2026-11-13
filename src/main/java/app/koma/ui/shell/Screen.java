package app.koma.ui.shell;

/** Top-level screens. The reader hides the nav bar. */
public enum Screen {
    HOME("Home", true),
    LIBRARY("Library", true),
    IMPORT("Import CBZ", true),
    SERIES("Series", true),
    SETTINGS("Settings", true),
    READER("Reader", false);

    public final String title;
    public final boolean showNav;

    Screen(String title, boolean showNav) {
        this.title = title;
        this.showNav = showNav;
    }
}
