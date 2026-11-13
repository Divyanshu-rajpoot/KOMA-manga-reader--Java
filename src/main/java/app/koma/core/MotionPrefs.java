package app.koma.core;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/** Global "reduce motion" flag: the Windows setting OR the in-app toggle. */
public final class MotionPrefs {
    private MotionPrefs() {}

    private static boolean system;
    private static boolean app;
    private static final BooleanProperty REDUCED = new SimpleBooleanProperty(false);

    public static void init(boolean systemReduced, boolean appReduced) {
        system = systemReduced;
        app = appReduced;
        REDUCED.set(system || app);
    }

    public static void setApp(boolean appReduced) {
        app = appReduced;
        REDUCED.set(system || app);
    }

    public static boolean reduced() {
        return REDUCED.get();
    }

    public static BooleanProperty reducedProperty() {
        return REDUCED;
    }

    public static boolean systemReduced() {
        return system;
    }
}
