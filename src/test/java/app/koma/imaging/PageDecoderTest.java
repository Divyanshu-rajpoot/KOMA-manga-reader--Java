package app.koma.imaging;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageDecoderTest {

    private static void check(BufferedImage src) {
        // 4x2 → 2x1: left block averages (10,20,30,40), right block is a flat 200
        int[] left = {10, 20, 30, 40};
        src.setRGB(0, 0, gray(left[0]));
        src.setRGB(1, 0, gray(left[1]));
        src.setRGB(0, 1, gray(left[2]));
        src.setRGB(1, 1, gray(left[3]));
        for (int y = 0; y < 2; y++) for (int x = 2; x < 4; x++) src.setRGB(x, y, gray(200));
        BufferedImage out = PageDecoder.halve(src);
        assertEquals(BufferedImage.TYPE_INT_ARGB_PRE, out.getType());
        assertEquals(2, out.getWidth());
        assertEquals(1, out.getHeight());
        assertEquals(25, out.getRGB(0, 0) & 0xFF, "box average of 10,20,30,40");
        assertEquals(200, out.getRGB(1, 0) & 0xFF);
        assertEquals(0xFF, out.getRGB(0, 0) >>> 24, "opaque");
    }

    private static int gray(int v) {
        return 0xFF000000 | v << 16 | v << 8 | v;
    }

    @Test
    void halvesBgr() {
        check(new BufferedImage(4, 2, BufferedImage.TYPE_3BYTE_BGR));
    }

    @Test
    void halvesIntRgb() {
        check(new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB));
    }

    /** Colored pixels catch swapped channels, which gray test pixels can't. */
    @Test
    void keepsColorChannelsInOrder() {
        for (int type : new int[]{BufferedImage.TYPE_3BYTE_BGR, BufferedImage.TYPE_INT_RGB}) {
            BufferedImage src = new BufferedImage(2, 2, type);
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) src.setRGB(x, y, 0xFFE3242B); // ink red
            int p = PageDecoder.halve(src).getRGB(0, 0);
            assertEquals(0xE3, (p >> 16) & 0xFF, "red, type " + type);
            assertEquals(0x24, (p >> 8) & 0xFF, "green, type " + type);
            assertEquals(0x2B, p & 0xFF, "blue, type " + type);
        }
    }

    @Test
    void halvesGray() {
        BufferedImage g = new BufferedImage(4, 2, BufferedImage.TYPE_BYTE_GRAY);
        g.getRaster().setSample(0, 0, 0, 10);
        g.getRaster().setSample(1, 0, 0, 20);
        g.getRaster().setSample(0, 1, 0, 30);
        g.getRaster().setSample(1, 1, 0, 40);
        BufferedImage out = PageDecoder.halve(g);
        assertEquals(25, out.getRGB(0, 0) & 0xFF);
    }

    @Test
    void resizeHitsExactTarget() {
        BufferedImage src = new BufferedImage(2000, 3000, BufferedImage.TYPE_3BYTE_BGR);
        BufferedImage out = PageDecoder.resize(src, 933, 1400);
        assertEquals(933, out.getWidth());
        assertEquals(1400, out.getHeight());
    }
}
