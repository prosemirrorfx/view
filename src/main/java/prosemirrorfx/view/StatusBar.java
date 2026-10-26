package prosemirrorfx.view;

import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import prosemirrorfx.core.editor.Stats;

/**
 * The status line along the bottom: the host's own items first (if any), then "1,204 words · 12 selected · notes.md ·
 * saved 10:42", a passing notice ("Copied all as formatted"), and the zoom on the right: − [100%] +, where the box
 * takes any number from 25 to 400. Place it with borderPane.bottom(StatusBar.of(view)).
 */
public final class StatusBar {

    static final String[] ZOOMS = {"50%", "75%", "90%", "100%", "110%", "125%", "150%", "200%", "300%"};

    public static Node of(DocView view, Node... leading) {
        var text = new Label();
        text.getStyleClass().add("pm-status");
        var notice = new Label();
        notice.getStyleClass().addAll("pm-status", "pm-notice");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var bar = new HBox();
        bar.getChildren().addAll(leading);
        bar.getChildren().addAll(text, notice, spacer, zoom(view));
        bar.getStyleClass().add("pm-statusbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStylesheets().add(StatusBar.class.getResource("editor.css").toExternalForm());
        var words = new int[] {-1};
        Runnable show = () -> {
            var state = view.model.state();
            var doc = state.getDoc();
            if (words[0] < 0) words[0] = Stats.words(doc, 0, doc.getContent().getSize());
            var sel = state.getSelection();
            var s = String.format("%,d words", words[0]);
            if (!sel.getEmpty()) s += String.format(" · %,d selected", Stats.words(doc, sel.getFrom(), sel.getTo()));
            if (!view.fileStatus.get().isEmpty()) s += " · " + view.fileStatus.get();
            text.setText(s);
        };
        view.model.onChange((old, now) -> {
            if (old.getDoc() != now.getDoc()) words[0] = -1;
            show.run();
        });
        view.fileStatus.addListener((o, a, b) -> show.run());
        var fade = new PauseTransition(Duration.seconds(3));
        fade.setOnFinished(e -> notice.setText(""));
        view.notice.addListener((o, a, b) -> {
            if (b.isEmpty()) return;
            notice.setText("·  " + b);
            fade.playFromStart();
        });
        show.run();
        view.follow(bar);
        return bar;
    }

    /** − [100%] +: the buttons step by 10% as Ctrl+- and Ctrl+= do; the box takes any number, Enter applies it. */
    static Node zoom(DocView view) {
        var out = tool("remove", "Zoom out (Ctrl+-)", () -> view.zoomStep(-1));
        var in = tool("add", "Zoom in (Ctrl+=)", () -> view.zoomStep(1));
        var box = new ComboBox<String>();
        box.getItems().addAll(ZOOMS);
        box.setEditable(true);
        box.setVisibleRowCount(ZOOMS.length);
        box.getStyleClass().add("pm-zoom");
        box.setTooltip(new Tooltip("Zoom: type any size from 25% to 400% and press Enter (Ctrl+0 resets to 100%)"));
        var updating = new boolean[1];
        Runnable shown = () -> {
            updating[0] = true;
            box.setValue(Math.round(view.zoom() * 100) + "%");
            updating[0] = false;
        };
        // a ComboBox fires its action whenever its value is set, from code too: only the user's choice counts
        box.setOnAction(e -> {
            if (updating[0]) return;
            var v = parse(box.getValue());
            if (v != null) view.setZoom(v / 100);
            shown.run();
            view.requestFocus();
        });
        box.getEditor().addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) { shown.run(); view.requestFocus(); e.consume(); }
        });
        view.zoomProperty().addListener((o, a, b) -> shown.run());
        shown.run();
        var group = new HBox(2, out, box, in);
        group.setAlignment(Pos.CENTER_RIGHT);
        return group;
    }

    /** "125", "125%", " 125 % " -> 125; anything else -> null. */
    static Double parse(String s) {
        if (s == null) return null;
        try {
            return Double.parseDouble(s.replace("%", "").strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Button tool(String icon, String tip, Runnable act) {
        var b = new Button();
        b.setGraphic(ToolbarIcons.of(icon));
        b.setTooltip(new Tooltip(tip));
        b.setFocusTraversable(false);
        b.getStyleClass().addAll("pm-tool", "pm-zoom-step");
        b.setOnAction(e -> act.run());
        return b;
    }

    private StatusBar() {}
}
