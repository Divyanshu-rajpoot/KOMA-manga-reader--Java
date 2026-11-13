package app.koma.imaging;

import javafx.scene.image.Image;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** LRU of decoded images, weighted by pixel bytes (w*h*4), not by count. */
public final class PageCache {
    private final LinkedHashMap<String, Image> map = new LinkedHashMap<>(128, 0.75f, true);
    private long bytes;
    private long budget;

    public PageCache(long budgetBytes) {
        this.budget = budgetBytes;
    }

    public static long weight(Image img) {
        return (long) img.getWidth() * (long) img.getHeight() * 4L;
    }

    public synchronized Image get(String key) {
        return map.get(key);
    }

    public synchronized boolean contains(String key) {
        return map.containsKey(key);
    }

    /** True if any key starts with prefix (e.g. a page decoded at any size). */
    public synchronized boolean containsPrefix(String prefix) {
        for (String k : map.keySet()) if (k.startsWith(prefix)) return true;
        return false;
    }

    public synchronized void put(String key, Image img) {
        Image old = map.put(key, img);
        if (old != null) bytes -= weight(old);
        bytes += weight(img);
        trim();
    }

    public synchronized void setBudget(long budgetBytes) {
        this.budget = budgetBytes;
        trim();
    }

    public synchronized void removeIf(Predicate<String> keyTest) {
        Iterator<Map.Entry<String, Image>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (keyTest.test(e.getKey())) {
                bytes -= weight(e.getValue());
                it.remove();
            }
        }
    }

    public synchronized void clear() {
        map.clear();
        bytes = 0;
    }

    public synchronized long bytes() {
        return bytes;
    }

    public synchronized int size() {
        return map.size();
    }

    private void trim() {
        Iterator<Map.Entry<String, Image>> it = map.entrySet().iterator();
        while (bytes > budget && it.hasNext()) {
            var e = it.next();
            bytes -= weight(e.getValue());
            it.remove();
        }
    }
}
