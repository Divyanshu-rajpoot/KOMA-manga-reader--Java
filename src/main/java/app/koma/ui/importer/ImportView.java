package app.koma.ui.importer;

import app.koma.AppContext;
import app.koma.core.Anim;
import app.koma.core.Exec;
import app.koma.core.MotionPrefs;
import app.koma.library.ImportService;
import app.koma.library.ImportService.Item;
import app.koma.library.ImportService.State;
import app.koma.ui.shared.Css;
import app.koma.ui.shared.Fmt;
import app.koma.ui.shared.Icons;
import app.koma.ui.shell.Screen;
import app.koma.ui.shell.View;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Import: drop zone, detection rules, keep-in-place / copy option, and the queue of detected files. */
public final class ImportView implements View {
    private final AppContext ctx;
    private final ScrollPane scroll = new ScrollPane();
    private final VBox queue = new VBox(10);
    private final Label detected = new Label();
    private final Button addAll = new Button("Add all");
    private final Button openLibrary = new Button("Open library");
    private final List<ImportRow> rows = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();
    private final Timeline ants;
    private Node keepCard, copyCard;
    private boolean adding;

    public ImportView(AppContext ctx, List<Path> initial) {
        this.ctx = ctx;
        VBox page = new VBox(10);
        page.setPadding(new Insets(4, 36, 36, 36));
        Css.add(page, ImportView.class, "importer");

        Label title = new Label("IMPORT VOLUMES");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Add CBZ files or a whole folder. Nothing is uploaded; KOMA only reads the files on your PC.");
        sub.getStyleClass().add("muted");

        Rectangle border = new Rectangle();
        border.getStyleClass().add("ants");
        border.setManaged(false);
        StackPane zone = dropZone(border);
        ants = new Timeline(new KeyFrame(Duration.seconds(1), new KeyValue(border.strokeDashOffsetProperty(), -32, Interpolator.LINEAR)));
        ants.setCycleCount(Animation.INDEFINITE);
        Anim.play(ants);
        MotionPrefs.reducedProperty().addListener((o, a, r) -> {
            if (r) ants.stop();
        });

        VBox left = new VBox(16, zone, rules(), storageOptions());
        left.setPrefWidth(560);
        HBox.setHgrow(left, Priority.ALWAYS);

        detected.getStyleClass().add("h2");
        addAll.getStyleClass().add("accent-button");
        addAll.setOnAction(e -> addAll());
        openLibrary.getStyleClass().add("outline-button");
        openLibrary.setOnAction(e -> ctx.router.navigate(Screen.LIBRARY, null));
        openLibrary.setVisible(false);
        openLibrary.setManaged(false);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox qHead = new HBox(10, detected, sp, openLibrary, addAll);
        qHead.setAlignment(Pos.CENTER_LEFT);
        VBox right = new VBox(14, qHead, queue);
        right.setPrefWidth(620);
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox cols = new HBox(30, left, right);
        VBox.setMargin(cols, new Insets(18, 0, 0, 0));
        page.getChildren().addAll(title, sub, cols);
        scroll.setContent(page);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        updateHeader();
        List<Path> start = new ArrayList<>();
        if (initial != null) start.addAll(initial);
        if (ctx.watched != null) start.addAll(ctx.watched.take());
        if (!start.isEmpty()) addPaths(start);
    }

    private StackPane dropZone(Rectangle border) {
        Region icon = Icons.of("upload", 56);
        icon.getStyleClass().add("drop-icon");
        Label h = new Label("Drop CBZ files or a folder");
        h.getStyleClass().add("drop-title");
        Label sub = new Label("CBZ · ZIP · files of any size");
        sub.getStyleClass().add("muted");
        Button files = new Button("Choose files");
        files.getStyleClass().add("ink-button");
        files.setOnAction(e -> ctx.window.nav().chooseFiles());
        Button folder = new Button("Choose folder");
        folder.getStyleClass().add("outline-button");
        folder.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Choose a folder of CBZ files");
            File dir = dc.showDialog(ctx.stage);
            if (dir != null) addPaths(List.of(dir.toPath()));
        });
        HBox buttons = new HBox(10, files, folder);
        buttons.setAlignment(Pos.CENTER);
        VBox content = new VBox(12, icon, h, sub, buttons);
        content.setAlignment(Pos.CENTER);
        StackPane zone = new StackPane(border, content) {
            @Override
            protected void layoutChildren() {
                super.layoutChildren();
                border.setX(1.5);
                border.setY(1.5);
                border.setWidth(getWidth() - 3);
                border.setHeight(getHeight() - 3);
            }
        };
        zone.getStyleClass().add("drop-zone");
        zone.setMinHeight(320);
        return zone;
    }

    private Node rules() {
        TextFlow t = new TextFlow(
                text("Series detection: ", false), text("{Series} v{NN}.cbz", true), text(" · ", false),
                text("{Series} Vol {N}.cbz", true), text(" · ", false), text("{Series} {NN}-{NN}.cbz", true),
                text(" · ", false), text("{Series} c{NNN}.cbz", true),
                text(". A ComicInfo.xml inside the archive overrides the file name. Click Edit on a row to fix a match.", false));
        StackPane box = new StackPane(t);
        box.getStyleClass().add("rules-box");
        return box;
    }

    private static Text text(String s, boolean bold) {
        Text t = new Text(s);
        t.getStyleClass().addAll("rules-text", bold ? "rules-bold" : "rules-plain");
        return t;
    }

    private Node storageOptions() {
        keepCard = option("Keep files where they are", "KOMA saves only a link. No extra disk space used.", "linked");
        copyCard = option("Copy into KOMA library folder", "One tidy folder: Series\\Vol NN.cbz", "copied");
        HBox row = new HBox(14, keepCard, copyCard);
        HBox.setHgrow(keepCard, Priority.ALWAYS);
        HBox.setHgrow(copyCard, Priority.ALWAYS);
        paintOptions();
        return row;
    }

    private Node option(String title, String sub, String value) {
        Label t = new Label(title);
        t.getStyleClass().add("strong");
        t.setWrapText(true);
        Label s = new Label(sub);
        s.getStyleClass().add("small");
        s.setWrapText(true);
        VBox card = new VBox(4, t, s);
        card.getStyleClass().add("option-card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setOnMouseClicked(e -> {
            ctx.settings.importStorage = value;
            ctx.settings.save();
            paintOptions();
        });
        return card;
    }

    private void paintOptions() {
        boolean copy = "copied".equals(ctx.settings.importStorage);
        keepCard.getStyleClass().remove("selected");
        copyCard.getStyleClass().remove("selected");
        (copy ? copyCard : keepCard).getStyleClass().add("selected");
    }

    /** Adds files or folders to the queue (folders are scanned off the FX thread). */
    public void addPaths(List<Path> paths) {
        Exec.io(() -> ImportService.expand(paths)).thenAccept(found -> Exec.fx(() -> {
            int before = rows.size();
            for (Path p : found) {
                String key = p.toAbsolutePath().toString().toLowerCase();
                if (!seen.add(key)) continue;
                Item it = new Item(p);
                ImportRow row = new ImportRow(ctx, it, this::updateHeader);
                rows.add(row);
                queue.getChildren().add(row);
                Anim.riseIn(row, Anim.STAGGER.multiply(Math.min(rows.size() - before, 12)));
                ImportService.analyze(it, ctx.store, ctx.settings.defaultDirection).whenComplete((r, err) -> Exec.fx(() -> {
                    if (err != null) {
                        it.state = State.FAILED;
                        it.error = err.getMessage();
                    }
                    row.refresh();
                    row.loadCover();
                    updateHeader();
                }));
            }
            if (found.isEmpty()) ctx.window.toast("No CBZ or ZIP files found there.");
            updateHeader();
        }));
    }

    private void updateHeader() {
        long total = rows.stream().mapToLong(r -> r.item.size).sum();
        detected.setText("Detected · " + rows.size() + (rows.size() == 1 ? " file" : " files") + (total > 0 ? " · " + Fmt.bytes(total) : ""));
        long ready = rows.stream().filter(r -> r.item.canAdd()).count();
        addAll.setText(ready > 0 ? "Add all (" + ready + ")" : "Add all");
        addAll.setDisable(ready == 0 || adding);
        if (rows.isEmpty()) {
            Label empty = new Label("Files you add show up here with their series match.");
            empty.getStyleClass().add("muted");
            queue.getChildren().setAll(empty);
        } else if (!(queue.getChildren().get(0) instanceof ImportRow)) {
            queue.getChildren().remove(0);
        }
    }

    /** Adds every file that is ready (same as the "Add all" button). */
    public void addAllReady() {
        addAll();
    }

    private void addAll() {
        boolean copy = "copied".equals(ctx.settings.importStorage);
        Path libFolder = Path.of(ctx.settings.libraryFolder);
        List<ImportRow> todo = rows.stream().filter(r -> r.item.canAdd()).toList();
        if (todo.isEmpty()) return;
        adding = true;
        updateHeader();
        int[] left = {todo.size()};
        int[] added = {0};
        for (ImportRow r : todo) {
            Item it = r.item;
            it.state = State.ADDING;
            r.refresh();
            ImportService.prepare(it, copy, libFolder, f -> Exec.fx(() -> {
                it.copyProgress = f;
                r.refresh();
            })).whenComplete((vol, err) -> Exec.fx(() -> {
                if (err != null) {
                    it.state = State.FAILED;
                    it.error = "Could not add: " + (err.getCause() != null ? err.getCause().getMessage() : err.getMessage());
                } else {
                    ctx.store.addVolume(it.series, it.titleJp, it.type, it.direction, vol);
                    it.state = State.ADDED;
                    added[0]++;
                }
                r.refresh();
                if (--left[0] == 0) {
                    adding = false;
                    updateHeader();
                    openLibrary.setVisible(true);
                    openLibrary.setManaged(true);
                    ctx.window.toast("Added " + added[0] + (added[0] == 1 ? " volume" : " volumes") + " to your library.");
                }
            }));
        }
    }

    @Override
    public Parent root() {
        return scroll;
    }

    @Override
    public void dispose() {
        ants.stop();
    }
}
