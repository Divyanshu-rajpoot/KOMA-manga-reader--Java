package app.koma.imaging;

import app.koma.archive.CbzIndex;
import app.koma.core.Exec;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Cover thumbnails: the first image of each CBZ, scaled to 360 px wide.
 * Disk cache in %LOCALAPPDATA%\Koma\thumbs keyed by hash(path + size + mtime), plus a small RAM cache.
 * Stored as JPEG: the allowed TwelveMonkeys plugin can read WebP but not write it.
 */
public final class ThumbCache {
    public static final int WIDTH = 360;
    /** Bump when decoding changes so stale thumbnails are rebuilt (v2: fixed red/blue swap from the box resize). */
    private static final int VERSION = 2;

    private final Path dir;
    private final PageCache mem = new PageCache(160L << 20);
    private final Map<String, CompletableFuture<Image>> inflight = new ConcurrentHashMap<>();

    public ThumbCache(Path dir) {
        this.dir = dir;
    }

    public static String key(String path, long size, long mtime) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest(("v" + VERSION + "|" + path.toLowerCase() + "|" + size + "|" + mtime).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Deletes KOMA's thumbnails from older cache versions (only our top-level *.jpg files).
     * Call once at startup, off the FX thread and before any cover is requested.
     */
    public static void purgeOldVersions(Path dir) {
        Path marker = dir.resolve("koma-thumbs.version");
        try {
            if (Files.isRegularFile(marker) && Files.readString(marker).trim().equals(String.valueOf(VERSION))) return;
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().matches("[0-9a-f]{40}\\.jpg")).forEach(p -> p.toFile().delete());
            }
            Files.writeString(marker, String.valueOf(VERSION));
        } catch (IOException e) {
            System.err.println("KOMA: thumbnail cache cleanup failed: " + e.getMessage());
        }
    }

    public Image peek(String key) {
        return mem.get(key);
    }

    public CompletableFuture<Image> cover(String path, long size, long mtime) {
        return cover(path, size, mtime, () -> true);
    }

    /**
     * Like {@link #cover(String, long, long)}, but {@code wanted} is checked before the expensive steps; when it turns
     * false (the cover scrolled out of view) the work is skipped and the future completes with null.
     */
    public CompletableFuture<Image> cover(String path, long size, long mtime, java.util.function.BooleanSupplier wanted) {
        String k = key(path, size, mtime);
        Image hit = mem.get(k);
        if (hit != null) return CompletableFuture.completedFuture(hit);
        return inflight.computeIfAbsent(k, kk -> {
            Path file = dir.resolve(kk + ".jpg");
            // disk work on an I/O thread, decoding on the bounded decode pool (opening a library
            // shouldn't decode dozens of covers at once on unbounded threads)
            CompletableFuture<Image> f = Exec.io(() -> {
                try {
                    if (!wanted.getAsBoolean()) return null;
                    if (Files.isRegularFile(file)) return Files.readAllBytes(file);
                    try (CbzIndex idx = CbzIndex.open(Path.of(path))) {
                        if (idx.pageCount() == 0) throw new IOException("No images in archive");
                        return idx.readPage(0);
                    }
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }).thenApplyAsync(bytes -> {
                if (bytes == null || !wanted.getAsBoolean()) return null;
                try {
                    boolean cached = Files.isRegularFile(file);
                    BufferedImage bi = PageDecoder.decodeBuffered(bytes, WIDTH, 0);
                    if (bi == null) throw new IOException("Unsupported cover image");
                    if (!cached) writeJpeg(bi, file);
                    Image img = PageDecoder.toFx(bi);
                    mem.put(kk, img);
                    return img;
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, Exec.DECODE);
            f.whenComplete((r, e) -> inflight.remove(kk));
            return f;
        });
    }

    private static void writeJpeg(BufferedImage src, Path file) throws IOException {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        var writer = ImageIO.getImageWritersByFormatName("jpg").next();
        var param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.92f);
        try (var out = ImageIO.createImageOutputStream(tmp.toFile())) {
            writer.setOutput(out);
            writer.write(null, new javax.imageio.IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Disk usage in bytes. Call off the FX thread. */
    public long diskBytes() {
        try (Stream<Path> s = Files.list(dir)) {
            return s.mapToLong(p -> p.toFile().length()).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Deletes all cached thumbnails. Call off the FX thread. */
    public void clear() {
        mem.clear();
        try (Stream<Path> s = Files.list(dir)) {
            s.forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // best effort
        }
    }
}
