package app.koma.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * An open CBZ. Opening reads only the ZIP central directory (ZIP64 aware), never page data,
 * so a multi-GB file opens in milliseconds. Pages are read one entry at a time on demand.
 * Thread-safe: {@link ZipFile} synchronises entry access internally.
 */
public final class CbzIndex implements AutoCloseable {

    /** One page: archive entry name, uncompressed size, position in reading order. */
    public record PageRef(String name, long size, int index) {}

    private final Path path;
    private final ZipFile zip;
    private final List<ZipEntry> entries;
    private final List<PageRef> pages;
    private final ZipEntry comicInfo;
    private final long openNanos;

    private CbzIndex(Path path, ZipFile zip, List<ZipEntry> entries, ZipEntry comicInfo, long openNanos) {
        this.path = path;
        this.zip = zip;
        this.entries = entries;
        this.comicInfo = comicInfo;
        this.openNanos = openNanos;
        List<PageRef> refs = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            ZipEntry e = entries.get(i);
            refs.add(new PageRef(e.getName(), e.getSize(), i));
        }
        this.pages = Collections.unmodifiableList(refs);
    }

    public static CbzIndex open(Path path) throws IOException {
        long t0 = System.nanoTime();
        try {
            return build(path, new ZipFile(path.toFile(), StandardCharsets.UTF_8), t0);
        } catch (IllegalArgumentException | ZipException e) {
            // Entry names that are not valid UTF-8 and lack the UTF-8 flag: retry with the legacy DOS code page.
            try {
                return build(path, new ZipFile(path.toFile(), Charset.forName("Cp437")), t0);
            } catch (IllegalArgumentException | ZipException e2) {
                throw new ZipException("Not a readable CBZ/ZIP: " + path.getFileName() + " (" + e.getMessage() + ")");
            }
        }
    }

    private static CbzIndex build(Path path, ZipFile zip, long t0) throws IOException {
        try {
            List<ZipEntry> imgs = new ArrayList<>();
            ZipEntry info = null;
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (ImageSniffer.isPageName(name)) {
                    imgs.add(e);
                } else if (name.toLowerCase(Locale.ROOT).endsWith("comicinfo.xml")) {
                    if (info == null || name.length() < info.getName().length()) info = e;
                }
            }
            imgs.sort(Comparator.comparing(ZipEntry::getName, NaturalSort.INSTANCE));
            return new CbzIndex(path, zip, imgs, info, System.nanoTime() - t0);
        } catch (IllegalArgumentException e) {
            zip.close();
            throw e;
        } catch (RuntimeException e) {
            zip.close();
            throw new IOException("Not a readable CBZ: " + path.getFileName(), e);
        }
    }

    public Path path() {
        return path;
    }

    public List<PageRef> pages() {
        return pages;
    }

    public int pageCount() {
        return pages.size();
    }

    public double openMillis() {
        return openNanos / 1_000_000.0;
    }

    /** Reads one page's bytes. Only that entry is touched. */
    public byte[] readPage(int index) throws IOException {
        ZipEntry e = entries.get(index);
        try (InputStream in = zip.getInputStream(e)) {
            return in.readAllBytes();
        }
    }

    /** Stream for one page, for header-only reads (dimensions). */
    public InputStream openPage(int index) throws IOException {
        return zip.getInputStream(entries.get(index));
    }

    /** ComicInfo.xml bytes, or null. */
    public byte[] readComicInfo() throws IOException {
        if (comicInfo == null) return null;
        try (InputStream in = zip.getInputStream(comicInfo)) {
            return in.readNBytes(1 << 20);
        }
    }

    @Override
    public void close() {
        try {
            zip.close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }
}
