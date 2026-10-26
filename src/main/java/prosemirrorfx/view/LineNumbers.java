package prosemirrorfx.view;

import java.util.*;
import javafx.geometry.VPos;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.*;

/**
 * Line numbers in the page's left margin, one per paragraph, heading or list item: a wrapped paragraph is one
 * line and keeps one number, on the baseline of its first drawn line. Painted after layout like the caret,
 * so the numbers are reused shapes with font and colour set in code, never styled by CSS.
 */
final class LineNumbers {

    Font font = Font.font("Segoe UI", 11);
    Color color = Theme.LIGHT.color("number");
    static final double RIGHT_EDGE = 56;

    final Pane gutter;
    final List<Text> pool = new ArrayList<>();

    LineNumbers(Pane gutter) {
        this.gutter = gutter;
    }

    void paint(Positions positions) {
        var n = 0;
        for (var tb : positions.textblocks) {
            var flow = tb.flow;
            var at = gutter.sceneToLocal(flow.localToScene(0, flow.getBaselineOffset()));
            var t = number(n++);
            t.setText(Integer.toString(n));
            t.setX(RIGHT_EDGE - t.getLayoutBounds().getWidth());
            t.setY(at.getY());
            t.setVisible(true);
        }
        for (var k = n; k < pool.size(); k++) pool.get(k).setVisible(false);
    }

    void setScale(double zoom) {
        font = Font.font("Segoe UI", 11 * zoom);
        pool.forEach(t -> t.setFont(font));
    }

    void apply(Theme theme) {
        color = theme.color("number");
        pool.forEach(t -> t.setFill(color));
    }

    void clear() {
        pool.forEach(t -> t.setVisible(false));
    }

    Text number(int i) {
        while (pool.size() <= i) {
            var t = new Text();
            t.setFont(font);
            t.setFill(color);
            t.setTextOrigin(VPos.BASELINE);
            t.setManaged(false);
            pool.add(t);
            gutter.getChildren().add(t);
        }
        return pool.get(i);
    }
}
