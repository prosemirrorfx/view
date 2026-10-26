package prosemirrorfx.view;

import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;

import static prosemirrorfx.core.commands.Commands.*;

/**
 * The block style menu (the "Normal text" drop-down of Docs, Confluence, CKEditor): the toolbar button opens it
 * under itself; Ctrl+Alt+0..6 set a style directly. The menu has no text input, so bare digits 0..6 pick a style; arrows
 * and Enter also work, Esc closes it.
 */
final class BlockMenu {

    static final String[] NAMES = {"Normal text", "Heading 1", "Heading 2", "Heading 3", "Heading 4", "Heading 5", "Heading 6"};
    static final double[] SIZES = {15, 26, 21, 17, 15, 14, 13};

    static ContextMenu showing;

    static void open(DocView view, Node anchor) {
        if (showing != null) showing.hide();
        var state = view.model.state();
        var current = blockLevel(state);
        var menu = new ContextMenu();
        for (var level = 0; level < NAMES.length; level++) {
            var l = level;
            var name = new Label(level + "   " + NAMES[level]);
            name.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: " + SIZES[level] + "px;" + (level > 0 ? " -fx-font-weight: bold;" : ""));
            var item = new CustomMenuItem(name, true);
            item.setDisable(level != current && !setBlock(level).applies(state));
            if (level == current) item.getStyleClass().add("pm-current");
            item.setOnAction(e -> pick(view, l));
            menu.getItems().add(item);
        }
        menu.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            var d = e.getCode().isDigitKey() ? e.getText() : "";
            if (d.length() == 1 && d.charAt(0) >= '0' && d.charAt(0) <= '6' && !menu.getItems().get(d.charAt(0) - '0').isDisable()) {
                menu.hide();
                pick(view, d.charAt(0) - '0');
                e.consume();
            }
        });
        menu.setOnHidden(e -> { if (showing == menu) showing = null; });
        showing = menu;
        if (anchor != null) {
            menu.show(anchor, Side.BOTTOM, 0, 0);
        } else {
            var b = view.caretLayer.caretBounds;
            var at = b == null ? view.localToScreen(20, 20) : view.overlay.localToScreen(b.getMinX(), b.getMaxY() + 4);
            menu.show(view, at.getX(), at.getY());
        }
    }

    static void pick(DocView view, int level) {
        view.model.run(setBlock(level));
        view.requestFocus();
    }

    private BlockMenu() {}
}
