package app.koma.ui.shell;

import app.koma.AppContext;
import app.koma.ui.library.LibraryView;
import app.koma.ui.shared.Icons;
import app.koma.ui.shared.LogoMark;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;

/** Logo, Home / Library / Import tabs, search (Ctrl K), theme + settings, "+ Add CBZ". */
public final class NavBar extends HBox {
    private final AppContext ctx;
    private final ToggleButton home = tab("Home");
    private final ToggleButton library = tab("Library");
    private final ToggleButton importTab = tab("Import");
    private final TextField search = new TextField();
    private final Button themeButton = new Button();

    public NavBar(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("navbar");
        setAlignment(Pos.CENTER_LEFT);

        // logo: red square with コ + KOMA
        LogoMark mark = new LogoMark(28);
        Label word = new Label("KOMA");
        word.getStyleClass().add("logo-word");
        HBox logo = new HBox(8, mark, word);
        logo.setAlignment(Pos.CENTER_LEFT);
        logo.getStyleClass().add("logo");
        logo.setOnMouseClicked(e -> ctx.router.navigate(Screen.HOME, null));

        ToggleGroup g = new ToggleGroup();
        for (ToggleButton t : List.of(home, library, importTab)) t.setToggleGroup(g);
        // clicking the active tab again keeps it active
        g.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null && old != null && ctx.router != null && isTabScreen(ctx.router.currentScreen(), old)) old.setSelected(true);
        });
        home.setOnAction(e -> go(Screen.HOME));
        library.setOnAction(e -> go(Screen.LIBRARY));
        importTab.setOnAction(e -> go(Screen.IMPORT));
        HBox tabs = new HBox(4, home, library, importTab);
        tabs.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        search.setPromptText("Search series, volumes, files");
        search.getStyleClass().add("nav-search");
        Label hint = new Label("Ctrl K");
        hint.getStyleClass().add("kbd");
        Region icon = Icons.of("search", 14);
        StackPane searchBox = new StackPane(search, icon, hint);
        StackPane.setAlignment(icon, Pos.CENTER_LEFT);
        StackPane.setAlignment(hint, Pos.CENTER_RIGHT);
        icon.setTranslateX(14);
        hint.setTranslateX(-12);
        hint.setMouseTransparent(true);
        searchBox.getStyleClass().add("search-box");
        searchBox.setPrefWidth(300);
        search.textProperty().addListener((o, a, b) -> onSearch(b));
        search.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                search.clear();
                getParent().requestFocus();
            }
        });

        themeButton.getStyleClass().add("icon-button");
        themeButton.setFocusTraversable(false);
        themeButton.setTooltip(new Tooltip("Light / dark (Ctrl Shift L)"));
        themeButton.setOnAction(e -> {
            ctx.theme.toggle();
            updateThemeIcon();
        });

        Button settings = new Button();
        settings.setGraphic(Icons.of("settings", 16));
        settings.getStyleClass().add("icon-button");
        settings.setFocusTraversable(false);
        settings.setTooltip(new Tooltip("Settings (Ctrl ,)"));
        settings.setOnAction(e -> go(Screen.SETTINGS));

        Button add = new Button("Add CBZ");
        add.setGraphic(Icons.of("plus", 14));
        add.getStyleClass().add("accent-button");
        add.setOnAction(e -> chooseFiles());

        getChildren().addAll(logo, tabs, spacer, searchBox, themeButton, settings, add);
        HBox.setMargin(tabs, new javafx.geometry.Insets(0, 0, 0, 28));
        setSpacing(12);
    }

    private void go(Screen s) {
        if (s != Screen.LIBRARY) search.clear();
        ctx.router.navigate(s, s == Screen.LIBRARY ? search.getText() : null);
    }

    private void onSearch(String q) {
        if (ctx.router.currentView() instanceof LibraryView lv) {
            lv.setQuery(q);
        } else if (q != null && !q.isBlank()) {
            ctx.router.navigate(Screen.LIBRARY, q);
            search.requestFocus();
            search.end();
        }
    }

    public void chooseFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Add CBZ files");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Comic archives", "*.cbz", "*.zip", "*.cbr"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        List<File> files = fc.showOpenMultipleDialog(ctx.stage);
        if (files != null && !files.isEmpty()) {
            ctx.router.navigate(Screen.IMPORT, files.stream().map(File::toPath).toList());
        }
    }

    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    public void setActive(Screen s) {
        home.setSelected(s == Screen.HOME);
        library.setSelected(s == Screen.LIBRARY || s == Screen.SERIES);
        importTab.setSelected(s == Screen.IMPORT);
        if (s != Screen.LIBRARY && s != Screen.SERIES && !search.getText().isEmpty()) {
            search.setText("");
        }
    }

    public void updateThemeIcon() {
        themeButton.setGraphic(Icons.of(ctx.theme.isDark() ? "sun" : "moon", 16));
    }

    private boolean isTabScreen(Screen s, javafx.scene.control.Toggle t) {
        return (t == home && s == Screen.HOME) || (t == library && (s == Screen.LIBRARY || s == Screen.SERIES))
                || (t == importTab && s == Screen.IMPORT);
    }

    private static ToggleButton tab(String text) {
        ToggleButton t = new ToggleButton(text);
        t.getStyleClass().add("nav-tab");
        t.setFocusTraversable(false);
        return t;
    }
}
