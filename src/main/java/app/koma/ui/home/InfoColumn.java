package app.koma.ui.home;

import app.koma.AppContext;
import app.koma.core.Anim;
import app.koma.library.Series;
import app.koma.library.Volume;
import app.koma.ui.shared.Fmt;
import app.koma.ui.shared.Icons;
import app.koma.ui.shared.SegmentProgress;
import app.koma.ui.shell.Screen;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import javafx.util.Duration;

import java.util.List;

/** Left half of the Home band: "NOW READING", giant №07, title, meta, 20-block bar, Resume / Open series. */
final class InfoColumn extends VBox {
    private final AppContext ctx;
    private final List<Series> all;
    private final Label kicker = new Label();
    private final Text numSign = new Text("№");
    private final Text number = new Text();
    private final HBox numberRow = new HBox(0, numSign, number);
    private final Label title = new Label();
    private final Label meta = new Label();
    private final SegmentProgress bar = new SegmentProgress(10);
    private final Label pageLine = new Label();
    private final Button resume = new Button("RESUME");
    private final Button open = new Button("OPEN SERIES");
    private Series series;

    InfoColumn(AppContext ctx, List<Series> all) {
        this.ctx = ctx;
        this.all = all;
        getStyleClass().add("info-column");
        setSpacing(6);
        kicker.getStyleClass().add("mono");
        numSign.getStyleClass().addAll("display-xl", "display-text");
        number.getStyleClass().addAll("display-xl", "display-text", "accent-fill");
        numSign.setBoundsType(TextBoundsType.VISUAL);
        number.setBoundsType(TextBoundsType.VISUAL);
        numberRow.setAlignment(Pos.BOTTOM_LEFT);
        numberRow.setSpacing(6);
        VBox.setMargin(numberRow, new javafx.geometry.Insets(14, 0, 10, 0));
        title.getStyleClass().add("display-l");
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMaxWidth(Double.MAX_VALUE);
        meta.getStyleClass().add("mono");
        meta.setTextOverrun(OverrunStyle.ELLIPSIS);
        pageLine.getStyleClass().add("mono-s");
        bar.setMaxWidth(440);

        resume.getStyleClass().add("accent-button");
        resume.setGraphic(Icons.of("play", 14));
        resume.setOnAction(e -> {
            Volume v = series.current();
            if (v != null) ctx.router.navigate(Screen.READER, v.id);
        });
        open.getStyleClass().add("outline-button");
        open.setOnAction(e -> ctx.router.navigate(Screen.SERIES, series.id));
        HBox buttons = new HBox(12, resume, open);
        VBox.setMargin(buttons, new javafx.geometry.Insets(16, 0, 0, 0));
        VBox.setMargin(bar, new javafx.geometry.Insets(10, 0, 0, 0));

        getChildren().addAll(kicker, numberRow, title, meta, bar, pageLine, buttons);
    }

    void show(Series s, boolean animate) {
        this.series = s;
        Volume v = s.current();
        int pos = all.indexOf(s) + 1;
        String state = v != null && v.isStarted() ? "NOW READING" : "UP NEXT";
        kicker.setText(state + " · " + Fmt.upper(s.type) + " · SERIES " + Fmt.pad3(pos) + " / " + Fmt.pad3(all.size()));
        number.setText(v == null ? "00" : v.label());
        title.setText(Fmt.upper(s.title));
        StringBuilder m = new StringBuilder();
        if (s.titleJp != null) m.append(s.titleJp).append(" / ");
        m.append(s.volumes.size()).append(s.volumes.size() == 1 ? " VOL" : " VOLS")
                .append(" / ").append(Fmt.bytes(s.totalSize()))
                .append(" / ").append(s.directionLabel());
        meta.setText(m.toString());
        if (v != null) {
            int page = v.progress == null ? 0 : v.progress.page + 1;
            int pct = (int) Math.round(v.fraction() * 100);
            pageLine.setText("P." + page + " / " + v.pages + " — " + pct + "% OF VOL " + Volume.num(v.number).replaceFirst("^0", ""));
            bar.set(v.fraction(), animate);
            resume.setText(v.isStarted() && !v.isFinished() ? "RESUME" : "READ");
        }
        if (animate) {
            Anim.slam(numberRow, Duration.ZERO);
            Anim.riseIn(title, Duration.millis(60));
            Anim.riseIn(meta, Duration.millis(100));
        }
    }
}
