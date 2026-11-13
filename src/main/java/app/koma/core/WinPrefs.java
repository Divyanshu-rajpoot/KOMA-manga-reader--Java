package app.koma.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Reads the two Windows preferences KOMA follows: app theme and "Show animations". Call off the FX thread. */
public final class WinPrefs {
    private WinPrefs() {}

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** True when Windows apps are set to dark mode. */
    public static boolean systemDark() {
        Integer v = regDword("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "AppsUseLightTheme");
        return v != null && v == 0;
    }

    /** True when "Animation effects" is switched off in Windows accessibility settings. */
    public static boolean systemReduceMotion() {
        String v = regString("HKCU\\Control Panel\\Desktop\\WindowMetrics", "MinAnimate");
        return "0".equals(v);
    }

    private static Integer regDword(String key, String value) {
        String raw = regString(key, value);
        if (raw == null) return null;
        try {
            return raw.startsWith("0x") ? Integer.parseInt(raw.substring(2), 16) : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String regString(String key, String value) {
        if (!isWindows()) return null;
        try {
            Process p = new ProcessBuilder("reg", "query", key, "/v", value).redirectErrorStream(true).start();
            String result = null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String t = line.trim();
                    if (t.startsWith(value + " ") || t.startsWith(value + "\t")) {
                        String[] parts = t.split("\\s+");
                        result = parts[parts.length - 1];
                    }
                }
            }
            p.waitFor(2, TimeUnit.SECONDS);
            return result;
        } catch (Exception e) {
            return null;
        }
    }
}
