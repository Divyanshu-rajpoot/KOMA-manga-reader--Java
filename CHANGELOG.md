# Changelog

## Unreleased

### Reader
- Zoom 100–400% in every mode: floating slider with − / + buttons (click the percentage to reset), `+` `-` `0` keys, Ctrl+wheel and trackpad pinch. Zoomed pages scroll and pan (drag, wheel, Shift+wheel in the long strip). The view resizes instantly and sharpens a moment later.

### Performance
- Measured with the new frame-time harness (`-Dkoma.devperf=<series>`, see CLAUDE.md), 400 synthetic series, before → after: Library open froze the UI 1.1–1.4 s → about 0.1 s; scrolling 8–12 → about 55–60 fps; switching screens 7–10 → about 46 fps with worst stall 3.8 s → 0.3 s. With 1,500 series the old build ran out of memory; the new one runs.
- Screen stylesheets are loaded once on the scene instead of per screen. JavaFX otherwise registers every styled node's parent against them with a linear scan that grew with every tile ever created (about half of UI-thread time while scrolling).
- Library tiles are recycled (re-bound) instead of rebuilt while scrolling, cached as bitmaps, and a cover is one third of the nodes it was (the kanji title is one label, not eight). Covers scrolled past are not decoded. The placeholder under a loaded cover is hidden.
- Home catalog and Details table are virtualized too.
- `library.json` is written on a background thread and coalesced (400 ms). It used to be serialized on the UI thread on every change: every few page turns while reading, and once per volume when importing.
- Page decoder: the area-average downscale no longer allocates a full-height float buffer (over 100 MB for tall pages, which triggered the GCLocker warnings).
- Animations: removed the endless dash march on the Import drop zone, the rise-in of queue rows, the 80 s rotation of the Home speed lines (now a cached bitmap) and the block-by-block progress fill. Screen changes fade only (no 10 px slide), after the screen is laid out. Remaining fades/slams are cached while they run and only the first 10 list items stagger. The carousel reuses its effects and does not auto-advance in a background window.
- Library and Series screens are virtualized: only the covers and rows near the viewport are built, so big libraries stay smooth (previously every tile, with its animation, was built at once).
- Screentone pattern is shared between covers instead of rebuilt per cover.
- Reader: thumbnail strip reuses its nodes, and the "cached pages" probe is bounded, so page turns do less work.

## 1.0.0 (2026-11-14)

First public release.

**Download:** `KOMA-1.0.0-windows-x64.zip`. Unzip it and run `KOMA\KOMA.exe`. Java is bundled; Windows 10/11 64-bit.

### Reading
- Two-page spread (right-to-left for manga), single page, and long strip for manhwa and webtoons.
- Full-screen immersive mode (`F`, F11 or double-click): the controls show only when the mouse reaches the screen edge.
- Pages are decoded at the exact display size with area-average downscaling, so line art stays sharp.
- Pages around the current one are preloaded, so page turns are instant.
- Progress is saved per volume; the last page marks a volume as read and continues to the next one.

### Library
- Opens multi-GB ZIP64 CBZ files in milliseconds without extracting them.
- Files are grouped into series automatically from names like `Title v03.cbz`, `Title Vol 3.cbz` and `Title c012.cbz`, or from `ComicInfo.xml`.
- Keep files where they are, or copy them into a `Series\Vol NN.cbz` library folder.
- Watched folders: new CBZ files wait on the Import screen.
- Rename series inline; removing a series asks for a second click and never deletes your files.

### Design
- "Ink Archive" light and dark themes, a 3D cover carousel, and a "Reduce motion" option that follows Windows.

### Known limitations
- CBR/RAR archives aren't supported (repack them as CBZ).
- The app isn't code-signed, so Windows SmartScreen shows a warning the first time it runs.
