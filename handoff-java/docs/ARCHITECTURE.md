# KOMA architecture (Java / JavaFX)

## Why Java + JavaFX
- The developer already works in Java (Spring Boot), so the whole app is one language and one toolchain.
- JavaFX gives hardware-accelerated 2D/3D scene graph, `Timeline` animation, CSS theming and `PerspectiveCamera`, which covers the 3D cover carousel and the ink animations.
- `java.util.zip.ZipFile` reads the ZIP central directory only and supports ZIP64, so multi-GB CBZs open fast.
- Trade-off versus Tauri: a jlink-trimmed runtime makes the installer roughly 50–70 MB (Tauri: ~3–10 MB) and idle RAM around 150–250 MB (Tauri: ~40–80 MB). Fine for a desktop reader; set expectations accordingly.
- JavaFX has no WebP and no `clip-path`/`box-shadow`; handled with TwelveMonkeys and custom nodes (see DESIGN.md).

### Prerequisites (Windows)
- JDK 21 (Temurin) with `JAVA_HOME` set
- Maven 3.9+
- WiX Toolset 3 or 4 (needed by `jpackage` to build an .msi; .exe installers need Inno Setup instead)

## Folder layout
```
koma/
  CLAUDE.md, pom.xml
  docs/  design/
  src/main/java/app/koma/
    App.java, Launcher.java       (Launcher avoids the JavaFX main-class quirk in fat jars)
    core/        ThemeManager, MotionPrefs, Anim, Executors, Paths, KeyboardShortcuts
    ui/
      shell/     TitleBar, NavBar, Router
      shared/    CoverView, SegmentProgress, InkButton, Chip, Stamp, CatalogTable, Halftone
      home/      CoverCarousel (SubScene+PerspectiveCamera), InfoColumn, ReadingCard, UpNext
      library/   series/   reader/ (SpreadView, SingleView, StripView, ThumbStrip, Scrubber)
      importer/  settings/
    archive/     CbzIndex (ZipFile wrapper), EntryReader, NaturalSort, ImageSniffer
    library/     Series, Volume, LibraryStore (Jackson), FileNameParser, ComicInfoReader
    imaging/     PageDecoder, Resizer, PageCache (byte-weighted LRU), Preloader, ThumbCache
  src/main/resources/
    themes/light.css, dark.css, base.css
    fonts/       Anton, Archivo, IBM Plex Mono, Noto Sans JP (.ttf)
  src/test/java/ ...
```

## Large-CBZ strategy (the core requirement)
1. **Open = index only.** `ZipFile` seeks to the End of Central Directory (+ ZIP64 records) and reads the central directory, never the data. Build a `List<PageRef>` (name, size, order). Target: 5 GB file opens in under 300 ms. Keep one open `ZipFile` per open volume; close it when the volume is closed.
2. **Pages on demand.** `zipFile.getInputStream(entry)` for the single entry; for stored images this is one seek plus a read. Never extract to disk.
3. **Order** pages by natural sort of the entry name. Skip non-images, directories and `__MACOSX`.
4. **Decode off the FX thread.** Read bytes, then `ImageIO` (JPEG/PNG/GIF/BMP; WebP via TwelveMonkeys `imageio-webp`) to `BufferedImage`, resize to the on-screen pixel width (progressive downscale or Lanczos via `imgscalr`/own kernel), convert to JavaFX `WritableImage`, then hand to the UI with `Platform.runLater`. For JPEG use `ImageReadParam.setSourceSubsampling` when the target is much smaller than the source.
5. **Caching**
   - RAM: byte-weighted LRU of resized pages (default 1 GB; account `w*h*4` bytes each). Pre-set `-Xmx` accordingly (e.g. 2g).
   - Preload +6 / −2 pages around the current one on a fixed thread pool; cancel stale tasks when the user jumps.
   - Disk: WebP thumbnails in `%LOCALAPPDATA%\Koma\thumbs`, keyed by hash of path+size+mtime.
6. **Long strip (manhwa)**: read each image's width/height from its header only (`ImageReader.getWidth/getHeight` without decoding) so layout is known up front. Virtualize: only `ImageView`s for visible slices + a buffer exist; others are placeholders of the right height. Open the next chapter's index before the current one ends.
7. **CBR (RAR)**: solid RAR can't be read page by page. Offer "Convert to CBZ" in v1 instead of native support.
8. **Memory safety**: never hold more than the cache budget; free `BufferedImage`s right after conversion; avoid `Image(url)` with huge sources, and always pass target size.

## Library model (no database; JSON files)
`%APPDATA%\Koma\library.json` (writes are atomic: write a temp file, then `Files.move(..., ATOMIC_MOVE)`):
```json
{
  "version": 1,
  "series": [{
    "id": "paper-ronin",
    "title": "Paper Ronin",
    "titleJp": "ペーパーローニン",
    "type": "manga",
    "direction": "rtl",
    "volumes": [{
      "id": "uuid",
      "number": 7,
      "path": "D:\\Manga\\Paper Ronin\\Paper Ronin v07.cbz",
      "storage": "linked",
      "size": 1395864371,
      "mtime": 1731300000,
      "pages": 208,
      "progress": { "page": 142, "updatedAt": "2026-11-11T18:00:00Z" },
      "sessions": [{ "date": "2026-11-11", "from": 104, "to": 142 }]
    }]
  }]
}
```
- `storage` is `"linked"` (file stays where it is) or `"copied"` (copied into the KOMA library folder as `Series\Vol NN.cbz`).
- `settings.json` sits next to it: theme, accent, reduce motion, cache size, preload range, default direction.

## File-name parser
Try in order; the first match wins:
1. `ComicInfo.xml` inside the archive (`Series`, `Number`, `Manga=YesAndRightToLeft`)
2. `{Series} v{NN}` / `{Series} Vol. {N}` / `{Series} Volume {N}`
3. `{Series} {NN}-{NN}` (omnibus, covering a range of volumes)
4. `{Series} c{NNN}` / `Ch {N}` (chapter files, mainly manhwa)
5. Otherwise "unmatched": the user picks the series in the import queue.

Strip tags like `[Group]`, `(Digital)`, `(2019)` before matching. Unit-test the parser with at least 30 real-world-style names.

## Testing
- A JUnit test helper generates a CBZ with N stored JPEGs (e.g. 2,000 x 3 MB = about 6 GB, which forces ZIP64) in a temp folder (`@TempDir`).
- Benchmarks to track (JMH or simple timers): open time, first-page time, page-turn time with a warm cache, heap after reading 500 pages.
- Targets: open under 300 ms; first page shown under 150 ms; cached page turn under 16 ms; heap stays under cache size + 150 MB.
- UI smoke tests with TestFX are optional; ask before adding.

## Allowed dependencies (ask before adding others)
- `org.openjfx:javafx-controls`, `javafx-graphics`, `javafx-fxml` is NOT needed (views are built in Java)
- `com.fasterxml.jackson.core:jackson-databind`
- `com.twelvemonkeys.imageio:imageio-webp` (and `imageio-jpeg` for CMYK/odd JPEGs)
- `org.imgscalr:imgscalr-lib` (or a small custom Lanczos resizer)
- `org.junit.jupiter:junit-jupiter`
- Build plugins: `javafx-maven-plugin`, `maven-shade-plugin` or `jlink`/`jpackage` via `org.panteleyev:jpackage-maven-plugin`
- No Spring, no database.
