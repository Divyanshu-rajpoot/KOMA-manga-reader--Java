package app.koma.imaging;

import app.koma.archive.CbzIndex;
import app.koma.core.Exec;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
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

    private final Path dir;
    private final PageCache mem = new PageCache(160L << 20);
    private final Map<String, CompletableFuture<Image>> inflight = new ConcurrentHashMap<>();

    public ThumbCache(Path dir) {
        this.dir = dir;
    }

    public static String key(String path, long size, long mtime) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest((path.toLowerCase() + "|" + size + "|" + mtime).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Image peek(String key) {
        return mem.get(key);
    }

    public CompletableFuture<Image> cover(String path, long size, long mtime) {
        String k = key(path, size, mtime);
        Image hit = mem.get(k);
        if (hit != null) return CompletableFuture.completedFuture(hit);
        return inflight.computeIfAbsent(k, kk -> {
            CompletableFuture<Image> f = Exec.io(() -> {
                Path file = dir.resolve(kk + ".jpg");
                try {
                    if (Files.isRegularFile(file)) {
                        try (InputStream in = Files.newInputStream(file)) {
                            Image img = new Image(in);
                            if (!img.isError()) {
                                mem.put(kk, img);
                                return img;
                            }
                        }
                    }
                    byte[] first;
                    try (CbzIndex idx = CbzIndex.open(Path.of(path))) {
                        if (idx.pageCount() == 0) throw new IOException("No images in archive");
                        first = idx.readPage(0);
                    }
                    BufferedImage bi = PageDecoder.decodeBuffered(first, WIDTH, 0);
                    if (bi == null) throw new IOException("Unsupported cover image");
                    writeJpeg(bi, file);
                    Image img = PageDecoder.toFx(bi);
                    mem.put(kk, img);
                    return img;
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            });
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
        ImageIO.write(rgb, "jpg", tmp.toFile());
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
