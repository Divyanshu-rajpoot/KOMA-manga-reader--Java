# Changelog

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
