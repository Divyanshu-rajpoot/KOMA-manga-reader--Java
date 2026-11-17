package app.koma.library;

import app.koma.core.Exec;
import app.koma.core.Json;

import java.io.IOException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The library (library.json). Mutate from one thread (the FX thread in the app); writes are
 * coalesced, serialised and flushed to disk atomically on an I/O thread, newest snapshot wins.
 */
public final class LibraryStore {
    private final Path file;
    private final Library lib;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<byte[]> pending = new AtomicReference<>();
    private final Object writeLock = new Object();

    private LibraryStore(Path file, Library lib) {
        this.file = file;
        this.lib = lib;
        if (lib.series == null) lib.series = new ArrayList<>();
        for (Series s : lib.series) if (s.volumes == null) s.volumes = new ArrayList<>();
    }

    public static LibraryStore load(Path file) {
        return new LibraryStore(file, Json.read(file, Library.class, Library::new));
    }

    public Path file() {
        return file;
    }

    // ---------- queries ----------

    public List<Series> series() {
        return Collections.unmodifiableList(lib.series);
    }

    public Series findSeries(String id) {
        for (Series s : lib.series) if (s.id.equals(id)) return s;
        return null;
    }

    public Series findSeriesByTitle(String title) {
        String slug = slug(title);
        for (Series s : lib.series) {
            if (s.id.equals(slug) || s.title.equalsIgnoreCase(title.trim())) return s;
        }
        return null;
    }

    public Volume findVolume(String id) {
        for (Series s : lib.series) for (Volume v : s.volumes) if (v.id.equals(id)) return v;
        return null;
    }

    public Series seriesOf(Volume v) {
        for (Series s : lib.series) if (s.volumes.contains(v)) return s;
        return null;
    }

    public boolean containsPath(String path) {
        for (Series s : lib.series) for (Volume v : s.volumes) if (v.path != null && v.path.equalsIgnoreCase(path)) return true;
        return false;
    }

    public int volumeCount() {
        return lib.series.stream().mapToInt(s -> s.volumes.size()).sum();
    }

    public long totalSize() {
        return lib.series.stream().mapToLong(Series::totalSize).sum();
    }

    // ---------- mutations ----------

    /** Adds a volume, creating the series if needed. */
    public Volume addVolume(String seriesTitle, String titleJp, String type, String direction, Volume v) {
        Series s = findSeriesByTitle(seriesTitle);
        if (s == null) {
            s = new Series();
            s.id = uniqueId(slug(seriesTitle));
            s.title = seriesTitle.trim();
            s.type = type;
            s.direction = direction;
            s.addedAt = Instant.now().toString();
            lib.series.add(s);
        }
        if (s.titleJp == null && titleJp != null) s.titleJp = titleJp;
        if (v.addedAt == null) v.addedAt = Instant.now().toString();
        s.volumes.add(v);
        changed();
        return v;
    }

    /** Records the current page. Marks the volume finished on its last page. */
    public void updateProgress(Volume v, int page) {
        boolean finished = v.pages > 0 && page >= v.pages - 1;
        boolean wasFinished = v.isFinished();
        v.progress = new Volume.Progress(page, Instant.now().toString(), finished || (wasFinished && page >= v.pages - 1));
        changed();
    }

    /** Adds a reading session (pages are 0-based internally, stored 1-based like the UI). */
    public void addSession(Volume v, int fromPage, int toPage) {
        if (toPage == fromPage) return;
        v.sessions.add(0, new Volume.Session(LocalDate.now().toString(), fromPage + 1, toPage + 1));
        if (v.sessions.size() > 50) v.sessions.subList(50, v.sessions.size()).clear();
        changed();
    }

    public void setFinished(Volume v, boolean finished) {
        v.progress = finished ? new Volume.Progress(Math.max(0, v.pages - 1), Instant.now().toString(), true) : null;
        changed();
    }

    public void setSeriesMeta(Series s, String title, String type, String direction) {
        if (title != null && !title.isBlank()) s.title = title.trim();
        if (type != null) s.type = type;
        if (direction != null) s.direction = direction;
        changed();
    }

    public void removeVolume(Volume v) {
        Series s = seriesOf(v);
        if (s == null) return;
        s.volumes.remove(v);
        if (s.volumes.isEmpty()) lib.series.remove(s);
        changed();
    }

    public void removeSeries(Series s) {
        lib.series.remove(s);
        changed();
    }

    public void onChange(Runnable r) {
        listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    private void changed() {
        save();
        for (Runnable r : listeners) r.run();
    }

    private final java.util.concurrent.atomic.AtomicBoolean writeQueued = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Schedules a write. Changes within 400 ms share one write, and the JSON is built on an I/O thread:
     * serializing a big library on every change (every few page turns, every import) used to stall the UI.
     */
    public void save() {
        if (!writeQueued.compareAndSet(false, true)) return;
        Exec.io(() -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            writeQueued.set(false);
            // the FX thread may mutate the lists mid-serialization (ConcurrentModificationException): retry, it is rare
            for (int attempt = 0; attempt < 5; attempt++) {
                try {
                    synchronized (writeLock) {
                        pending.set(Json.bytes(lib));
                        flush();
                    }
                    return;
                } catch (RuntimeException e) {
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            save(); // still racing with edits: queue another pass
        });
    }

    /** Blocking write of the latest snapshot (shutdown, tests). */
    public void flush() {
        synchronized (writeLock) {
            byte[] data = pending.getAndSet(null);
            if (data == null) return;
            try {
                Json.writeAtomic(file, data);
            } catch (IOException e) {
                System.err.println("KOMA: could not save library: " + e.getMessage());
            }
        }
    }

    public void saveNow() {
        synchronized (writeLock) {
            pending.set(Json.bytes(lib));
            flush();
        }
    }

    // ---------- ids ----------

    public static String slug(String title) {
        // strip Latin accents (é → e) but keep kana voicing marks (ペ stays ペ)
        String n = Normalizer.normalize(Objects.requireNonNullElse(title, ""), Normalizer.Form.NFKD)
                .replaceAll("[\\u0300-\\u036F]", "");
        n = Normalizer.normalize(n, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return n.isEmpty() ? "series" : n;
    }

    private String uniqueId(String base) {
        String id = base;
        int i = 2;
        while (findSeries(id) != null) id = base + "-" + i++;
        return id;
    }
}
