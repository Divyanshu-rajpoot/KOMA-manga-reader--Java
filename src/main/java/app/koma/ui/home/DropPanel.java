package app.koma.ui.home;

import app.koma.AppContext;
import app.koma.ui.shared.SpeedBurst;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** "DROP CBZ FILES HERE!" — inverted panel with a speed-line burst. Click opens the file picker. */
final class DropPanel extends StackPane {
    DropPanel(AppContext ctx) {
        getStyleClass().add("drop-panel");
        SpeedBurst burst = new SpeedBurst(0.16);
        Label kicker = new Label("IMPORT");
        kicker.getStyleClass().addAll("display-s", "drop-kicker");
        Label l1 = new Label("DROP CBZ");
        l1.getStyleClass().add("drop-big");
        Label l2 = new Label("FILES ");
        l2.getStyleClass().add("drop-big");
        Label l3 = new Label("HERE!");
        l3.getStyleClass().addAll("drop-big", "accent-text");
        HBox line2 = new HBox(l2, l3);
        Label sub = new Label("Or a whole folder. \"Title v03.cbz\" is filed as volume 3 of Title automatically, at any file size.");
        sub.getStyleClass().add("small");
        sub.setWrapText(true);
        VBox text = new VBox(2, kicker, l1, line2, sub);
        VBox.setMargin(sub, new javafx.geometry.Insets(10, 0, 0, 0));
        getChildren().addAll(burst, text);
        setOnMouseClicked(e -> ctx.window.nav().chooseFiles());
        setStyle("-fx-cursor: hand;");
    }
}
