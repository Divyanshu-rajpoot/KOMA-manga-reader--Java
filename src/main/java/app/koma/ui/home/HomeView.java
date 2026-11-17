package app.koma.ui.home;

import app.koma.AppContext;
import app.koma.core.Anim;
import app.koma.library.Series;
import app.koma.ui.shared.CatalogTable;
import app.koma.ui.shared.Css;
import app.koma.ui.shared.Fmt;
import app.koma.ui.shared.Icons;
import app.koma.ui.shared.SlantPanel;
import app.koma.ui.shell.Screen;
import app.koma.ui.shell.View;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Home: top band (info column + 3D carousel), mid row (reading card, up next, drop), catalog table. */
public final class HomeView implements View {
    private final AppContext ctx;
    private final ScrollPane scroll = new ScrollPane();
    private CoverCarousel carousel;

    public HomeView(AppContext ctx) {
        this.ctx = ctx;
        VBox page = new VBox(22);
        page.getStyleClass().add("home");
        page.setPadding(new Insets(0, 36, 36, 36));
        Css.add(page, HomeView.class, "home");

        List<Series> all = new ArrayList<>(ctx.store.series());
        all.sort(Comparator.comparing(s -> s.title.toLowerCase()));
        if (all.isEmpty()) {
            page.getChildren().addAll(emptyBand(), dropRow());
        } else {
            List<Series> featured = featured(all);
            InfoColumn info = new InfoColumn(ctx, all);
            ReadingCard card = new ReadingCard();
            UpNext upNext = new UpNext(ctx);
            carousel = new CoverCarousel(ctx, featured, s -> {
                info.show(s, true);
                card.show(s);
                upNext.show(s);
            }, s -> ctx.router.navigate(Screen.SERIES, s.id));
            Series first = featured.get(0);
            info.show(first, true);
            card.show(first);
            upNext.show(first);

            page.getChildren().addAll(topBand(info, carousel), midRow(card, upNext), catalog(all));
        }
        scroll.setContent(page);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    }

    /** In-progress series by last read; otherwise the most recently added. */
    private static List<Series> featured(List<Series> all) {
        List<Series> reading = new ArrayList<>(all.stream().filter(s -> s.lastReadAt() != null).toList());
        reading.sort(Comparator.comparing(Series::lastReadAt).reversed());
        if (reading.isEmpty()) {
            reading = new ArrayList<>(all);
            reading.sort(Comparator.comparing((Series s) -> s.addedAt == null ? "" : s.addedAt).reversed());
        }
        return reading.subList(0, Math.min(9, reading.size()));
    }

    private Node topBand(InfoColumn info, CoverCarousel carousel) {
        Region divider = new Region();
        divider.getStyleClass().add("rule-v");
        info.setPadding(new Insets(22, 24, 22, 0));
        HBox band = new HBox(info, divider, carousel);
        band.getStyleClass().add("top-band");
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox.setHgrow(carousel, Priority.ALWAYS);
        info.setPrefWidth(1);
        carousel.setPrefWidth(1.3);
        // keep 1 : 1.3
        band.widthProperty().addListener((o, a, w) -> {
            double avail = w.doubleValue() - 3;
            info.setPrefWidth(avail / 2.3);
            carousel.setPrefWidth(avail * 1.3 / 2.3);
            info.setMaxWidth(avail / 2.3);
        });
        return band;
    }

    private Node midRow(ReadingCard card, UpNext upNext) {
        SlantPanel p1 = new SlantPanel(card, 0.03, false, true, "panel-slant");
        SlantPanel p2 = new SlantPanel(upNext, 0.03, false, true, "panel-slant");
        SlantPanel p3 = new SlantPanel(new DropPanel(ctx), 0.03, true, false, "invert-slant");
        HBox row = new HBox(16, p1, p2, p3);
        for (SlantPanel p : List.of(p1, p2, p3)) {
            HBox.setHgrow(p, Priority.ALWAYS);
            p.setPrefWidth(400);
            p.setMinHeight(280);
            p.setPrefHeight(280);
        }
        p3.getStyleClass().add("invert-panel");
        Anim.slam(p1, Duration.millis(60));
        Anim.slam(p2, Duration.millis(120));
        Anim.slam(p3, Duration.millis(180));
        return row;
    }

    private Node dropRow() {
        SlantPanel p = new SlantPanel(new DropPanel(ctx), 0.03, true, false, "invert-slant");
        p.getStyleClass().add("invert-panel");
        p.setMinHeight(260);
        return p;
    }

    private Node emptyBand() {
        Label kicker = new Label("NOTHING ON THE SHELF · 0 SERIES");
        kicker.getStyleClass().add("mono");
        javafx.scene.text.Text num = new javafx.scene.text.Text("№");
        num.getStyleClass().addAll("display-xl", "display-text");
        javafx.scene.text.Text zero = new javafx.scene.text.Text("00");
        zero.getStyleClass().addAll("display-xl", "display-text", "accent-fill");
        num.setBoundsType(javafx.scene.text.TextBoundsType.VISUAL);
        zero.setBoundsType(javafx.scene.text.TextBoundsType.VISUAL);
        HBox numRow = new HBox(6, num, zero);
        numRow.setAlignment(Pos.BOTTOM_LEFT);
        VBox.setMargin(numRow, new Insets(14, 0, 10, 0));
        Label title = new Label("YOUR LIBRARY IS EMPTY");
        title.getStyleClass().add("display-l");
        Label sub = new Label("Import CBZ files or a folder of them. KOMA reads them where they are, at any size, fully offline.");
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);
        Button add = new Button("ADD CBZ FILES");
        add.setGraphic(Icons.of("plus", 14));
        add.getStyleClass().add("accent-button");
        add.setOnAction(e -> ctx.window.nav().chooseFiles());
        Button imp = new Button("OPEN IMPORT");
        imp.getStyleClass().add("outline-button");
        imp.setOnAction(e -> ctx.router.navigate(Screen.IMPORT, null));
        HBox buttons = new HBox(12, add, imp);
        VBox box = new VBox(8, kicker, numRow, title, sub, buttons);
        VBox.setMargin(buttons, new Insets(14, 0, 0, 0));
        box.setPadding(new Insets(22, 0, 26, 0));
        box.getStyleClass().add("top-band");
        Anim.slam(numRow, Duration.ZERO);
        return box;
    }

    private Node catalog(List<Series> all) {
        CatalogTable t = new CatalogTable(
                new String[]{"ID", "SERIES", "TYPE", "VOLS", "READ", "SIZE", "STATUS"},
                new double[]{0.6, 2.4, 1.2, 0.8, 0.8, 1, 1});
        // virtualized: rows exist only near the viewport (a row is ~10 nodes; thousands of series used to build all of them)
        t.setVirtual(scroll, all.size(), i -> {
            Series s = all.get(i);
            Label tag = switch (s.status()) {
                case "reading" -> CatalogTable.tag("OUT", "out");
                case "finished" -> CatalogTable.tag("FINISHED", "finished");
                default -> CatalogTable.tag("ON SHELF", "shelf");
            };
            return List.of(Fmt.pad3(i + 1), Fmt.upper(s.title), Fmt.upper(s.type), s.volumes.size(),
                    s.finishedCount() + "/" + s.volumes.size(), Fmt.bytes(s.totalSize()), tag);
        }, i -> ctx.router.navigate(Screen.SERIES, all.get(i).id));
        HBox.setHgrow(t, Priority.ALWAYS);
        VBox wrap = new VBox(t);
        wrap.setAlignment(Pos.TOP_LEFT);
        return wrap;
    }

    @Override
    public Parent root() {
        return scroll;
    }

    @Override
    public void dispose() {
        if (carousel != null) carousel.stop();
    }
}
