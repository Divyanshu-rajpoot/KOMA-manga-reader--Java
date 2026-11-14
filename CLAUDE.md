# KOMA: offline manga & manhwa reader for Windows (Java 21 / JavaFX 21)

Read this first. Read `docs/DESIGN.md` before UI work and `docs/ARCHITECTURE.md` before backend work.

## What it is
A fast, offline desktop reader for **local CBZ files** (manga and manhwa).
- Each file is a **volume**; volumes are grouped into **series**.
- No accounts, no network, no telemetry. Everything stays on the user's PC.
- Handles **very large CBZ files (1–10+ GB, ZIP64)** without extracting them and without freezing the UI.
- Windows 10/11 x64 first.

## Commands
Use the Maven wrapper (JDK 21 on PATH or `JAVA_HOME`; no global Maven needed):

| Task | Command |
|---|---|
| Run | `mvnw javafx:run` |
| Test | `mvnw test` |
| 5 GB ZIP64 benchmark | `mvnw test -Phuge` |
| Portable app + release zip (`target/installer/KOMA/KOMA.exe`, `target/release/*.zip`) | `mvnw clean -Ppackage package` |
| MSI installer (needs WiX Toolset 3) | `mvnw -Ppackage package -Djpackage.type=msi "-Djpackage.extra=--win-menu --win-shortcut"` |

- `.mvn/jvm.config` makes Maven trust the Windows certificate store. This PC inspects HTTPS, and without it downloads fail with PKIX errors.
- Screenshot tour (never touches the real library): run `app.koma.Launcher` with
  `-Dkoma.home=<tmp> -Dkoma.devtour=<shots dir> -Dkoma.devimport=<folder of CBZs>` (see `dev/DevTour`).

## Design
- **Concept 12, "Ink Archive"**, with a light ("paper") and a dark ("ink") theme.
- Reference: `design/koma-concept-12.html` (open it in a browser and pick Concept 12) and `design/screens/`.
- Tokens, components and motion: `docs/DESIGN.md`, including the JavaFX translation table.
- **Never hard-code colors.** Use the looked-up colors from `themes/light.css` / `dark.css`. Canvas code asks `ThemeManager.color("ink")`.

## Rules
1. Never load a whole CBZ into memory: open the ZIP index, then read single entries on demand.
2. Never touch the disk or decode images on the JavaFX Application Thread. Use `Exec.IO` (virtual threads) or `Exec.DECODE` (fixed pool) and hand results back with `Exec.fx`.
3. One screen = one package under `ui/` with a Java view class (no FXML) and its own CSS file. Keep classes small.
4. Animations use the tokens in `core/Anim` and go through `Anim.play`, which respects "Reduce motion" (the Windows setting plus the in-app toggle).
5. Decoded images are cached by byte size (`PageCache`); the app runs with `-Xmx2g`.
6. Keep the archive reader, file-name parser, library store and decoder covered by JUnit 5 tests.
7. Ask before adding a dependency that isn't listed in `docs/ARCHITECTURE.md`.

## Status
All seven planned phases are implemented and run end to end: scaffold, archive engine, import + library,
reader, Home, Library/Series, Settings + packaging.

Known gaps:
- 5 GB benchmark: opening takes about 24 ms (target 300 ms) ✓. The first page takes about 220 ms (target 150 ms) ✗ on 6-megapixel, high-detail pages; the cost is mostly the JPEG decode. Page turns are served from the preload cache.
- CBR/RAR files are detected and flagged in the import queue ("repack as CBZ"); there is no conversion.

Decisions that differ from the original plan:
- Cover thumbnails are **JPEG, not WebP**: TwelveMonkeys can read WebP but can't write it.
- The 3D carousel uses a **per-card `PerspectiveTransform`** instead of a `SubScene`, so the cards get the theme CSS. The geometry is the same as in DESIGN.md.
- Watched folders never import on their own: new files wait on the Import screen.
- Reader full screen (`F`) is "immersive": the stage goes full screen, the frame disappears, and the bars become overlays that show near the screen edges.
- Image quality: pages are decoded at the exact display size (no size bucketing) and downscaled with 2x box halving plus an exact area average (`PageDecoder.resize`). Subsampling during decode is only used for 4x+ reductions (thumbnails).
- Thumbnail cache is versioned (`ThumbCache.VERSION`): bump it whenever decoding changes, and old thumbnails are purged at startup.
