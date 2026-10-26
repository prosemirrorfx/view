package prosemirrorfx.view;

import java.util.*;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import static prosemirrorfx.core.commands.Commands.blockLevel;

/**
 * The formatting toolbar along the top, as ProseMirror's menu bar, CKEditor and Google Docs have it:
 * undo/redo | paragraph style | bold italic | lists | indent | clear | find, and the panel toggles on the right.
 * Buttons never take the focus, so the caret stays in the text; each tooltip names the standard shortcut.
 * Pressed and greyed-out states follow the caret on every change. Place it with borderPane.top(Toolbar.of(view)).
 */
public final class Toolbar {

    /** A group ends after these; CONTENTS starts the right-hand side. */
    static final Set<EditorAction> GROUP_ENDS = EnumSet.of(EditorAction.REDO, EditorAction.STYLE, EditorAction.ITALIC,
            EditorAction.NUMBERS, EditorAction.INDENT, EditorAction.CLEAR);

    public static Node of(DocView view) {
        var bar = new HBox(2);
        bar.getStyleClass().add("pm-toolbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStylesheets().add(Toolbar.class.getResource("editor.css").toExternalForm());
        var controls = new EnumMap<EditorAction, ButtonBase>(EditorAction.class);
        for (var a : EditorAction.values()) {
            if (!a.available(view)) continue;
            if (a == EditorAction.CONTENTS || (a == EditorAction.LINES && !EditorAction.CONTENTS.available(view))) {
                var spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                bar.getChildren().add(spacer);
            }
            var b = button(view, a);
            controls.put(a, b);
            bar.getChildren().add(b);
            if (GROUP_ENDS.contains(a)) bar.getChildren().add(new Separator(Orientation.VERTICAL));
        }
        Runnable refresh = () -> controls.forEach((a, b) -> {
            b.setDisable(!a.enabled(view));
            if (b instanceof ToggleButton t) t.setSelected(a.active(view));
            if (a == EditorAction.STYLE) b.setText(BlockMenu.NAMES[blockLevel(view.model.state())]);
        });
        view.model.onChange((old, now) -> refresh.run());
        if (view.toc != null) view.toc.visibleProperty().addListener((o, a, b) -> refresh.run());
        view.lineNumbersProperty().addListener((o, a, b) -> refresh.run());
        view.activeLineProperty().addListener((o, a, b) -> refresh.run());
        refresh.run();
        return bar;
    }

    static ButtonBase button(DocView view, EditorAction a) {
        ButtonBase b = a.toggle() ? new ToggleButton() : a == EditorAction.COPY_ALL ? copyAll(view) : new Button();
        b.setMnemonicParsing(false);
        b.setFocusTraversable(false);
        b.setTooltip(new Tooltip(a == EditorAction.COPY_ALL ? "Copy all, formatted (arrow: other formats)" : a.tooltip()));
        b.getStyleClass().add("pm-tool");
        var icon = ToolbarIcons.of(a.icon);
        if (a == EditorAction.STYLE) {
            b.getStyleClass().add("pm-style");
            b.setGraphic(ToolbarIcons.of("arrow_drop_down"));
            b.setContentDisplay(ContentDisplay.RIGHT);
        } else if (icon != null) {
            b.setGraphic(icon);
        } else {
            b.setText(a.label);
        }
        b.setOnAction(e -> {
            a.act.accept(view, b);
            // a toggle's pressed state is the document's, not the click's
            if (b instanceof ToggleButton t) t.setSelected(a.active(view));
            if (a.refocus()) view.requestFocus();
        });
        return b;
    }

    /** Copy all: a click copies formatted, the arrow offers each format. */
    static SplitMenuButton copyAll(DocView view) {
        var b = new SplitMenuButton();
        b.getStyleClass().add("pm-split");
        for (var f : CopyFormat.values()) {
            var item = new MenuItem(f.label);
            item.setMnemonicParsing(false);
            item.setOnAction(e -> { view.copyAll(f); view.requestFocus(); });
            b.getItems().add(item);
        }
        return b;
    }

    private Toolbar() {}
}
