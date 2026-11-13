package app.koma.ui.reader;

/** Reader options shared by the modes. */
final class ReaderState {
    enum Mode { SINGLE, SPREAD, STRIP }

    enum Fit { PAGE, WIDTH }

    Mode mode = Mode.SINGLE;
    Fit fit = Fit.PAGE;
    boolean rtl = true;
    boolean pageAnimation = true;
    int preloadAhead = 6;
    int preloadBehind = 2;
}
