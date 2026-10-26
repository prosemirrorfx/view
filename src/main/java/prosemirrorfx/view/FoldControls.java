package prosemirrorfx.view;

import java.util.*;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;

/**
 * The fold control beside each block that has something to fold: a small triangle in the left margin, pointing down
 * while the block is open and right while it is folded. Painted after layout, like the caret, from a pool of labels
 * that are reused. A click folds or unfolds; the label takes the press so the caret stays where it is.
 */
final class FoldControls {

    final DocView view;
    final Pane front;
    final List<Label> pool = new ArrayList<>();

    FoldControls(DocView view, Pane front) {
        this.view = view;
        this.front = front;
    }

    void paint(List<BlockView> views, Folds folds) {
        var used = new int[1];
        place(views, folds, used);
        for (var k = used[0]; k < pool.size(); k++) pool.get(k).setVisible(false);
    }

    void place(List<BlockView> views, Folds folds, int[] used) {
        for (var v : views) {
            if (v.hidden) continue;
            var pos = v.start - 1;
            if (folds.foldableSet.contains(pos)) show(v, pos, folds.folded.contains(pos), used[0]++);
            place(v.children, folds, used);
        }
    }

    void show(BlockView v, int pos, boolean folded, int slot) {
        var control = control(slot);
        control.setText(folded ? "▸" : "▾");
        control.setUserData(pos);
        control.setVisible(true);
        control.applyCss();
        control.autosize();
        var at = v.marker != null ? beside(v.marker) : beside(v);
        control.relocate(at[0] - control.getWidth() - 2, at[1] - control.getHeight() / 2);
    }

    /** The left edge of a list item's bullet or number (the marker is right-aligned in a wider column) and the middle of its first line, in the front layer. */
    double[] beside(Label marker) {
        var b = front.sceneToLocal(marker.localToScene(marker.getLayoutBounds()));
        var text = new javafx.scene.text.Text(marker.getText());
        text.setFont(marker.getFont());
        return new double[]{b.getMaxX() - text.getLayoutBounds().getWidth() - 3, b.getCenterY()};
    }

    /** The left edge of a heading and the middle of its first drawn line. */
    double[] beside(BlockView heading) {
        var lines = heading.flow.getLayoutInfo().getTextLines(false);
        var y = lines.isEmpty() ? heading.flow.getLayoutBounds().getCenterY() : (lines.getFirst().bounds().getMinY() + lines.getFirst().bounds().getMaxY()) / 2;
        var p = front.sceneToLocal(heading.flow.localToScene(0, y));
        return new double[]{p.getX(), p.getY()};
    }

    Label control(int slot) {
        while (pool.size() <= slot) {
            var l = new Label();
            l.getStyleClass().add("pm-fold");
            l.setManaged(false);
            l.setFocusTraversable(false);
            l.setMnemonicParsing(false);
            l.setOnMousePressed(e -> e.consume());
            l.setOnMouseClicked(e -> { view.toggleFold((Integer) l.getUserData()); e.consume(); });
            pool.add(l);
            front.getChildren().add(l);
        }
        return pool.get(slot);
    }
}
