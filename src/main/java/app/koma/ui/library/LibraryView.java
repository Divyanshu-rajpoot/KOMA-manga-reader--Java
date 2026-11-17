package app.koma.ui.library;

import app.koma.AppContext;
import app.koma.library.Series;
import app.koma.library.Volume;
import app.koma.ui.shared.CatalogTable;
import app.koma.ui.shared.CoverView;
import app.koma.ui.shared.Css;
import app.koma.ui.shared.Fmt;
import app.koma.ui.shared.VirtualGrid;
import app.koma.ui.shared.ThinProgress;
import app.koma.ui.shell.Screen;
import app.koma.ui.shell.View;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Library: counts, filter chips, sort, Covers / Details. Filter, sort and view persist for the session. */
public final class LibraryView implements View {
    private static String filter = "All";
    private static String sort = "Recently read";
    private static boolean details;

    private final AppContext ctx;
    private final ScrollPane scroll = new ScrollPane();
    private final VBox results = new VBox();
    private final Label counts = new Label();
    private String query;

    public LibraryView(AppContext ctx, String query) {
        this.ctx = ctx;
        this.query = query == null ? "" : query;
        VBox page = new VBox(18);
        page.setPadding(new Insets(4, 36, 36, 36));
        Css.add(page, LibraryView.class, "library");

        Label title = new Label("LIBRARY");
        title.getStyleClass().add("page-title");
        counts.getStyleClass().add("mono");
        VBox head = new VBox(0, title, counts);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox header = new HBox(head, sp, viewToggle());
        header.setAlignment(Pos.CENTER_LEFT);

        HBox chips = new HBox(8);
        ToggleGroup g = new ToggleGroup();
        for (String f : List.of("All", "Manga", "Manhwa", "Reading", "Unread", "Finished")) {
            ToggleButton c = new ToggleButton(f);
            c.getStyleClass().add("chip");
            c.setToggleGroup(g);
            c.setSelected(f.equals(filter));
            c.setOnAction(e -> {
                c.setSelected(true);
                filter = f;
                rebuild();
            });
            chips.getChildren().add(c);
        }
        ComboBox<String> sortBox = new ComboBox<>();
        sortBox.getItems().addAll("Recently read", "Title", "Recently added", "Size");
        sortBox.setValue(sort);
        sortBox.getStyleClass().add("sort-box");
        sortBox.setOnAction(e -> {
            sort = sortBox.getValue();
            rebuild();
        });
        Label sortLabel = new Label("Sort:");
        sortLabel.getStyleClass().add("muted");
        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        HBox filters = new HBox(8, chips, sp2, sortLabel, sortBox);
        filters.setAlignment(Pos.CENTER_LEFT);

        page.getChildren().addAll(header, filters, results);
        scroll.setContent(page);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rebuild();
    }

    public void setQuery(String q) {
        query = q == null ? "" : q;
        rebuild();
    }

    private Node viewToggle() {
        ToggleGroup g = new ToggleGroup();
        ToggleButton covers = new ToggleButton("Covers");
        ToggleButton list = new ToggleButton("Details");
        for (ToggleButton b : List.of(covers, list)) {
            b.getStyleClass().add("chip");
            b.setToggleGroup(g);
        }
        covers.setSelected(!details);
        list.setSelected(details);
        covers.setOnAction(e -> {
            covers.setSelected(true);
            details = false;
            rebuild();
        });
        list.setOnAction(e -> {
            list.setSelected(true);
            details = true;
            rebuild();
        });
        HBox box = new HBox(covers, list);
        box.getStyleClass().add("segmented");
        box.setMaxHeight(Region.USE_PREF_SIZE);
        return box;
    }

    private List<Series> visible() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Series> out = new ArrayList<>();
        for (Series s : ctx.store.series()) {
            boolean ok = switch (filter) {
                case "Manga" -> "manga".equals(s.type);
                case "Manhwa" -> "manhwa".equals(s.type);
                case "Reading" -> "reading".equals(s.status());
                case "Unread" -> "unread".equals(s.status());
                case "Finished" -> "finished".equals(s.status());
                default -> true;
            };
            if (ok && !q.isEmpty()) ok = matches(s, q);
            if (ok) out.add(s);
        }
        Comparator<Series> c = switch (sort) {
            case "Title" -> Comparator.comparing(s -> s.title.toLowerCase(Locale.ROOT));
            case "Recently added" -> Comparator.comparing((Series s) -> s.addedAt == null ? "" : s.addedAt).reversed();
            case "Size" -> Comparator.comparingLong(Series::totalSize).reversed();
            default -> Comparator.comparing((Series s) -> s.lastReadAt() == null ? "" : s.lastReadAt()).reversed()
                    .thenComparing(s -> s.title.toLowerCase(Locale.ROOT));
        };
        out.sort(c);
        return out;
    }

    private static boolean matches(Series s, String q) {
        if (s.title.toLowerCase(Locale.ROOT).contains(q)) return true;
        if (s.titleJp != null && s.titleJp.toLowerCase(Locale.ROOT).contains(q)) return true;
        for (Volume v : s.volumes) if (v.fileName().toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }

    private void rebuild() {
        List<Series> list = visible();
        counts.setText(ctx.store.series().size() + " series · " + ctx.store.volumeCount() + " volumes · "
                + Fmt.bytes(ctx.store.totalSize()) + " · all local"
                + (query.isBlank() ? "" : "  ·  search \"" + query.trim() + "\": " + list.size()));
        if (list.isEmpty()) {
            Label none = new Label(ctx.store.series().isEmpty() ? "NO SERIES YET" : "NOTHING MATCHES");
            none.getStyleClass().add("empty-title");
            Label hint = new Label(ctx.store.series().isEmpty()
                    ? "Use Add CBZ or drop files anywhere in the window." : "Try another filter or search.");
            hint.getStyleClass().add("muted");
            results.getChildren().setAll(new VBox(6, none, hint));
            return;
        }
        results.getChildren().setAll(details ? table(list) : grid(list));
    }

    private Node grid(List<Series> list) {
        // virtualized: only tiles near the viewport exist, so thousands of series stay smooth
        return VirtualGrid.pooled(scroll, list.size(), SeriesTile::new, (n, i) -> ((SeriesTile) n).bind(list.get(i)),
                170, 22, 28, 2, 6);
    }

    /** A library tile that is re-bound to different series as the grid scrolls. */
    private final class SeriesTile extends VBox {
        private final CoverView cover = new CoverView(ctx, 200);
        private final Label name = new Label();
        private final Label sub = new Label();
        private final ThinProgress bar = new ThinProgress(4);
        private Series series;

        SeriesTile() {
            super(6);
            // scrolling moves a bitmap instead of re-rasterizing the cover's shapes and text on every frame
            setCache(true);
            setCacheHint(javafx.scene.CacheHint.SPEED);
            cover.getStyleClass().add("hover-lift");
            cover.setBadgeVisible(false);
            name.getStyleClass().add("tile-title");
            sub.getStyleClass().add("mono-s");
            getChildren().addAll(cover, name, sub, bar);
            VBox.setMargin(name, new Insets(8, 0, 0, 0));
            setOnMouseClicked(e -> {
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) ctx.router.navigate(Screen.SERIES, series.id);
            });
            setOnContextMenuRequested(e -> menu(series).show(this, e.getScreenX(), e.getScreenY()));
        }

        void bind(Series s) {
            series = s;
            cover.bind(s, null);
            name.setText(Fmt.upper(s.title));
            sub.setText(s.finishedCount() + " / " + s.volumes.size() + " volumes read");
            bar.set(s.volumes.isEmpty() ? 0 : s.volumes.stream().mapToDouble(Volume::fraction).sum() / s.volumes.size());
        }
    }

    private ContextMenu menu(Series s) {
        MenuItem open = new MenuItem("Open series");
        open.setOnAction(e -> ctx.router.navigate(Screen.SERIES, s.id));
        MenuItem resume = new MenuItem("Resume reading");
        resume.setOnAction(e -> {
            Volume v = s.current();
            if (v != null) ctx.router.navigate(Screen.READER, v.id);
        });
        MenuItem remove = new MenuItem("Remove from library (files stay)");
        remove.setOnAction(e -> {
            ctx.store.removeSeries(s);
            rebuild();
            ctx.window.toast("Removed " + s.title + " from the library. Files were not deleted.");
        });
        return new ContextMenu(open, resume, remove);
    }

    private Node table(List<Series> list) {
        CatalogTable t = new CatalogTable(
                new String[]{"ID", "SERIES", "TYPE", "DIRECTION", "VOLS", "READ", "PAGES", "SIZE", "LAST READ"},
                new double[]{0.5, 2.4, 0.9, 1.2, 0.6, 0.7, 0.8, 0.9, 1.2});
        t.setVirtual(scroll, list.size(), i -> {
            Series s = list.get(i);
            return List.of(Fmt.pad3(i + 1), Fmt.upper(s.title), Fmt.upper(s.type), s.directionLabel(),
                    s.volumes.size(), s.finishedCount() + "/" + s.volumes.size(), s.totalPages(),
                    Fmt.bytes(s.totalSize()), Fmt.ago(s.lastReadAt()));
        }, i -> ctx.router.navigate(Screen.SERIES, list.get(i).id));
        return t;
    }

    @Override
    public Parent root() {
        return scroll;
    }
}
