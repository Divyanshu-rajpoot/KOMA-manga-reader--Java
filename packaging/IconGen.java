import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Regenerates the KOMA app icons (window PNGs + packaging/koma.ico) from one geometry:
 * accent square, bold white コ (same path as Icons "koma": M6 6h12v12H6v-3h9V9H6z on a 24 grid),
 * and a hard ink offset shadow (Ink Archive style). Run from the project root:
 *   java packaging/IconGen.java
 */
public class IconGen {
    static final Color ACC = new Color(0xE3242B), INK = new Color(0x0E0E0E), WHITE = Color.WHITE;

    static BufferedImage render(int n) {
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        boolean shadow = n >= 32;                       // too small to read below 32 px
        int off = shadow ? Math.max(2, Math.round(n * 0.08f)) : 0;
        int sq = n - off;                               // red square size
        if (shadow) { g.setColor(INK); g.fillRect(off, off, sq, sq); }
        g.setColor(ACC); g.fillRect(0, 0, sq, sq);
        // コ on a 24-unit grid inside the square, snapped to whole pixels for crisp small sizes
        double u = sq / 24.0;
        int a = (int) Math.round(6 * u), b = (int) Math.round(18 * u);
        int t = Math.max(2, (int) Math.round(3 * u));
        Path2D p = new Path2D.Double();
        p.moveTo(a, a); p.lineTo(b, a); p.lineTo(b, b); p.lineTo(a, b);
        p.lineTo(a, b - t); p.lineTo(b - t, b - t); p.lineTo(b - t, a + t); p.lineTo(a, a + t); p.closePath();
        g.setColor(WHITE); g.fill(p);
        g.dispose();
        return img;
    }

    static byte[] png(BufferedImage img) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        Path res = Path.of("src/main/resources/app/koma/icon");
        for (int s : new int[]{16, 32, 64, 128, 256}) Files.write(res.resolve("koma-" + s + ".png"), png(render(s)));

        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        List<byte[]> data = new ArrayList<>();
        for (int s : sizes) data.add(png(render(s)));
        var out = new ByteArrayOutputStream();
        var d = new DataOutputStream(out);
        d.writeShort(0); d.writeShort(Short.reverseBytes((short) 1)); d.writeShort(Short.reverseBytes((short) sizes.length));
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            d.writeByte(s >= 256 ? 0 : s); d.writeByte(s >= 256 ? 0 : s); d.writeByte(0); d.writeByte(0);
            d.writeShort(Short.reverseBytes((short) 1)); d.writeShort(Short.reverseBytes((short) 32));
            d.writeInt(Integer.reverseBytes(data.get(i).length)); d.writeInt(Integer.reverseBytes(offset));
            offset += data.get(i).length;
        }
        for (byte[] bytes : data) d.write(bytes);
        Files.write(Path.of("packaging/koma.ico"), out.toByteArray());
        System.out.println("icons written");
    }
}
