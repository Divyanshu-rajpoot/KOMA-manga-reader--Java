# KOMA — offline manga & manhwa reader for Windows (Java / JavaFX)

Read this file first, every session. Then read `docs/DESIGN.md` and `docs/ARCHITECTURE.md` before writing UI or backend code.

## What we are building
A fast, offline desktop reader for **locally imported CBZ files** (manga and manhwa).
- The user imports CBZ files (or a folder of them). **Each file is a volume**; volumes are grouped into **series**.
- No accounts, no internet, no online sources, no telemetry. Everything stays on the user's PC.
- Must handle **very large CBZ files (1–10+ GB, ZIP64)** without extracting and without freezing the UI.
- Windows first (Windows 10/11, x64). Other platforms later.

## Design
- The chosen design is **Concept 12, "Ink Archive"**, with a **light ("paper") and dark ("ink") theme**.
- Visual reference: `design/koma-concept-12.html`. Open it in a browser. Pick **Concept 12 · Ink Archive**; Concepts 2, 9 and 4 are history only.
- Screenshots: `design/screens/` (`light-*.png`, `dark-*.png`).
- Tokens, components, motion: `docs/DESIGN.md`, including the **JavaFX translation table** at the end. **Never hard-code colors; use the looked-up colors from the theme stylesheets.**
- The covers in the concept are generated placeholders. The real app shows the first image of each CBZ.

## Stack (decided)
- **Java 21 (LTS)** + **JavaFX 21+**, built with **Maven**
- Styling with JavaFX CSS (two theme stylesheets); custom controls in Java for the ink look
- `java.util.zip.ZipFile` for archives (handles ZIP64), Jackson for JSON, TwelveMonkeys for WebP
- Packaged with **jlink + jpackage** into a Windows installer
- Details: `docs/ARCHITECTURE.md`

## Rules for working on this repo
1. Work in the phases below, one at a time. Finish a phase, run it, show me, then stop and wait for approval.
2. Never load a whole CBZ into memory. Open the ZIP index, then read single entries on demand.
3. Never touch the disk or decode images on the JavaFX Application Thread. Use an executor (virtual threads for I/O, a fixed pool for decoding) and `Platform.runLater` for results.
4. One screen = one package under `ui/` with its own FXML-free Java view class and a CSS file. Keep classes small.
5. Animations follow `docs/DESIGN.md` → Motion and must respect "Reduce motion" (OS setting plus in-app toggle).
6. Cache decoded images by byte size (not count) and cap heap use; set `-Xmx` in the launcher.
7. Unit-test the archive reader, file-name parser and library store with JUnit 5. Test with a generated multi-GB CBZ (see ARCHITECTURE → Testing).
8. Ask me before adding any dependency that isn't listed in ARCHITECTURE.md.

## Phases
1. **Scaffold**: Maven + JavaFX app runs; undecorated window with custom ink title bar; light/dark theme stylesheets and fonts loaded; theme toggle works.
2. **Archive engine**: open CBZ/ZIP64, list image entries in natural order, read one page by index, decode and resize off-thread. Benchmark: open a 5 GB CBZ in under 300 ms.
3. **Import + library**: import files or folders, file-name parser, "keep in place" or "copy to library folder", library JSON in `%APPDATA%\Koma`, WebP thumbnails in `%LOCALAPPDATA%\Koma\thumbs`.
4. **Reader**: spread (RTL), single, long-strip modes; page cache and preloading; thumbnail strip; shortcuts; remembers progress.
5. **Home**: 3D cover carousel driving the info column, reading card, up next, catalog table.
6. **Library and series screens**: cover/details views, filters, sort, volume grid with progress.
7. **Settings and polish**: settings, reduce motion, folder watcher (`WatchService`), jlink + jpackage installer.

## Commands (fill in once scaffolded)
- Run: `mvn javafx:run`
- Test: `mvn test`
- Package: `mvn -Ppackage package` (jlink + jpackage)
