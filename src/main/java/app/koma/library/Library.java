package app.koma.library;

import java.util.ArrayList;
import java.util.List;

/** Root of library.json. */
public class Library {
    public int version = 1;
    public List<Series> series = new ArrayList<>();
}
