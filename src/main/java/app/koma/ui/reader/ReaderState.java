package app.koma.ui.reader;

/** Reader options shared by the modes. */
final class ReaderState {
    enum Mode { SINGLE, SPREAD, STRIP }

    enum Fit { PAGE, WIDTH }

    Mode mode = Mode.SINGLE;
    Fit fit = Fit.PAGE;
    boolean rtl = true;
    boolean pageAnimation = true;
    /** Full-screen reading: pages use the whole screen, no frame. */
    boolean immersive;
    /** 1.0 = fit; up to 4.0. Applies to every mode and persists while reading. */
    double zoom = 1.0;
    int preloadAhead = 6;
    int preloadBehind = 2;
}
