package app.koma.library;

import app.koma.imaging.PageCache;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryStoreTest {
    @TempDir
    Path dir;

    private static Volume vol(double n, String path, int pages) {
        Volume v = new Volume();
        v.number = n;
        v.path = path;
        v.pages = pages;
        v.size = 1000;
        return v;
    }

    @Test
    void addsGroupsSavesAndReloads() {
        Path file = dir.resolve("library.json");
        LibraryStore store = LibraryStore.load(file);
        assertTrue(store.series().isEmpty());

        Volume v7 = store.addVolume("Paper Ronin", "ペーパーローニン", "manga", "rtl", vol(7, "D:\\Manga\\Paper Ronin v07.cbz", 208));
        store.addVolume("paper ronin", null, "manga", "rtl", vol(8, "D:\\Manga\\Paper Ronin v08.cbz", 200));
        store.addVolume("Tidebreaker", null, "manhwa", "ttb", vol(1, "D:\\Manhwa\\Tidebreaker c001.cbz", 60));
        assertEquals(2, store.series().size(), "same title (any case) joins the existing series");
        Series pr = store.findSeries("paper-ronin");
        assertNotNull(pr);
        assertEquals(2, pr.volumes.size());

        store.updateProgress(v7, 141);
        store.addSession(v7, 103, 141);
        store.saveNow();

        LibraryStore again = LibraryStore.load(file);
        Volume r = again.findVolume(v7.id);
        assertNotNull(r);
        assertEquals(141, r.progress.page);
        assertFalse(r.progress.finished);
        assertEquals(1, r.sessions.size());
        assertEquals(104, r.sessions.get(0).from);
        assertEquals("ペーパーローニン", again.findSeries("paper-ronin").titleJp);
        assertTrue(again.containsPath("d:\\manga\\paper ronin v07.cbz"), "path match ignores case on Windows");
    }

    @Test
    void lastPageMarksFinishedAndCurrentMovesOn() {
        LibraryStore store = LibraryStore.load(dir.resolve("l.json"));
        Volume a = store.addVolume("X", null, "manga", "rtl", vol(1, "a.cbz", 10));
        Volume b = store.addVolume("X", null, "manga", "rtl", vol(2, "b.cbz", 10));
        Series s = store.seriesOf(a);
        assertSame(a, s.current());
        store.updateProgress(a, 9);
        assertTrue(a.isFinished());
        assertSame(b, s.current(), "after finishing vol 1, resume points at vol 2");
        assertEquals("reading", s.status());
        store.setFinished(b, true);
        assertEquals("finished", s.status());
        store.setFinished(b, false);
        assertNull(b.progress);
        store.saveNow();
    }

    @Test
    void removingLastVolumeRemovesSeries() {
        LibraryStore store = LibraryStore.load(dir.resolve("l.json"));
        Volume a = store.addVolume("Solo", null, "manga", "rtl", vol(1, "a.cbz", 10));
        store.removeVolume(a);
        assertTrue(store.series().isEmpty());
        store.saveNow();
    }

    @Test
    void corruptFileIsKeptAndLibraryStartsEmpty() throws Exception {
        Path file = dir.resolve("library.json");
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);
        LibraryStore store = LibraryStore.load(file);
        assertTrue(store.series().isEmpty());
        assertTrue(Files.exists(dir.resolve("library.json.broken")));
    }

    @Test
    void slugs() {
        assertEquals("paper-ronin", LibraryStore.slug("Paper Ronin"));
        assertEquals("pokemon-adventures", LibraryStore.slug("Pokémon: Adventures!"));
        assertEquals("ペーパーローニン", LibraryStore.slug("ペーパーローニン"));
    }

    @Test
    void comicInfo() {
        var info = ComicInfoReader.parse(("<?xml version=\"1.0\"?><ComicInfo><Series>Paper Ronin</Series><Number>7</Number>"
                + "<LocalizedSeries>ペーパーローニン</LocalizedSeries><Manga>YesAndRightToLeft</Manga></ComicInfo>")
                .getBytes(StandardCharsets.UTF_8));
        assertNotNull(info);
        assertEquals("Paper Ronin", info.series());
        assertEquals(7.0, info.number());
        assertTrue(info.rightToLeft());
        assertNull(ComicInfoReader.parse("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///c:/windows/win.ini\">]><ComicInfo>&e;</ComicInfo>"
                .getBytes(StandardCharsets.UTF_8)), "DOCTYPE is refused");
    }

    @Test
    void pageCacheEvictsByBytes() {
        PageCache cache = new PageCache(3 * 100 * 100 * 4);
        cache.put("a", new WritableImage(100, 100));
        cache.put("b", new WritableImage(100, 100));
        cache.put("c", new WritableImage(100, 100));
        cache.get("a"); // a is now most recently used
        cache.put("d", new WritableImage(100, 100));
        assertTrue(cache.contains("a"));
        assertFalse(cache.contains("b"), "least recently used goes first");
        assertEquals(3 * 100 * 100 * 4, cache.bytes());
        cache.put("big", new WritableImage(200, 200));
        assertTrue(cache.bytes() <= 3 * 100 * 100 * 4 + 200 * 200 * 4);
    }
}
