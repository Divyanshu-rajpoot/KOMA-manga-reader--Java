package app.koma.ui.importer;

import app.koma.AppContext;
import app.koma.core.Exec;
import app.koma.library.ImportService.Item;
import app.koma.library.ImportService.State;
import app.koma.library.Volume;
import app.koma.ui.shared.Fmt;
import app.koma.ui.shared.Icons;
import app.koma.ui.shared.ThinProgress;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** One file in the import queue: cover, name, series match (editable), progress, steps. */
final class ImportRow extends HBox {
    final Item item;
    private final AppContext ctx;
    private final ImageView thumb = new ImageView();
    private final Label size = new Label();
    private final Label match = new Label();
    private final Hyperlink edit = new Hyperlink("Edit");
    private final HBox editor = new HBox(8);
    private final TextField seriesField = new TextField();
    private final TextField numberField = new TextField();
    private final ThinProgress bar = new ThinProgress(4);
    private final HBox steps = new HBox(14);
    private boolean coverDone;
    private final Runnable onChange;

    ImportRow(AppContext ctx, Item item, Runnable onChange) {
        this.ctx = ctx;
        this.item = item;
        this.onChange = onChange;
        getStyleClass().add("import-row");
        setSpacing(14);

        thumb.setFitWidth(42);
        thumb.setFitHeight(60);
        thumb.setPreserveRatio(false);
        StackPane thumbBox = new StackPane(thumb);
        thumbBox.getStyleClass().add("import-thumb");
        thumbBox.setMinSize(42, 60);
        thumbBox.setMaxSize(42, 60);

        Label name = new Label(item.fileName);
        name.getStyleClass().add("strong");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        size.getStyleClass().add("mono-s");
        HBox top = new HBox(8, name, size);

        match.getStyleClass().add("small");
        edit.getStyleClass().add("edit-link");
        edit.setOnAction(e -> {
            editor.setVisible(!editor.isVisible());
            editor.setManaged(editor.isVisible());
        });
        HBox matchRow = new HBox(8, match, edit);
        matchRow.setAlignment(Pos.CENTER_LEFT);

        seriesField.setPromptText("Series title");
        HBox.setHgrow(seriesField, Priority.ALWAYS);
        numberField.setPromptText("Vol.");
        numberField.setPrefWidth(70);
        Label hint = new Label("Enter ↵");
        hint.getStyleClass().add("mono-s");
        editor.getChildren().addAll(seriesField, numberField, hint);
        editor.setAlignment(Pos.CENTER_LEFT);
        editor.setVisible(false);
        editor.setManaged(false);
        seriesField.setOnAction(e -> applyEdit());
        numberField.setOnAction(e -> applyEdit());
        seriesField.focusedProperty().addListener((o, a, f) -> {
            if (!f) applyEdit();
        });
        numberField.focusedProperty().addListener((o, a, f) -> {
            if (!f) applyEdit();
        });

        VBox body = new VBox(5, top, matchRow, editor, bar, steps);
        HBox.setHgrow(body, Priority.ALWAYS);
        getChildren().addAll(thumbBox, body);
        refresh();
    }

    private void applyEdit() {
        String t = seriesField.getText().trim();
        if (!t.isEmpty()) item.series = t;
        try {
            String n = numberField.getText().trim().replace(',', '.');
            if (!n.isEmpty()) {
                item.number = Double.parseDouble(n);
                item.numberEnd = null;
            }
        } catch (NumberFormatException ignored) {
            numberField.setText(Volume.num(item.number));
        }
        if (item.state == State.UNMATCHED && item.series != null && !item.series.isBlank()) item.state = State.READY;
        refresh();
        onChange.run();
    }

    /** Re-reads the item into the UI. FX thread. */
    void refresh() {
        size.setText(item.size > 0 ? Fmt.bytes(item.size) : "");
        match.getStyleClass().remove("accent-text");
        edit.setVisible(item.state == State.READY || item.state == State.UNMATCHED);
        switch (item.state) {
            case QUEUED, READING -> match.setText("Reading index…");
            case UNMATCHED -> {
                match.setText("No match: choose a series");
                match.getStyleClass().add("accent-text");
                if (!editor.isVisible()) {
                    editor.setVisible(true);
                    editor.setManaged(true);
                }
            }
            case READY -> match.setText(item.series + "  →  " + numberText() + (item.fromComicInfo ? "   (ComicInfo.xml)" : ""));
            case DUPLICATE -> match.setText("Already in your library");
            case UNSUPPORTED, FAILED -> {
                match.setText(item.error == null ? "Can't read this file" : item.error);
                match.getStyleClass().add("accent-text");
            }
            case ADDING -> match.setText("Adding to " + item.series + "…");
            case ADDED -> match.setText(item.series + "  →  " + numberText() + "   ✓ added");
        }
        if (!seriesField.isFocused() && item.series != null) seriesField.setText(item.series);
        if (!numberField.isFocused() && item.state != State.QUEUED) {
            numberField.setText(item.state == State.UNMATCHED && item.number == 0 ? "" : Volume.num(item.number));
        }

        double p = switch (item.state) {
            case QUEUED -> 0.05;
            case READING -> 0.3;
            case READY, UNMATCHED -> coverDone ? 1 : 0.7;
            case ADDING -> item.copyProgress > 0 ? item.copyProgress : 0.5;
            case ADDED, DUPLICATE -> 1;
            default -> 0;
        };
        bar.set(p);

        steps.getChildren().clear();
        boolean indexed = item.pages > 0;
        steps.getChildren().add(step(indexed, indexed
                ? String.format(java.util.Locale.ROOT, "Read index %.2f s · %d pp", item.indexMs / 1000, item.pages) : "Read index"));
        steps.getChildren().add(step(coverDone, "Cover"));
        steps.getChildren().add(step(coverDone, "Thumbnails"));
        if (item.state == State.ADDING && item.copyProgress > 0) {
            steps.getChildren().add(step(false, String.format(java.util.Locale.ROOT, "Copying %.0f%%", item.copyProgress * 100)));
        }
    }

    private String numberText() {
        String kind = "chapter".equals(item.kind) ? "Ch. " : "omnibus".equals(item.kind) ? "Vols. " : "Vol. ";
        return kind + Volume.num(item.number).replaceFirst("^0(?=\\d)", "")
                + (item.numberEnd != null ? "–" + Volume.num(item.numberEnd).replaceFirst("^0(?=\\d)", "") : "");
    }

    /** Loads the cover once the index is read (also fills the disk thumbnail cache). */
    void loadCover() {
        if (item.pages <= 0) return;
        ctx.thumbs.cover(item.path.toAbsolutePath().toString(), item.size, item.mtime).whenComplete((img, err) -> Exec.fx(() -> {
            if (img != null) thumb.setImage(img);
            coverDone = img != null;
            refresh();
        }));
    }

    private static HBox step(boolean done, String text) {
        Label l = new Label(text);
        l.getStyleClass().add(done ? "step-done" : "step-todo");
        HBox h = new HBox(4);
        h.setAlignment(Pos.CENTER_LEFT);
        if (done) h.getChildren().add(Icons.of("check", 11));
        h.getChildren().add(l);
        return h;
    }
}
