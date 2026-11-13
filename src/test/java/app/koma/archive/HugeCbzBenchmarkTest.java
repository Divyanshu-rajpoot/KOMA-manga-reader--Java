package app.koma.archive;

import app.koma.imaging.PageDecoder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generates a ~5 GB ZIP64 CBZ (stored pages, like real scans) and checks the targets from ARCHITECTURE.md:
 * open under 300 ms, first page under 150 ms. Slow to generate, so it only runs with: mvn test -Phuge
 */
@Tag("huge")
class HugeCbzBenchmarkTest {
    @TempDir
    Path dir;

    @Test
    void opensFiveGigabyteCbzQuickly() throws Exception {
        Path f = dir.resolve("huge v01.cbz");
        byte[] padded = TestCbz.noisyJpeg(2000, 3000); // a real ~2-4 MB high-detail scan, no padding tricks
        long target = 5L * 1024 * 1024 * 1024;
        int pages = (int) (target / padded.length) + 1; // ≈ 5 GB
        try (OutputStream fo = Files.newOutputStream(f); ZipOutputStream zip = new ZipOutputStream(new java.io.BufferedOutputStream(fo, 1 << 20))) {
            for (int i = 1; i <= pages; i++) TestCbz.putStored(zip, String.format("%04d.jpg", i), padded);
        }
        long size = Files.size(f);
        assertTrue(size > 4L * 1024 * 1024 * 1024, "file must exceed 4 GB to need ZIP64");

        PageDecoder.warmUp(); // the app does this at startup, off the FX thread
        long t0 = System.nanoTime();
        try (CbzIndex idx = CbzIndex.open(f)) {
            double openMs = (System.nanoTime() - t0) / 1e6;
            long t1 = System.nanoTime();
            byte[] bytes = idx.readPage(pages / 2);
            double readMs = (System.nanoTime() - t1) / 1e6;
            var img = PageDecoder.decode(bytes, 1000, 1400);
            double firstMs = (System.nanoTime() - t1) / 1e6;
            System.out.printf("%.2f GB CBZ, %d pages of %.1f MB: open %.1f ms, first page %.1f ms (read %.1f ms) -> %dx%d%n",
                    size / 1e9, pages, bytes.length / 1e6, openMs, firstMs, readMs, (int) img.getWidth(), (int) img.getHeight());
            long t2 = System.nanoTime();
            PageDecoder.decode(idx.readPage(pages / 2 + 1), 1000, 1400);
            System.out.printf("next page (warm decoder): %.1f ms%n", (System.nanoTime() - t2) / 1e6);
            assertEquals(pages, idx.pageCount());
            assertTrue(openMs < 300, "open took " + openMs + " ms");
            assertTrue(firstMs < 150, "first page took " + firstMs + " ms");
        }
    }
}
