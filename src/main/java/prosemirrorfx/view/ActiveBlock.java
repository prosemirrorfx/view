package prosemirrorfx.view;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import static prosemirrorfx.core.commands.Commands.blockLevel;

/**
 * The block holding the caret: an optional gentle band behind the whole of it (every wrapped line of a long
 * paragraph is one line), and a small block-type control in the left margin beside its first line, as Atlaskit's
 * block controls have it. The control opens the same style menu as the toolbar's drop-down. It never takes the
 * focus, so the caret stays where it is. Painted after layout, like the caret.
 */
final class ActiveBlock {

    final DocView view;
    final Pane back, front;
    final Region band = new Region();
    final Button handle = new Button();
    boolean highlight;

    ActiveBlock(DocView view, Pane back, Pane front) {
        this.view = view;
        this.back = back;
        this.front = front;
        band.getStyleClass().add("pm-active-line");
        band.setManaged(false);
        band.setVisible(false);
        back.getChildren().add(band);
        handle.getStyleClass().add("pm-block-handle");
        handle.setManaged(false);
        handle.setFocusTraversable(false);
        handle.setMnemonicParsing(false);
        handle.setTooltip(new Tooltip("Paragraph style (Ctrl+Alt+0..6)"));
        handle.setOnAction(e -> BlockMenu.open(view, handle));
        handle.setVisible(false);
        front.getChildren().add(handle);
    }

    void paint(Positions positions) {
        var none = positions == null || positions.textblocks.isEmpty();
        band.setVisible(!none && highlight);
        handle.setVisible(!none);
        if (none) return;
        var state = view.model.state();
        var tb = positions.at(state.getSelection().getHead());
        var flow = tb.flow;
        var b = back.sceneToLocal(flow.localToScene(flow.getLayoutBounds()));
        band.resizeRelocate(0, b.getMinY() - 3, view.page.getWidth(), b.getHeight() + 6);
        var level = blockLevel(state);
        handle.setText(level == 0 ? "¶" : "H" + level);
        handle.applyCss();
        handle.autosize();
        var lines = flow.getLayoutInfo().getTextLines(false);
        var first = lines.isEmpty() ? null : lines.getFirst().bounds();
        var y = first == null ? flow.getLayoutBounds().getCenterY() : (first.getMinY() + first.getMaxY()) / 2;
        var mid = front.sceneToLocal(flow.localToScene(0, y)).getY();
        handle.relocate(view.page.getPadding().getLeft() - handle.getWidth() - 16, mid - handle.getHeight() / 2);
    }
}
