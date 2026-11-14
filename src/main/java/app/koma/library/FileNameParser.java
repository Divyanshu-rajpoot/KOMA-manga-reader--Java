package app.koma.library;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guesses series and volume from a file name. Rules are tried in order; the first match wins:
 * <ol>
 *   <li>{Series} v{NN} / Vol. {N} / Volume {N} (also "v01-05" ranges)</li>
 *   <li>{Series} {NN}-{NN} (omnibus)</li>
 *   <li>{Series} c{NNN} / Ch {N} / Chapter {N} / Episode {N}</li>
 *   <li>{Series} {NN} (bare trailing number)</li>
 *   <li>{NN} - {Series} (bare leading number; needs a separator, "#" or zero padding)</li>
 *   <li>otherwise unmatched</li>
 * </ol>
 * The name may come before or after the number: "Title v03", "Vol 3 - Title", "Ch 12 Title" and
 * "[Group] Chapter 12 - Title" all work. Text before the number wins when there is any; otherwise
 * the text after it is used (skipping a chapter marker, as in "Vol 2 - Ch 10 - Title").
 * Tags such as [Group], (Digital), (2019), {HQ} are stripped first. ComicInfo.xml, when present,
 * overrides this (see {@link ComicInfoReader}).
 */
public final class FileNameParser {
    private FileNameParser() {}

    public record Parsed(String series, double number, Double numberEnd, String kind, boolean matched) {
        public static Parsed unmatched(String guess) {
            return new Parsed(guess, 0, null, "volume", false);
        }
    }

    private static final String NUM = "(\\d{1,4}(?:\\.\\d{1,2})?)";
    private static final Pattern VOLUME = Pattern.compile(
            "^(.*?)[\\s,._-]*\\b(?:v|vol|vols|volume|volumes|tome)\\.?\\s*" + NUM + "(?:\\s*-\\s*(?:v|vol\\.?\\s*)?" + NUM + ")?\\b(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OMNIBUS = Pattern.compile(
            "^(.*?)\\s+(?:omnibus\\s+)?#?(\\d{1,4})\\s*-\\s*(\\d{1,4})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAPTER = Pattern.compile(
            "^(.*?)[\\s,._-]*\\b(?:c|ch|chap|chapter|ep|episode)\\.?\\s*" + NUM + "\\b(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE = Pattern.compile("^(.*?\\D)\\s*#?" + NUM + "$");
    /** "012 - Title", "07. Title", "#3 Title", "03 Title"; a plain "1 Punch Man" stays unmatched. */
    private static final Pattern LEADING = Pattern.compile("^(#)?" + NUM + "(\\s*[-–—:._)]\\s*|\\s+)(\\D.*)$");
    /** A chapter/episode marker at the start of the text after a volume number. */
    private static final Pattern SUFFIX_MARKER = Pattern.compile(
            "^[\\s,._:-]*(?:(?:c|ch|chap|chapter|ep|episode)\\.?\\s*\\d+(?:\\.\\d+)?\\b)?", Pattern.CASE_INSENSITIVE);
    /** Words after a number that are not a series name ("Tome 5 bis"). */
    private static final Pattern NOT_A_NAME = Pattern.compile(
            "(?:bis|ter|extra|extras|special|end|final|(?:part|pt)\\.? ?\\d+)?", Pattern.CASE_INSENSITIVE);

    public static Parsed parse(String fileName) {
        String name = stripExtension(fileName);
        String clean = normalise(stripTags(name));
        if (clean.isEmpty()) return Parsed.unmatched(normalise(name));

        Matcher m = VOLUME.matcher(clean);
        if (m.matches()) {
            String series = seriesFrom(m.group(1), m.group(4));
            if (!series.isEmpty()) {
                double a = Double.parseDouble(m.group(2));
                Double b = m.group(3) != null ? Double.parseDouble(m.group(3)) : null;
                return new Parsed(series, a, b, b != null ? "omnibus" : "volume", true);
            }
        }
        m = OMNIBUS.matcher(clean);
        if (m.matches() && !cleanSeries(m.group(1)).isEmpty()) {
            double a = Double.parseDouble(m.group(2)), b = Double.parseDouble(m.group(3));
            if (b > a) return new Parsed(cleanSeries(m.group(1)), a, b, "omnibus", true);
        }
        m = CHAPTER.matcher(clean);
        if (m.matches()) {
            String series = seriesFrom(m.group(1), m.group(3));
            if (!series.isEmpty()) return new Parsed(series, Double.parseDouble(m.group(2)), null, "chapter", true);
        }
        m = BARE.matcher(clean);
        if (m.matches() && !cleanSeries(m.group(1)).isEmpty() && !looksLikeYear(m.group(2))
                && !cleanSeries(m.group(1)).matches("(?i)c|ch|chap|chapter|ep|episode|v|vol|volume|tome|part|pt")) {
            return new Parsed(cleanSeries(m.group(1)), Double.parseDouble(m.group(2)), null, "volume", true);
        }
        m = LEADING.matcher(clean);
        if (m.matches() && !looksLikeYear(m.group(2))) {
            boolean hasSep = m.group(1) != null || !m.group(3).isBlank()
                    || (m.group(2).startsWith("0") && m.group(2).length() > 1);
            String series = hasSep ? seriesFrom("", m.group(4)) : "";
            if (!series.isEmpty()) return new Parsed(series, Double.parseDouble(m.group(2)), null, "volume", true);
        }
        return Parsed.unmatched(cleanSeries(clean));
    }

    /** The name before the number if there is one, otherwise the name after it. */
    private static String seriesFrom(String before, String after) {
        String s = cleanSeries(before);
        if (!s.isEmpty() || after == null) return s;
        String rest = cleanSeries(SUFFIX_MARKER.matcher(after).replaceFirst(""));
        if (NOT_A_NAME.matcher(rest).matches() || !rest.matches(".*\\p{L}.*")) return "";
        return rest;
    }

    static String stripExtension(String n) {
        int dot = n.lastIndexOf('.');
        if (dot > 0) {
            String ext = n.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (ext.matches("cbz|zip|cbr|rar|cb7|7z")) return n.substring(0, dot);
        }
        return n;
    }

    static String stripTags(String n) {
        String prev;
        String s = n;
        do {
            prev = s;
            s = s.replaceAll("\\[[^\\[\\]]*]", " ")
                    .replaceAll("\\([^()]*\\)", " ")
                    .replaceAll("\\{[^{}]*}", " ");
        } while (!s.equals(prev));
        return s;
    }

    static String normalise(String s) {
        return s.replace('_', ' ').replaceAll("\\s+", " ").trim();
    }

    static String cleanSeries(String s) {
        String t = s.replaceAll("(?i)\\b(omnibus|complete|digital)\\b", " ");
        t = t.replaceAll("[\\s,._#:-]+$", "").replaceAll("^[\\s,._#:-]+", "");
        // dots used as spaces ("Paper.Ronin")
        if (!t.contains(" ") && t.contains(".")) t = t.replace('.', ' ');
        return t.replaceAll("\\s+", " ").trim();
    }

    private static boolean looksLikeYear(String n) {
        if (n.length() != 4 || n.contains(".")) return false;
        int y = Integer.parseInt(n);
        return y >= 1950 && y <= 2100;
    }
}
