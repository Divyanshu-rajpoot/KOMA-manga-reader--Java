package app.koma.library;

import app.koma.archive.CbzIndex;
import app.koma.core.Exec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.DoubleConsumer;
import java.util.stream.Stream;

/** Finds CBZ files, reads their index and metadata, and adds them to the library. */
public final class ImportService {
    private ImportService() {}

    public enum State { QUEUED, READING, READY, UNMATCHED, DUPLICATE, UNSUPPORTED, FAILED, ADDING, ADDED }

    /** One file in the import queue. Mutated on I/O threads during analysis, then only on the FX thread. */
    public static final class Item {
        public final Path path;
        public final String fileName;
        public long size;
        public long mtime;
        public volatile State state = State.QUEUED;
        public String series;
        public String titleJp;
        public double number;
        public Double numberEnd;
        public String kind = "volume";
        public String type = "manga";
        public String direction = "rtl";
        public int pages;
        public double indexMs;
        public String error;
        public double copyProgress;
        public boolean fromComicInfo;

        public Item(Path path) {
            this.path = path;
            this.fileName = path.getFileName().toString();
        }

        public boolean canAdd() {
            return (state == State.READY || state == State.UNMATCHED) && series != null && !series.isBlank();
        }
    }

    /** Expands folders (recursively) into archive files. CBR/RAR files are kept so the UI can flag them. Off the FX thread. */
    public static List<Path> expand(List<Path> inputs) {
        List<Path> out = new ArrayList<>();
        for (Path p : inputs) {
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.walk(p, 8)) {
                    s.filter(Files::isRegularFile).filter(ImportService::isArchive)
                            .sorted((a, b) -> app.koma.archive.NaturalSort.INSTANCE.compare(a.toString(), b.toString()))
                            .forEach(out::add);
                } catch (IOException e) {
                    System.err.println("KOMA: cannot scan " + p + ": " + e.getMessage());
                }
            } else if (Files.isRegularFile(p) && isArchive(p)) {
                out.add(p);
            }
        }
        return out;
    }

    public static boolean isArchive(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".cbz") || n.endsWith(".zip") || n.endsWith(".cbr") || n.endsWith(".rar");
    }

    /** Reads index, page count, ComicInfo.xml and parses the name. */
    public static CompletableFuture<Item> analyze(Item it, LibraryStore store, String defaultDirection) {
        return Exec.io(() -> {
            try {
                it.size = Files.size(it.path);
                it.mtime = Files.getLastModifiedTime(it.path).toMillis() / 1000;
            } catch (IOException e) {
                it.state = State.FAILED;
                it.error = e.getMessage();
                return it;
            }
            String lower = it.fileName.toLowerCase(Locale.ROOT);
            FileNameParser.Parsed parsed = FileNameParser.parse(it.fileName);
            it.series = parsed.matched() ? parsed.series() : null;
            it.number = parsed.number();
            it.numberEnd = parsed.numberEnd();
            it.kind = parsed.kind();
            if (lower.endsWith(".cbr") || lower.endsWith(".rar")) {
                it.state = State.UNSUPPORTED;
                it.error = "CBR/RAR isn't supported yet. Repack it as CBZ.";
                return it;
            }
            if (store.containsPath(it.path.toAbsolutePath().toString())) {
                it.state = State.DUPLICATE;
                return it;
            }
            it.state = State.READING;
            boolean webtoon = false;
            try (CbzIndex idx = CbzIndex.open(it.path)) {
                it.indexMs = idx.openMillis();
                it.pages = idx.pageCount();
                if (it.pages == 0) {
                    it.state = State.FAILED;
                    it.error = "No images in this archive";
                    return it;
                }
                ComicInfoReader.Info info = ComicInfoReader.parse(idx.readComicInfo());
                if (info != null) {
                    if (info.series() != null) {
                        it.series = info.series();
                        it.fromComicInfo = true;
                    }
                    if (info.number() != null) {
                        it.number = info.number();
                        it.numberEnd = null;
                    }
                    if (info.localizedSeries() != null) it.titleJp = info.localizedSeries();
                    webtoon = info.webtoon();
                }
            } catch (Exception e) {
                it.state = State.FAILED;
                it.error = e.getMessage();
                return it;
            }
            if (webtoon || "chapter".equals(it.kind)) {
                it.type = "manhwa";
                it.direction = "ttb";
            } else {
                it.direction = defaultDirection;
            }
            // An existing series keeps its own type/direction.
            if (it.series != null) {
                Series existing = store.findSeriesByTitle(it.series);
                if (existing != null) {
                    it.series = existing.title;
                    it.type = existing.type;
                    it.direction = existing.direction;
                }
            }
            it.state = it.series == null ? State.UNMATCHED : State.READY;
            return it;
        });
    }

    /**
     * Copies the file into the library folder if requested and returns the volume to add.
     * Runs on an I/O thread; the caller adds the volume to the store on the FX thread.
     */
    public static CompletableFuture<Volume> prepare(Item it, boolean copy, Path libraryFolder, DoubleConsumer progress) {
        return Exec.io(() -> {
            try {
                Path target = it.path.toAbsolutePath();
                if (copy) {
                    Path dir = libraryFolder.resolve(safeName(it.series));
                    Files.createDirectories(dir);
                    String label = "chapter".equals(it.kind) ? "Ch " : "Vol ";
                    String num = Volume.num(it.number) + (it.numberEnd != null ? "-" + Volume.num(it.numberEnd) : "");
                    target = uniquePath(dir.resolve(label + num + ".cbz"));
                    copyWithProgress(it.path, target, it.size, progress);
                }
                Volume v = new Volume();
                v.number = it.number;
                v.numberEnd = it.numberEnd;
                v.kind = it.kind;
                v.path = target.toString();
                v.storage = copy ? "copied" : "linked";
                v.size = it.size;
                v.mtime = copy ? Files.getLastModifiedTime(target).toMillis() / 1000 : it.mtime;
                v.pages = it.pages;
                return v;
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    private static void copyWithProgress(Path src, Path dst, long total, DoubleConsumer progress) throws IOException {
        Path tmp = dst.resolveSibling(dst.getFileName() + ".part");
        try (InputStream in = Files.newInputStream(src); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[1 << 20];
            long done = 0;
            long lastReport = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (done - lastReport > (16 << 20)) {
                    lastReport = done;
                    progress.accept(total > 0 ? done / (double) total : 0);
                }
            }
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
        progress.accept(1);
    }

    static String safeName(String s) {
        String n = s.replaceAll("[\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").trim();
        return n.isEmpty() ? "Untitled" : n;
    }

    private static Path uniquePath(Path p) {
        if (!Files.exists(p)) return p;
        String name = p.getFileName().toString();
        String base = name.substring(0, name.lastIndexOf('.'));
        for (int i = 2; ; i++) {
            Path c = p.resolveSibling(base + " (" + i + ").cbz");
            if (!Files.exists(c)) return c;
        }
    }
}
