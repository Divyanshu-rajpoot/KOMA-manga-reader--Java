package app.koma.archive;

import java.util.Locale;
import java.util.Set;

/** Decides which archive entries are pages. */
public final class ImageSniffer {
    private ImageSniffer() {}

    private static final Set<String> EXT = Set.of("jpg", "jpeg", "jfif", "png", "gif", "bmp", "webp");

    public static boolean isPageName(String entryName) {
        String n = entryName.replace('\\', '/');
        if (n.endsWith("/")) return false;
        String lower = n.toLowerCase(Locale.ROOT);
        if (lower.startsWith("__macosx/") || lower.contains("/__macosx/")) return false;
        String file = lower.substring(lower.lastIndexOf('/') + 1);
        if (file.startsWith("._") || file.startsWith(".")) return false;
        if (file.equals("thumbs.db")) return false;
        int dot = file.lastIndexOf('.');
        return dot > 0 && EXT.contains(file.substring(dot + 1));
    }

    /** Format from magic bytes: "jpeg", "png", "gif", "bmp", "webp" or null. */
    public static String sniff(byte[] head) {
        if (head == null || head.length < 12) return null;
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8) return "jpeg";
        if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') return "png";
        if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F') return "gif";
        if (head[0] == 'B' && head[1] == 'M') return "bmp";
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') return "webp";
        return null;
    }
}
