package app.koma.imaging;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * Decodes page bytes to a JavaFX image at (at most) the on-screen pixel size.
 * Large sources are subsampled while decoding, then downscaled in halving steps for quality.
 * Never call on the FX thread.
 */
public final class PageDecoder {
    private PageDecoder() {}

    static {
        ImageIO.setUseCache(false); // no temp files; everything stays in memory
        ImageIO.scanForPlugins();
    }

    public record Size(int width, int height) {
        public boolean wide() {
            return width > height;
        }
    }

    /**
     * Loads and registers the ImageIO plugins (JPEG, PNG, WebP…) by decoding a tiny image.
     * The first decode in a fresh JVM otherwise costs ~0.5–0.8 s of class loading. Call off the FX thread at startup.
     */
    public static void warmUp() {
        try {
            BufferedImage tiny = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            ImageIO.write(tiny, "jpg", out);
            decode(out.toByteArray(), 3, 3); // full path: ImageIO, box halving, bilinear, JavaFX image
            ImageIO.getImageReadersByFormatName("webp").hasNext();
        } catch (Exception ignored) {
            // warm-up is best effort
        }
    }

    /** Reads width and height from the image header only. */
    public static Size readSize(InputStream in) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            ImageReader r = reader(iis);
            if (r == null) throw new IOException("Unsupported image");
            try {
                r.setInput(iis, true, true);
                return new Size(r.getWidth(0), r.getHeight(0));
            } finally {
                r.dispose();
            }
        }
    }

    /** Decode and fit inside maxW x maxH (0 = unbounded). Never upscales. */
    public static Image decode(byte[] data, int maxW, int maxH) throws IOException {
        BufferedImage bi = decodeBuffered(data, maxW, maxH);
        if (bi == null) {
            // Last resort: JavaFX's own decoder (handles a few odd PNG/GIF variants).
            Image img = new Image(new ByteArrayInputStream(data), Math.max(0, maxW), Math.max(0, maxH), true, true);
            if (img.isError()) throw new IOException("Cannot decode image", img.getException());
            return img;
        }
        return toFx(bi);
    }

    public static BufferedImage decodeBuffered(byte[] data, int maxW, int maxH) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            ImageReader r = reader(iis);
            if (r == null) return null;
            try {
                r.setInput(iis, true, true);
                int sw = r.getWidth(0), sh = r.getHeight(0);
                double scale = fitScale(sw, sh, maxW, maxH);
                int tw = Math.max(1, (int) Math.round(sw * scale));
                int th = Math.max(1, (int) Math.round(sh * scale));
                ImageReadParam p = r.getDefaultReadParam();
                int sub = Math.max(1, sw / (tw * 2)); // keep at least 2x for a clean final downscale
                if (sub > 1) p.setSourceSubsampling(sub, sub, 0, 0);
                BufferedImage src = r.read(0, p);
                return resize(src, tw, th);
            } finally {
                r.dispose();
            }
        }
    }

    static double fitScale(int sw, int sh, int maxW, int maxH) {
        double s = 1;
        if (maxW > 0) s = Math.min(s, (double) maxW / sw);
        if (maxH > 0) s = Math.min(s, (double) maxH / sh);
        return s;
    }

    /**
     * Halves with a 2x2 box average (reads the raster directly: fast, and averaging keeps screentone
     * from turning into moiré), then a final bilinear step (the remaining scale is always ≥ 0.5).
     * Output is INT_ARGB_PRE.
     */
    static BufferedImage resize(BufferedImage src, int tw, int th) {
        BufferedImage cur = src;
        int w = cur.getWidth(), h = cur.getHeight();
        while (w / 2 >= tw && h / 2 >= th) {
            cur = halve(cur);
            w = cur.getWidth();
            h = cur.getHeight();
        }
        if (w != tw || h != th || cur.getType() != BufferedImage.TYPE_INT_ARGB_PRE) {
            cur = draw(cur, tw, th, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return cur;
    }

    /** 2x2 box downsample to INT_ARGB_PRE for opaque 8-bit sources; other types fall back to Java2D. */
    static BufferedImage halve(BufferedImage src) {
        int sw = src.getWidth(), sh = src.getHeight();
        int w = sw / 2, h = sh / 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        int[] dst = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
        var raster = src.getRaster();
        var sm = raster.getSampleModel();
        int type = src.getType();
        if ((type == BufferedImage.TYPE_3BYTE_BGR || type == BufferedImage.TYPE_BYTE_GRAY)
                && raster.getDataBuffer() instanceof java.awt.image.DataBufferByte db
                && sm instanceof java.awt.image.PixelInterleavedSampleModel ps
                && raster.getSampleModelTranslateX() == 0 && raster.getSampleModelTranslateY() == 0) {
            byte[] b = db.getData();
            int stride = ps.getScanlineStride(), px = ps.getPixelStride();
            int[] off = ps.getBandOffsets();
            boolean gray = type == BufferedImage.TYPE_BYTE_GRAY;
            // band offsets are indexed by band (R, G, B), not by byte position
            int ro = off[0], go = gray ? off[0] : off[1], bo = gray ? off[0] : off[2];
            for (int y = 0; y < h; y++) {
                int r0 = (2 * y) * stride, r1 = r0 + stride, d = y * w;
                for (int x = 0; x < w; x++) {
                    int a = r0 + 2 * x * px, c = r1 + 2 * x * px;
                    int r = (b[a + ro] & 0xFF) + (b[a + px + ro] & 0xFF) + (b[c + ro] & 0xFF) + (b[c + px + ro] & 0xFF);
                    int g = (b[a + go] & 0xFF) + (b[a + px + go] & 0xFF) + (b[c + go] & 0xFF) + (b[c + px + go] & 0xFF);
                    int bl = (b[a + bo] & 0xFF) + (b[a + px + bo] & 0xFF) + (b[c + bo] & 0xFF) + (b[c + px + bo] & 0xFF);
                    dst[d + x] = 0xFF000000 | ((r + 2) >> 2) << 16 | ((g + 2) >> 2) << 8 | ((bl + 2) >> 2);
                }
            }
            return out;
        }
        if ((type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB_PRE)
                && raster.getDataBuffer() instanceof DataBufferInt di
                && sm instanceof java.awt.image.SinglePixelPackedSampleModel sp
                && raster.getSampleModelTranslateX() == 0 && raster.getSampleModelTranslateY() == 0) {
            int[] s = di.getData();
            int stride = sp.getScanlineStride();
            boolean opaque = type == BufferedImage.TYPE_INT_RGB;
            for (int y = 0; y < h; y++) {
                int r0 = (2 * y) * stride, r1 = r0 + stride, d = y * w;
                for (int x = 0; x < w; x++) {
                    int p0 = s[r0 + 2 * x], p1 = s[r0 + 2 * x + 1], p2 = s[r1 + 2 * x], p3 = s[r1 + 2 * x + 1];
                    int a = opaque ? 0xFF : ((p0 >>> 24) + (p1 >>> 24) + (p2 >>> 24) + (p3 >>> 24) + 2) >> 2;
                    int r = (((p0 >> 16) & 0xFF) + ((p1 >> 16) & 0xFF) + ((p2 >> 16) & 0xFF) + ((p3 >> 16) & 0xFF) + 2) >> 2;
                    int g = (((p0 >> 8) & 0xFF) + ((p1 >> 8) & 0xFF) + ((p2 >> 8) & 0xFF) + ((p3 >> 8) & 0xFF) + 2) >> 2;
                    int bl = ((p0 & 0xFF) + (p1 & 0xFF) + (p2 & 0xFF) + (p3 & 0xFF) + 2) >> 2;
                    dst[d + x] = a << 24 | r << 16 | g << 8 | bl;
                }
            }
            return out;
        }
        return draw(src, w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h, Object interp) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Copies an INT_ARGB_PRE image into a WritableImage (safe off the FX thread). */
    public static WritableImage toFx(BufferedImage bi) {
        if (bi.getType() != BufferedImage.TYPE_INT_ARGB_PRE) bi = draw(bi, bi.getWidth(), bi.getHeight(), RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int w = bi.getWidth(), h = bi.getHeight();
        int[] px = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();
        WritableImage wi = new WritableImage(w, h);
        wi.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbPreInstance(), px, 0, w);
        return wi;
    }

    private static ImageReader reader(ImageInputStream iis) {
        if (iis == null) return null;
        Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
        return it.hasNext() ? it.next() : null;
    }
}
