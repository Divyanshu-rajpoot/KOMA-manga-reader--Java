package app.koma.ui.settings;

import app.koma.AppContext;
import app.koma.core.Exec;
import app.koma.core.MotionPrefs;
import app.koma.core.Settings;
import app.koma.ui.shared.Css;
import app.koma.ui.shared.Fmt;
import app.koma.ui.shell.View;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/** Settings: reading, appearance, performance, library. Every change saves immediately. */
public final class SettingsView implements View {
    private final AppContext ctx;
    private final Settings st;
    private final ScrollPane scroll = new ScrollPane();
    private final Label thumbSize = new Label("…");

    public SettingsView(AppContext ctx) {
        this.ctx = ctx;
        this.st = ctx.settings;
        VBox page = new VBox(26);
        page.setPadding(new Insets(4, 36, 40, 36));
        page.setMaxWidth(980);
        Css.add(page, SettingsView.class, "settings");

        Label title = new Label("SETTINGS");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Stored in " + ctx.store.file().getParent() + ". Nothing leaves this PC.");
        sub.getStyleClass().add("mono-s");
        sub.setWrapText(true);

        page.getChildren().addAll(new VBox(0, title, sub), reading(), appearance(), performance(), library());
        scroll.setContent(page);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        refreshThumbSize();
    }

    private Node reading() {
        return section("READING",
                row("Default direction for manga", "Manhwa and chapter files default to long strip.",
                        choice(List.of("rtl", "ltr"), List.of("Right to left", "Left to right"), st.defaultDirection, v -> {
                            st.defaultDirection = v;
                            save();
                        })),
                row("Paged layout", "Used when a series isn't set to long strip.",
                        choice(List.of("single", "spread"), List.of("Single", "Spread"), st.readerMode, v -> {
                            st.readerMode = v;
                            save();
                        })),
                row("Page-turn animation", "A short slide and fade when turning pages.",
                        check(st.pageTurnAnimation, v -> {
                            st.pageTurnAnimation = v;
                            save();
                        })));
    }

    private Node appearance() {
        String sys = "System";
        return section("APPEARANCE",
                row("Theme", "System follows the Windows app theme.",
                        choice(List.of("system", "light", "dark"), List.of(sys, "Paper (light)", "Ink (dark)"), st.theme, v -> {
                            ctx.theme.setTheme(v);
                            ctx.window.nav().updateThemeIcon();
                        })),
                row("Accent", "One accent only; everything else is ink and paper.",
                        choice(List.of("red", "orange"), List.of("Ink red", "Archive orange"), st.accent, v -> {
                            ctx.theme.setAccent(v);
                            ctx.router.refresh();
                        })),
                row("Reduce motion", MotionPrefs.systemReduced()
                                ? "Windows animations are off, so motion is already reduced."
                                : "Stops the carousel, bursts and marching ants; transitions become instant.",
                        check(st.reduceMotion, v -> {
                            st.reduceMotion = v;
                            MotionPrefs.setApp(v);
                            save();
                        })));
    }

    private Node performance() {
        ComboBox<String> cache = new ComboBox<>();
        List<Integer> sizes = List.of(256, 512, 1024, 2048, 3072);
        for (int s : sizes) cache.getItems().add(s >= 1024 ? (s / 1024) + " GB" : s + " MB");
        int idx = sizes.indexOf(st.cacheMb);
        cache.getSelectionModel().select(idx < 0 ? 2 : idx);
        cache.setOnAction(e -> {
            st.cacheMb = sizes.get(cache.getSelectionModel().getSelectedIndex());
            ctx.pages.setBudget((long) st.cacheMb << 20);
            save();
        });
        Spinner<Integer> ahead = new Spinner<>(1, 20, st.preloadAhead);
        ahead.setPrefWidth(90);
        ahead.valueProperty().addListener((o, a, b) -> {
            st.preloadAhead = b;
            save();
        });
        Spinner<Integer> behind = new Spinner<>(0, 10, st.preloadBehind);
        behind.setPrefWidth(90);
        behind.valueProperty().addListener((o, a, b) -> {
            st.preloadBehind = b;
            save();
        });
        Label aheadL = new Label("ahead");
        aheadL.getStyleClass().add("muted");
        Label behindL = new Label("behind");
        behindL.getStyleClass().add("muted");
        HBox preload = new HBox(8, ahead, aheadL, behind, behindL);
        preload.setAlignment(Pos.CENTER_LEFT);

        Button clear = new Button("Clear");
        clear.getStyleClass().addAll("outline-button", "small-button");
        clear.setOnAction(e -> Exec.io(ctx.thumbs::clear).thenRun(() -> Exec.fx(this::refreshThumbSize)));
        thumbSize.getStyleClass().add("mono");
        HBox thumbs = new HBox(10, thumbSize, clear);
        thumbs.setAlignment(Pos.CENTER_LEFT);

        return section("PERFORMANCE",
                row("Page cache", "Decoded pages kept in memory (counted by bytes). The app runs with -Xmx2g.", cache),
                row("Preload", "Pages decoded around the current one.", preload),
                row("Thumbnail cache", "Cover images in %LOCALAPPDATA%\\Koma\\thumbs. They're rebuilt when needed.", thumbs));
    }

    private Node library() {
        Label folder = path(st.libraryFolder);
        Button change = new Button("Change");
        change.getStyleClass().addAll("outline-button", "small-button");
        change.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("KOMA library folder");
            File cur = new File(st.libraryFolder);
            if (cur.isDirectory()) dc.setInitialDirectory(cur);
            File f = dc.showDialog(ctx.stage);
            if (f != null) {
                st.libraryFolder = f.getAbsolutePath();
                folder.setText(st.libraryFolder);
                folder.setTooltip(new javafx.scene.control.Tooltip(st.libraryFolder));
                save();
            }
        });
        HBox lib = new HBox(10, folder, change);
        lib.setAlignment(Pos.CENTER_LEFT);
        return section("LIBRARY",
                row("Import default", "Keep files where they are, or copy them into the library folder.",
                        choice(List.of("linked", "copied"), List.of("Keep in place", "Copy"), st.importStorage, v -> {
                            st.importStorage = v;
                            save();
                        })),
                row("Library folder", "Where \"Copy into KOMA library\" puts files: Series\\Vol NN.cbz", lib),
                row("Watched folders", "New CBZ files that appear here wait on the Import screen.", watchList()),
                row("Library file", "Your series, volumes and progress.", path(ctx.store.file().toString())));
    }

    private Node watchList() {
        VBox box = new VBox(6);
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            box.getChildren().clear();
            for (String f : List.copyOf(st.watchFolders)) {
                Button rm = new Button("Remove");
                rm.getStyleClass().addAll("ghost-button", "small-button");
                rm.setOnAction(e -> {
                    st.watchFolders.remove(f);
                    save();
                    ctx.watched.restart();
                    rebuild[0].run();
                });
                HBox r = new HBox(8, path(f), rm);
                r.setAlignment(Pos.CENTER_LEFT);
                box.getChildren().add(r);
            }
            Button add = new Button("Add folder");
            add.getStyleClass().addAll("outline-button", "small-button");
            add.setOnAction(e -> {
                DirectoryChooser dc = new DirectoryChooser();
                dc.setTitle("Watch a folder for new CBZ files");
                File f = dc.showDialog(ctx.stage);
                if (f != null && !st.watchFolders.contains(f.getAbsolutePath())) {
                    st.watchFolders.add(f.getAbsolutePath());
                    save();
                    ctx.watched.restart();
                    rebuild[0].run();
                }
            });
            box.getChildren().add(add);
        };
        rebuild[0].run();
        return box;
    }

    private void refreshThumbSize() {
        Exec.io(ctx.thumbs::diskBytes).thenAccept(b -> Exec.fx(() -> thumbSize.setText(Fmt.bytes(b))));
    }

    private void save() {
        st.save();
    }

    /** A file path: shows the end of long paths ("…\\KOMA Library") with the full path as a tooltip. */
    private static Label path(String p) {
        Label l = mono(p);
        l.setTextOverrun(javafx.scene.control.OverrunStyle.LEADING_ELLIPSIS);
        // fixed width: a Label's preferred width is its full text, which would push the page wider
        l.setPrefWidth(340);
        l.setMaxWidth(340);
        l.setMinWidth(80);
        l.setAlignment(Pos.CENTER_RIGHT);
        l.setTooltip(new javafx.scene.control.Tooltip(p));
        return l;
    }

    private static Label mono(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("mono");
        return l;
    }

    private static Node section(String name, Node... rows) {
        Label h = new Label(name);
        h.getStyleClass().add("display-s");
        Region rule = new Region();
        rule.getStyleClass().add("rule");
        VBox box = new VBox(0);
        box.getChildren().addAll(h, rule);
        VBox.setMargin(rule, new Insets(6, 0, 4, 0));
        box.getChildren().addAll(rows);
        return box;
    }

    private static Node row(String label, String help, Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("strong");
        Label h = new Label(help);
        h.getStyleClass().add("small");
        h.getStyleClass().add("muted");
        h.setWrapText(true);
        VBox text = new VBox(2, l, h);
        text.setPrefWidth(420);
        text.setMinWidth(280);
        HBox.setHgrow(text, Priority.ALWAYS);
        if (control instanceof javafx.scene.layout.Region region) region.setMinWidth(Region.USE_PREF_SIZE);
        HBox r = new HBox(20, text, control);
        r.setAlignment(Pos.CENTER_LEFT);
        r.getStyleClass().add("settings-row");
        return r;
    }

    private static Node choice(List<String> values, List<String> labels, String current, Consumer<String> onPick) {
        ToggleGroup g = new ToggleGroup();
        HBox box = new HBox();
        box.getStyleClass().add("segmented");
        box.setMaxHeight(Region.USE_PREF_SIZE);
        for (int i = 0; i < values.size(); i++) {
            String v = values.get(i);
            ToggleButton b = new ToggleButton(labels.get(i));
            b.getStyleClass().add("chip");
            b.setToggleGroup(g);
            b.setSelected(v.equals(current));
            b.setOnAction(e -> {
                b.setSelected(true);
                onPick.accept(v);
            });
            box.getChildren().add(b);
        }
        return box;
    }

    private static Node check(boolean value, Consumer<Boolean> onChange) {
        CheckBox c = new CheckBox();
        c.setSelected(value);
        c.selectedProperty().addListener((o, a, b) -> onChange.accept(b));
        return c;
    }

    @Override
    public Parent root() {
        return scroll;
    }
}
