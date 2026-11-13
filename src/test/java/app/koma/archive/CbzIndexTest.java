package app.koma.archive;

import app.koma.imaging.PageDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CbzIndexTest {
    @TempDir
    Path dir;

    @Test
    void listsOnlyImagesInNaturalOrder() throws Exception {
        byte[] img = TestCbz.jpeg(60, 90, Color.RED);
        Path f = TestCbz.write(dir.resolve("Paper Ronin v07.cbz"), List.of(
                "Paper Ronin/", "Paper Ronin/page10.jpg", "Paper Ronin/page2.jpg", "Paper Ronin/page1.jpg",
                "__MACOSX/Paper Ronin/._page1.jpg", "Paper Ronin/Thumbs.db", "Paper Ronin/notes.txt",
                "ComicInfo.xml", "Paper Ronin/.hidden.jpg"), img,
                "<ComicInfo><Series>Paper Ronin</Series><Number>7</Number></ComicInfo>".getBytes(StandardCharsets.UTF_8));
        try (CbzIndex idx = CbzIndex.open(f)) {
            assertEquals(3, idx.pageCount());
            assertEquals(List.of("Paper Ronin/page1.jpg", "Paper Ronin/page2.jpg", "Paper Ronin/page10.jpg"),
                    idx.pages().stream().map(CbzIndex.PageRef::name).toList());
            assertArrayEquals(img, idx.readPage(1));
            assertNotNull(idx.readComicInfo());
        }
    }

    @Test
    void readsHeaderSizeAndDecodesToTargetSize() throws Exception {
        Path f = TestCbz.write(dir.resolve("a.cbz"), List.of("001.jpg"), TestCbz.jpeg(1200, 1800, Color.BLUE), null);
        try (CbzIndex idx = CbzIndex.open(f)) {
            try (InputStream in = idx.openPage(0)) {
                PageDecoder.Size s = PageDecoder.readSize(in);
                assertEquals(1200, s.width());
                assertEquals(1800, s.height());
            }
            var img = PageDecoder.decode(idx.readPage(0), 400, 400);
            assertTrue(img.getHeight() <= 400 && img.getWidth() <= 400, "fits the box");
            assertEquals(400, (int) img.getHeight());
            assertEquals(267, (int) Math.round(img.getWidth()));
        }
    }

    @Test
    void neverUpscales() throws Exception {
        Path f = TestCbz.write(dir.resolve("b.cbz"), List.of("1.jpg"), TestCbz.jpeg(100, 150, Color.GREEN), null);
        try (CbzIndex idx = CbzIndex.open(f)) {
            var img = PageDecoder.decode(idx.readPage(0), 2000, 3000);
            assertEquals(100, (int) img.getWidth());
        }
    }

    @Test
    void rejectsNonZip() throws Exception {
        Path f = dir.resolve("broken.cbz");
        Files.writeString(f, "this is not a zip file");
        assertThrows(Exception.class, () -> CbzIndex.open(f));
    }

    /** More than 65,535 entries forces the ZIP64 end-of-central-directory records, cheaply. */
    @Test
    void opensZip64ByEntryCount() throws Exception {
        Path f = dir.resolve("many.cbz");
        int n = 70_000;
        byte[] tiny = {(byte) 0xFF, (byte) 0xD8, 0, 0};
        try (OutputStream fo = Files.newOutputStream(f); ZipOutputStream zip = new ZipOutputStream(fo)) {
            for (int i = n; i >= 1; i--) TestCbz.putStored(zip, "p" + i + ".jpg", tiny);
        }
        long t0 = System.nanoTime();
        try (CbzIndex idx = CbzIndex.open(f)) {
            double ms = (System.nanoTime() - t0) / 1e6;
            assertEquals(n, idx.pageCount());
            assertEquals("p1.jpg", idx.pages().get(0).name());
            assertEquals("p70000.jpg", idx.pages().get(n - 1).name());
            System.out.printf("ZIP64 (70k entries) opened in %.1f ms%n", ms);
        }
    }

    @Test
    void naturalSort() {
        List<String> names = new ArrayList<>(List.of("p10.jpg", "p2.jpg", "P1.jpg", "p01.jpg", "a/p3.jpg", "a b/p1.jpg", "p2a.jpg"));
        names.sort(NaturalSort.INSTANCE);
        assertEquals(List.of("a/p3.jpg", "a b/p1.jpg", "P1.jpg", "p01.jpg", "p2.jpg", "p2a.jpg", "p10.jpg"), names);
    }

    @Test
    void imageNames() {
        assertTrue(ImageSniffer.isPageName("x/001.JPG"));
        assertTrue(ImageSniffer.isPageName("002.webp"));
        assertTrue(!ImageSniffer.isPageName("__MACOSX/x/001.jpg"));
        assertTrue(!ImageSniffer.isPageName("x/"));
        assertTrue(!ImageSniffer.isPageName("ComicInfo.xml"));
        assertEquals("jpeg", ImageSniffer.sniff(new byte[]{(byte) 0xFF, (byte) 0xD8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
    }
}
