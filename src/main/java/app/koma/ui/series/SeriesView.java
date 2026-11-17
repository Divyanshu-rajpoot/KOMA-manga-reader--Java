package app.koma.ui.series;

import app.koma.AppContext;
import app.koma.core.Anim;
import app.koma.core.Exec;
import app.koma.library.Series;
import app.koma.library.Volume;
import app.koma.ui.shared.CatalogTable;
import app.koma.ui.shared.CoverView;
import app.koma.ui.shared.Css;
import app.koma.ui.shared.Fmt;
import app.koma.ui.shared.Icons;
import app.koma.ui.shared.VirtualGrid;
import app.koma.ui.shared.ThinProgress;
import app.koma.ui.shell.Screen;
import app.koma.ui.shell.View;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.animation.PauseTransition;
import javafx.scene.layout.StackPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Series: cover, title, type/direction chips, stats band, actions, volume grid, files table. */
public final class SeriesView implements View {
    private final AppContext ctx;
    private final ScrollPane scroll = new ScrollPane();
    private final Series s;

    public SeriesView(AppContext ctx, String seriesId) {
        this.ctx = ctx;
        this.s = ctx.store.findSeries(seriesId);
        VBox page = new VBox(26);
        page.setPadding(new Insets(10, 36, 36, 36));
        Css.add(page, SeriesView.class, "series");
        if (s == null) {
            Label l = new Label("THIS SERIES IS NO LONGER IN THE LIBRARY");
            l.getStyleClass().add("empty-title");
            page.getChildren().add(l);
        } else {
            page.getChildren().addAll(header(), section("VOLUMES"), volumes(), section("FILES"), files());
        }
        scroll.setContent(page);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    }

    @Override
    public String title() {
        return s == null ? null : s.title;
    }

    private Node header() {
        CoverView cover = new CoverView(ctx, s, null, 240);
        cover.setBadgeVisible(false);
        cover.getStyleClass().add("cover-shadow-10");
        cover.setMinWidth(240);
        cover.setMaxWidth(240);

        Label kicker = new Label("SERIES · " + Fmt.upper(s.type) + " · " + s.volumes.size() + (s.volumes.size() == 1 ? " VOLUME" : " VOLUMES"));
        kicker.getStyleClass().add("mono");
        Label title = new Label(Fmt.upper(s.title));
        title.getStyleClass().add("display-l");
        title.setWrapText(true);
        StackPane titleBox = new StackPane(title);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Label jp = new Label(s.titleJp == null ? "" : s.titleJp);
        jp.getStyleClass().addAll("jp", "series-jp");
        jp.setManaged(s.titleJp != null);

        HBox chips = new HBox(8);
        chips.getChildren().addAll(choice(List.of("manga", "manhwa"), s.type, v -> ctx.store.setSeriesMeta(s, null, v, null)));
        Label sep = new Label("·");
        sep.getStyleClass().add("muted");
        chips.getChildren().add(sep);
        chips.getChildren().addAll(choice(List.of("rtl", "ltr", "ttb"), s.direction, v -> ctx.store.setSeriesMeta(s, null, null, v)));
        chips.setAlignment(Pos.CENTER_LEFT);

        HBox stats = new HBox(stat(String.valueOf(s.volumes.size()), "VOLUMES"),
                stat(s.finishedCount() + "", "FINISHED"),
                stat(String.format(Locale.ROOT, "%,d", s.totalPages()), "PAGES"),
                stat(Fmt.bytes(s.totalSize()), "ON DISK"));
        stats.getStyleClass().add("stats-band");

        Volume cur = s.current();
        Button resume = new Button(cur != null && cur.isStarted() ? "RESUME VOL. " + cur.label() : "READ VOL. " + (cur == null ? "" : cur.label()));
        resume.setGraphic(Icons.of("play", 14));
        resume.getStyleClass().add("accent-button");
        resume.setDisable(cur == null);
        resume.setOnAction(e -> ctx.router.navigate(Screen.READER, cur.id));
        Button add = new Button("ADD VOLUMES");
        add.setGraphic(Icons.of("plus", 14));
        add.getStyleClass().add("outline-button");
        add.setOnAction(e -> ctx.window.nav().chooseFiles());
        Button rename = new Button("RENAME");
        rename.getStyleClass().add("outline-button");
        rename.setOnAction(e -> rename(titleBox, title));
        Button remove = new Button("REMOVE");
        remove.getStyleClass().add("ghost-button");
        // ask once before removing: the first click arms the button for 3 seconds
        PauseTransition disarm = new PauseTransition(Duration.seconds(3));
        disarm.setOnFinished(e -> {
            remove.setText("REMOVE");
            remove.getStyleClass().remove("danger");
        });
        remove.setOnAction(e -> {
            if (!remove.getStyleClass().contains("danger")) {
                remove.setText("CLICK AGAIN TO REMOVE");
                remove.getStyleClass().add("danger");
                disarm.playFromStart();
                return;
            }
            disarm.stop();
            ctx.store.removeSeries(s);
            ctx.window.toast("Removed " + s.title + " from the library. Your files were not deleted.");
            ctx.router.navigate(Screen.LIBRARY, null);
        });
        HBox actions = new HBox(12, resume, add, rename, remove);

        VBox info = new VBox(8, kicker, titleBox, jp, chips, stats, actions);
        VBox.setMargin(stats, new Insets(12, 0, 6, 0));
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox head = new HBox(40, cover, info);
        head.setPadding(new Insets(0, 10, 10, 0));
        Anim.slam(title, Duration.ZERO);
        return head;
    }

    private List<Node> choice(List<String> values, String current, java.util.function.Consumer<String> onPick) {
        ToggleGroup g = new ToggleGroup();
        List<Node> out = new ArrayList<>();
        for (String v : values) {
            ToggleButton b = new ToggleButton(label(v));
            b.getStyleClass().add("chip");
            b.setToggleGroup(g);
            b.setSelected(v.equals(current));
            b.setOnAction(e -> {
                b.setSelected(true);
                onPick.accept(v);
            });
            out.add(b);
        }
        return out;
    }

    private static String label(String v) {
        return switch (v) {
            case "manga" -> "Manga";
            case "manhwa" -> "Manhwa";
            case "rtl" -> "Right to left";
            case "ltr" -> "Left to right";
            case "ttb" -> "Long strip";
            default -> v;
        };
    }

    private static Node stat(String value, String label) {
        Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        Label l = new Label(label);
        l.getStyleClass().add("mono-s");
        VBox b = new VBox(0, v, l);
        b.getStyleClass().add("stat");
        HBox.setHgrow(b, Priority.ALWAYS);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private static Node section(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("display-s");
        VBox box = new VBox(6, l);
        javafx.scene.layout.Region rule = new javafx.scene.layout.Region();
        rule.getStyleClass().add("rule");
        box.getChildren().add(rule);
        return box;
    }

    private Node volumes() {
        List<Volume> vols = s.sortedVolumes();
        return new VirtualGrid(scroll, vols.size(), i -> volumeCell(vols.get(i)), 120, 18, 22, 3, 8);
    }

    private Node volumeCell(Volume v) {
        {
            CoverView c = new CoverView(ctx, s, v, 140);
            c.getStyleClass().add("hover-lift");
            ThinProgress bar = new ThinProgress(4);
            bar.set(v.fraction());
            String state = v.isFinished() ? "READ" : v.isStarted()
                    ? "P." + (v.progress.page + 1) + " / " + v.pages : "UNREAD · " + v.pages + " PP";
            Label name = new Label((v.kind.equals("chapter") ? "CH. " : "VOL. ") + v.label());
            name.getStyleClass().addAll("mono", "strong");
            Label l = new Label(state);
            l.getStyleClass().add("mono-s");
            VBox cell = new VBox(6, c, bar, name, l);
            cell.setCache(true);
            cell.setCacheHint(javafx.scene.CacheHint.SPEED);
            VBox.setMargin(name, new javafx.geometry.Insets(2, 0, -4, 0));
            cell.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) ctx.router.navigate(Screen.READER, v.id);
            });
            cell.setOnContextMenuRequested(e -> volumeMenu(v).show(cell, e.getScreenX(), e.getScreenY()));
            return cell;
        }
    }

    private ContextMenu volumeMenu(Volume v) {
        MenuItem read = new MenuItem(v.isFinished() ? "Mark as unread" : "Mark as read");
        read.setOnAction(e -> {
            ctx.store.setFinished(v, !v.isFinished());
            ctx.router.replace(Screen.SERIES, s.id);
        });
        MenuItem show = new MenuItem("Show in Explorer");
        show.setOnAction(e -> Exec.io(() -> {
            try {
                new ProcessBuilder("explorer.exe", "/select,", v.path).start();
            } catch (Exception ex) {
                Exec.fx(() -> ctx.window.toast("Could not open Explorer: " + ex.getMessage()));
            }
        }));
        MenuItem remove = new MenuItem("Remove from library (file stays)");
        remove.setOnAction(e -> {
            boolean last = s.volumes.size() == 1;
            ctx.store.removeVolume(v);
            if (last) ctx.router.navigate(Screen.LIBRARY, null);
            else ctx.router.replace(Screen.SERIES, s.id);
        });
        return new ContextMenu(read, show, remove);
    }

    private Node files() {
        CatalogTable t = new CatalogTable(new String[]{"VOL", "FILE", "SIZE", "PAGES", "STORAGE", "ADDED"},
                new double[]{0.6, 3.4, 0.9, 0.7, 0.9, 1});
        for (Volume v : s.sortedVolumes()) {
            t.addRow(List.of(v.label(), v.fileName(), Fmt.bytes(v.size), v.pages, Fmt.upper(v.storage),
                    v.addedAt == null ? "" : Fmt.shortDate(v.addedAt.substring(0, 10))), () -> ctx.router.navigate(Screen.READER, v.id));
        }
        return t;
    }

    /** Inline rename: the title turns into a text field (Enter saves, Esc cancels). */
    private void rename(StackPane titleBox, Label title) {
        TextField field = new TextField(s.title);
        field.getStyleClass().add("rename-field");
        HBox.setHgrow(field, Priority.ALWAYS);
        Button save = new Button("SAVE");
        save.getStyleClass().add("ink-button");
        Button cancel = new Button("CANCEL");
        cancel.getStyleClass().add("ghost-button");
        HBox editor = new HBox(10, field, save, cancel);
        editor.setAlignment(Pos.CENTER_LEFT);
        editor.setMaxWidth(760);
        Runnable close = () -> titleBox.getChildren().setAll(title);
        Runnable commit = () -> {
            String t = field.getText().trim();
            if (t.isEmpty() || t.equals(s.title)) {
                close.run();
                return;
            }
            ctx.store.setSeriesMeta(s, t, null, null);
            ctx.router.replace(Screen.SERIES, s.id);
        };
        save.setOnAction(e -> commit.run());
        cancel.setOnAction(e -> close.run());
        field.setOnAction(e -> commit.run());
        field.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                close.run();
                e.consume();
            }
        });
        titleBox.getChildren().setAll(editor);
        field.requestFocus();
        field.selectAll();
    }

    @Override
    public Parent root() {
        return scroll;
    }
}
