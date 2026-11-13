Paste this into Claude Code as your first message (after running `claude` inside this folder):

---

Read CLAUDE.md, docs/DESIGN.md and docs/ARCHITECTURE.md fully, and open design/koma-concept-12.html plus the screenshots in design/screens to understand the look.

Then start **Phase 1 (Scaffold)** only:
1. Check that JDK 21 and Maven are installed and on PATH; tell me exactly what's missing and how to install it before continuing.
2. Create a Maven JavaFX project in this folder (package `app.koma`) following the layout in ARCHITECTURE.md, with `mvn javafx:run` working.
3. Undecorated window with the custom ink title bar (drag to move, minimize, maximize, close, resize edges).
4. Add `themes/light.css` and `themes/dark.css` with the tokens from DESIGN.md, load the four fonts from resources, and add a light/dark toggle that follows the Windows theme by default.
5. Build the shared nav bar and a placeholder Home with the "№07" title block, so I can check that fonts and colors match the concept.

When it runs, stop and tell me what to check. Don't start Phase 2 until I say so.
