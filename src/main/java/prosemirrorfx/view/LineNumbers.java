package prosemirrorfx.view;

import java.util.*;
import javafx.geometry.VPos;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import prosemirrorfx.markdown.SourceMap;

/**
 * Line numbers in the page's left margin: the Markdown file's own source line numbers. Painted after layout like the caret,
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

    /**
     * One number for each source line a block was written as: the first drawn line of the block, and of every line
     * after a hard break. A line the text wraps onto has no number, and a collapsed fence line none either. The
     * numbers are the file's own, so a fold shows as a jump.
     */
    void paint(Positions positions, SourceMap map) {
        var n = 0;
        for (var tb : positions.textblocks) {
            var first = map == null ? -1 : map.firstLine(tb.start);
            if (first < 0) continue;
            var drawn = Motion.drawn(tb);
            var lines = tb.flow.getLayoutInfo().getTextLines(false);
            if (lines.isEmpty()) continue;
            var head = lines.getFirst().bounds();
            var ascent = head.getHeight() < 6 ? 0.62 : (tb.flow.getBaselineOffset() - head.getMinY()) / head.getHeight();
            var sourceLine = first - 1;
            for (var line : lines) {
                var at = line.start();
                if (at > 0 && (at > drawn.length() || drawn.charAt(at - 1) != '\n')) continue;
                sourceLine++;
                if (line.bounds().getHeight() < 6) continue;
                var baseline = line.bounds().getMinY() + ascent * line.bounds().getHeight();
                var y = gutter.sceneToLocal(tb.flow.localToScene(0, baseline)).getY();
                var t = number(n++);
                t.setText(Integer.toString(sourceLine + 1));
                t.setX(RIGHT_EDGE - t.getLayoutBounds().getWidth());
                t.setY(y);
                t.setVisible(true);
            }
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
