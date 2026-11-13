package app.koma.archive;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds CBZ files for tests. */
public final class TestCbz {
    private TestCbz() {}

    public static byte[] jpeg(int w, int h, Color c) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.drawLine(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    /** A detailed (hard to compress) page, like a high-resolution scan. */
    public static byte[] noisyJpeg(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.util.Random r = new java.util.Random(7);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = 200 + r.nextInt(56) - ((x / 40 + y / 40) % 2 == 0 ? 120 : 0);
                v = Math.max(0, Math.min(255, v));
                row[x] = (v << 16) | (v << 8) | v;
            }
            img.setRGB(0, y, w, 1, row, 0, w);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    /** Writes the named entries; every name ending in .jpg gets the given image bytes. Entries are STORED like real CBZs. */
    public static Path write(Path file, List<String> names, byte[] image, byte[] comicInfo) throws IOException {
        try (OutputStream fo = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(fo)) {
            for (String n : names) {
                byte[] data = n.endsWith("/") ? new byte[0]
                        : n.equalsIgnoreCase("ComicInfo.xml") ? comicInfo : image;
                putStored(zip, n, data);
            }
        }
        return file;
    }

    public static void putStored(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.STORED);
        e.setSize(data.length);
        e.setCompressedSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        e.setCrc(crc.getValue());
        zip.putNextEntry(e);
        zip.write(data);
        zip.closeEntry();
    }
}
