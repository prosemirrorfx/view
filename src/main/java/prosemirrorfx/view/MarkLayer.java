package prosemirrorfx.view;

import java.util.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polyline;

import static prosemirrorfx.view.Positions.*;

/**
 * Wavy underlines under ranges a checker found (a misspelling, a grammar slip), one per line the range covers.
 * Not part of the document: the ranges follow edits, and one whose text is edited is dropped (see DocView.setMarks).
 */
final class MarkLayer {

    final Pane overlay;
    final List<Polyline> lines = new ArrayList<>();
    Map<String, Color> colours = colours(Theme.LIGHT);

    MarkLayer(Pane overlay) { this.overlay = overlay; }

    void paint(List<DocView.Mark> marks, Positions positions, LiveLayer at) {
        var n = 0;
        if (positions != null && !positions.textblocks.isEmpty()) for (var m : marks) {
            var tb = positions.at(m.from());
            if (m.to() > end(tb) || !shown(tb.flow)) continue;
            for (var r : tb.flow.getLayoutInfo().getSelectionGeometry(m.from() - tb.start, m.to() - tb.start, true)) {
                var o = at.toOverlay(tb, r);
                var line = line(n++);
                squiggle(line, o.getMinX(), o.getMaxX(), o.getMaxY() - 1.5);
                line.setStroke(colours.getOrDefault(m.kind(), colours.get("grammar")));
                line.setVisible(true);
            }
        }
        for (var k = n; k < lines.size(); k++) lines.get(k).setVisible(false);
    }

    /** False inside a folded section, whose blocks keep their old layout while hidden. */
    static boolean shown(javafx.scene.Node n) {
        for (; n != null; n = n.getParent()) if (!n.isVisible()) return false;
        return true;
    }

    Polyline line(int i) {
        while (lines.size() <= i) {
            var p = new Polyline();
            p.setManaged(false);
            p.setStrokeWidth(1.2);
            lines.add(p);
            overlay.getChildren().add(p);
        }
        return lines.get(i);
    }

    static void squiggle(Polyline p, double x0, double x1, double y) {
        var pts = p.getPoints();
        pts.clear();
        var up = true;
        for (var x = x0; x <= x1; x += 2) {
            pts.addAll(x, up ? y - 1.5 : y + 0.5);
            up = !up;
        }
    }

    void apply(Theme theme) { colours = colours(theme); }

    static Map<String, Color> colours(Theme t) {
        return Map.of("spelling", t.color("mark-spelling"), "grammar", t.color("mark-grammar"), "style", t.color("mark-style"));
    }
}
