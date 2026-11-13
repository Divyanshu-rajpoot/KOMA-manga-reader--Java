package app.koma;

/**
 * Plain main class so the app also starts from a fat jar / jpackage image,
 * where a main class extending {@code Application} fails with "JavaFX runtime components are missing".
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        App.main(args);
    }
}
