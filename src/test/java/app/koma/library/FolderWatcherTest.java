package app.koma.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FolderWatcherTest {
    @TempDir
    Path dir;

    @Test
    void reportsNewArchivesOnly() throws Exception {
        List<Path> seen = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        try (FolderWatcher w = new FolderWatcher(List.of(dir), found -> {
            seen.addAll(found);
            latch.countDown();
        })) {
            Files.writeString(dir.resolve("notes.txt"), "ignore me");
            Files.write(dir.resolve("New Series v01.cbz"), new byte[]{1, 2, 3});
            assertTrue(latch.await(15, TimeUnit.SECONDS), "watcher should report the new CBZ");
        }
        assertEquals(1, seen.size());
        assertEquals("New Series v01.cbz", seen.get(0).getFileName().toString());
    }
}
