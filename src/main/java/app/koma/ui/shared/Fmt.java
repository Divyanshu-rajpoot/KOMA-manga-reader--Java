package app.koma.ui.shared;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display formatting: sizes, dates, relative times. */
public final class Fmt {
    private Fmt() {}

    public static String bytes(long b) {
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.ROOT, "%.0f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.ROOT, gb < 100 ? "%.1f GB" : "%.0f GB", gb);
    }

    /** "TODAY", "YESTERDAY", "3 DAYS AGO", "2 WEEKS AGO". */
    public static String ago(String isoInstant) {
        if (isoInstant == null) return "NEW";
        try {
            Duration d = Duration.between(Instant.parse(isoInstant), Instant.now());
            long h = d.toHours();
            if (h < 1) return "JUST NOW";
            if (h < 24) return h + (h == 1 ? " HOUR" : " HOURS") + " AGO";
            long days = d.toDays();
            if (days == 1) return "YESTERDAY";
            if (days < 14) return days + " DAYS AGO";
            if (days < 60) return days / 7 + " WEEKS AGO";
            return days / 30 + " MONTHS AGO";
        } catch (Exception e) {
            return "";
        }
    }

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd.MM.yy");

    /** "2026-11-11" → "11.11.26". */
    public static String shortDate(String isoDate) {
        try {
            return LocalDate.parse(isoDate).format(SHORT);
        } catch (Exception e) {
            return isoDate == null ? "" : isoDate;
        }
    }

    public static String pad3(int n) {
        return String.format(Locale.ROOT, "%03d", n);
    }

    public static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }
}
