package app.koma.library;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Watches folders (and their subfolders, two levels deep) for new CBZ/ZIP files with {@link WatchService}.
 * New files are reported once their size has stopped changing, so half-copied files are not picked up.
 * Callbacks run on the watcher thread.
 */
public final class FolderWatcher implements AutoCloseable {
    private final WatchService ws;
    private final Map<WatchKey, Path> keys = new java.util.concurrent.ConcurrentHashMap<>();
    private final Consumer<List<Path>> onNew;
    private final Thread thread;

    public FolderWatcher(List<Path> folders, Consumer<List<Path>> onNew) throws IOException {
        this.ws = FileSystems.getDefault().newWatchService();
        this.onNew = onNew;
        for (Path f : folders) registerTree(f);
        thread = Thread.ofVirtual().name("koma-watch").start(this::loop);
    }

    private void registerTree(Path root) {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> s = Files.walk(root, 2)) {
            s.filter(Files::isDirectory).forEach(this::register);
        } catch (IOException e) {
            System.err.println("KOMA: cannot watch " + root + ": " + e.getMessage());
        }
    }

    private void register(Path dir) {
        try {
            keys.put(dir.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY), dir);
        } catch (IOException e) {
            System.err.println("KOMA: cannot watch " + dir + ": " + e.getMessage());
        }
    }

    private void loop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = ws.take();
                Set<Path> found = new LinkedHashSet<>();
                collect(key, found);
                // gather a burst of events (a folder copy) before reporting
                WatchKey more;
                while ((more = ws.poll(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) != null) collect(more, found);
                List<Path> ready = new ArrayList<>();
                for (Path p : found) if (stable(p)) ready.add(p);
                if (!ready.isEmpty()) onNew.accept(ready);
            }
        } catch (InterruptedException | ClosedWatchServiceException e) {
            // closed
        }
    }

    private void collect(WatchKey key, Set<Path> found) {
        Path dir = keys.get(key);
        for (WatchEvent<?> ev : key.pollEvents()) {
            if (ev.kind() == StandardWatchEventKinds.OVERFLOW || dir == null) continue;
            Path p = dir.resolve((Path) ev.context());
            if (Files.isDirectory(p) && ev.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                registerTree(p);
                found.addAll(ImportService.expand(List.of(p)));
            } else if (ImportService.isArchive(p)) {
                found.add(p);
            }
        }
        key.reset();
    }

    /** True when the file exists and its size is unchanged for a moment (copy finished). */
    private static boolean stable(Path p) {
        try {
            for (int i = 0; i < 20; i++) {
                if (!Files.isRegularFile(p)) return false;
                long a = Files.size(p);
                Thread.sleep(500);
                if (Files.size(p) == a && a > 0) return true;
            }
        } catch (IOException | InterruptedException e) {
            return false;
        }
        return false;
    }

    @Override
    public void close() {
        thread.interrupt();
        try {
            ws.close();
        } catch (IOException ignored) {
            // closing
        }
    }
}
