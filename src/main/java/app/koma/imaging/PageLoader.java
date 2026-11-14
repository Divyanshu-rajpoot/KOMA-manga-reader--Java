package app.koma.imaging;

import app.koma.archive.CbzIndex;
import app.koma.core.Exec;
import javafx.scene.image.Image;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loads pages of one open volume: bytes on an I/O thread, decoding on the decode pool,
 * results in the shared byte-weighted {@link PageCache}. Preloads around the current page and
 * drops stale preload work when the reader jumps.
 */
public final class PageLoader implements AutoCloseable {
    private final CbzIndex index;
    private final PageCache cache;
    private final PageCache thumbCache;
    private final String volKey;
    private final Map<String, CompletableFuture<Image>> inflight = new ConcurrentHashMap<>();
    private final AtomicInteger generation = new AtomicInteger();
    private final PageDecoder.Size[] sizes;
    private volatile boolean closed;

    public PageLoader(CbzIndex index, PageCache cache, PageCache thumbCache, String volKey) {
        this.index = index;
        this.cache = cache;
        this.thumbCache = thumbCache;
        this.volKey = volKey;
        this.sizes = new PageDecoder.Size[index.pageCount()];
    }

    public CbzIndex index() {
        return index;
    }

    public int pageCount() {
        return index.pageCount();
    }

    /** Exact sizes: a page decoded even slightly larger than it's shown gets rescaled by the view and looks soft. */
    private static int bucket(int v) {
        return Math.max(0, v);
    }

    private String key(int page, int w, int h) {
        return volKey + ":" + page + ":" + bucket(w) + "x" + bucket(h);
    }

    /** Cached image for this size, or null. FX-thread safe. */
    public Image cached(int page, int maxW, int maxH) {
        return cache.get(key(page, maxW, maxH));
    }

    public boolean isCached(int page, int maxW, int maxH) {
        return cache.contains(key(page, maxW, maxH));
    }

    /** Load now (user is waiting): not subject to preload cancellation. */
    public CompletableFuture<Image> load(int page, int maxW, int maxH) {
        return load(page, maxW, maxH, -1, cache);
    }

    /** Small thumbnail for the reader's thumbnail strip. */
    public CompletableFuture<Image> thumb(int page, int height) {
        return load(page, 0, height, -1, thumbCache);
    }

    public Image cachedThumb(int page, int height) {
        return thumbCache.get(key(page, 0, height));
    }

    /** Preload +ahead / -behind around center. Bumps the generation so older preloads are skipped. */
    public void preload(int center, int ahead, int behind, int maxW, int maxH) {
        int gen = generation.incrementAndGet();
        for (int d = 1; d <= Math.max(ahead, behind); d++) {
            if (d <= ahead && center + d < pageCount()) load(center + d, maxW, maxH, gen, cache);
            if (d <= behind && center - d >= 0) load(center - d, maxW, maxH, gen, cache);
        }
    }

    private CompletableFuture<Image> load(int page, int maxW, int maxH, int gen, PageCache target) {
        if (page < 0 || page >= pageCount()) return CompletableFuture.failedFuture(new IndexOutOfBoundsException(page));
        int bw = bucket(maxW), bh = bucket(maxH);
        String k = key(page, maxW, maxH);
        Image hit = target.get(k);
        if (hit != null) return CompletableFuture.completedFuture(hit);
        // Preloads (gen >= 0) can be dropped and complete with null, so user loads never share their future.
        String flightKey = k + (target == thumbCache ? "#t" : "") + (gen >= 0 ? "#p" : "#u");
        if (gen >= 0) {
            CompletableFuture<Image> userLoad = inflight.get(k + "#u");
            if (userLoad != null) return userLoad;
        }
        return inflight.computeIfAbsent(flightKey, ik -> {
            CompletableFuture<Image> f = Exec.io(() -> {
                        if (closed || (gen >= 0 && gen != generation.get())) return null;
                        try {
                            return index.readPage(page);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    })
                    .thenApplyAsync(bytes -> {
                        if (bytes == null || closed || (gen >= 0 && gen != generation.get())) return null;
                        try {
                            Image img = PageDecoder.decode(bytes, bw, bh);
                            // decoded size has the page's aspect ratio, which is all layout needs
                            if (sizes[page] == null) {
                                sizes[page] = new PageDecoder.Size((int) img.getWidth(), (int) img.getHeight());
                            }
                            target.put(k, img);
                            return img;
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    }, Exec.DECODE);
            f.whenComplete((r, e) -> inflight.remove(ik));
            return f;
        });
    }

    /** Page dimensions from the image header (cheap; no full decode). */
    public PageDecoder.Size size(int page) throws Exception {
        PageDecoder.Size s = sizes[page];
        if (s != null) return s;
        try (InputStream in = index.openPage(page)) {
            s = PageDecoder.readSize(in);
        }
        sizes[page] = s;
        return s;
    }

    public PageDecoder.Size knownSize(int page) {
        return sizes[page];
    }

    @Override
    public void close() {
        closed = true;
        generation.incrementAndGet();
        Exec.io(index::close);
    }
}
