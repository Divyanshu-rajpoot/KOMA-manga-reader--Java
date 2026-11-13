package app.koma.ui.shared;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** Ink header row, mono 12px rows; a row turns accent on hover. Columns share width by weight. */
public final class CatalogTable extends VBox {
    private final double[] weights;

    public CatalogTable(String[] headers, double[] weights) {
        this.weights = weights;
        getStyleClass().add("catalog");
        GridPane head = row();
        head.getStyleClass().setAll("catalog-header");
        for (int i = 0; i < headers.length; i++) head.add(new Label(headers[i]), i, 0);
        getChildren().add(head);
    }

    /** Cells are Strings (mono labels) or Nodes. The first column is bold. */
    public GridPane addRow(List<Object> cells, Runnable onClick) {
        GridPane r = row();
        r.getStyleClass().setAll("catalog-row");
        for (int i = 0; i < cells.size(); i++) {
            Object c = cells.get(i);
            Node n;
            if (c instanceof Node node) {
                n = node;
            } else {
                Label l = new Label(String.valueOf(c));
                if (i == 1) l.getStyleClass().add("strong");
                n = l;
            }
            r.add(n, i, 0);
        }
        if (onClick != null) r.setOnMouseClicked(e -> onClick.run());
        getChildren().add(r);
        return r;
    }

    public List<Node> rows() {
        return new ArrayList<>(getChildren().subList(1, getChildren().size()));
    }

    public static Label tag(String text, String kind) {
        Label t = new Label(text);
        t.getStyleClass().addAll("tag", kind);
        return t;
    }

    private GridPane row() {
        GridPane g = new GridPane();
        double total = 0;
        for (double w : weights) total += w;
        for (double w : weights) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(w / total * 100);
            c.setHgrow(Priority.ALWAYS);
            g.getColumnConstraints().add(c);
        }
        g.setAlignment(Pos.CENTER_LEFT);
        g.setHgap(8);
        return g;
    }
}
