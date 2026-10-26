package prosemirrorfx.view;

import com.atlassian.prosemirror.state.Selection;
import java.util.*;
import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import static prosemirrorfx.view.Positions.*;

/**
 * Caret and selection, drawn on a layer above the text in page coordinates. Painted after layout, when
 * CSS has already run for the pulse, so the shapes carry their colours in code (a shape styled by CSS
 * would show black for one frame) and are reused rather than recreated, which is what made dragging flicker.
 */
final class CaretLayer {

    Color caretColor = Theme.LIGHT.color("caret"), selectionColor = Theme.LIGHT.color("selection"), matchColor = Theme.LIGHT.color("match");

    final Pane overlay;
    final Rectangle caret = new Rectangle(1.6, 16, caretColor);
    final List<Rectangle> shades = new ArrayList<>();
    final List<Rectangle> marks = new ArrayList<>();
    final Timeline blink = new Timeline(new KeyFrame(Duration.millis(530), e -> caret.setVisible(!caret.isVisible())));
    Bounds caretBounds;
    int lastHead = -1;

    CaretLayer(Pane overlay) {
        this.overlay = overlay;
        blink.setCycleCount(Animation.INDEFINITE);
        overlay.getChildren().add(caret);
    }

    void paint(Selection sel, Positions positions, boolean focused) {
        var used = sel.getEmpty() ? 0 : highlight(sel, positions);
        for (var k = used; k < shades.size(); k++) shades.get(k).setVisible(false);
        var tb = positions.at(sel.getHead());
        var r = toOverlay(tb, caretRect(tb, sel.getHead() - tb.start));
        caret.relocate(r.getMinX(), r.getMinY());
        caret.setHeight(r.getHeight());
        caretBounds = r;
        if (sel.getHead() != lastHead || !focused || blink.getStatus() != Animation.Status.RUNNING) restartBlink(focused);
        lastHead = sel.getHead();
    }

    int highlight(Selection sel, Positions positions) {
        var n = 0;
        for (var tb : positions.textblocks) {
            if (end(tb) < sel.getFrom() || tb.start > sel.getTo()) continue;
            var a = Math.max(sel.getFrom(), tb.start) - tb.start;
            var b = Math.min(sel.getTo(), end(tb)) - tb.start;
            var rects = tb.flow.getLayoutInfo().getSelectionGeometry(a, b, true);
            if (rects.isEmpty() && sel.getTo() > end(tb)) {
                var c = caretRect(tb, a);
                rects = List.of(new Rectangle2D(c.getMinX(), c.getMinY(), 6, c.getHeight()));
            }
            for (var r : rects) {
                var o = toOverlay(tb, r);
                var shade = shade(n++);
                shade.setX(o.getMinX());
                shade.setY(o.getMinY());
                shade.setWidth(o.getWidth());
                shade.setHeight(o.getHeight());
                shade.setVisible(true);
            }
        }
        return n;
    }

    /** Find matches, shaded under the selection. */
    void paintMatches(List<prosemirrorfx.core.editor.Find.Match> matches, Positions positions) {
        var n = 0;
        for (var m : matches) {
            var tb = positions.at(m.from());
            if (m.to() > end(tb)) continue;
            for (var r : tb.flow.getLayoutInfo().getSelectionGeometry(m.from() - tb.start, m.to() - tb.start, true)) {
                var o = toOverlay(tb, r);
                var s = mark(n++);
                s.setX(o.getMinX());
                s.setY(o.getMinY());
                s.setWidth(o.getWidth());
                s.setHeight(o.getHeight());
                s.setVisible(true);
            }
        }
        for (var k = n; k < marks.size(); k++) marks.get(k).setVisible(false);
    }

    final List<Rectangle> underlines = new ArrayList<>();

    /** Text an input method is still composing (Tamil, Hindi phonetic), underlined; to &lt;= from paints nothing. */
    void paintComposition(int from, int to, Positions positions) {
        var n = 0;
        if (to > from) {
            var tb = positions.at(from);
            for (var r : tb.flow.getLayoutInfo().getSelectionGeometry(from - tb.start, Math.min(to, end(tb)) - tb.start, true)) {
                var o = toOverlay(tb, r);
                while (underlines.size() <= n) {
                    var u = new Rectangle(0, 0, caretColor);
                    u.setManaged(false);
                    underlines.add(u);
                    overlay.getChildren().add(u);
                }
                var u = underlines.get(n++);
                u.setX(o.getMinX());
                u.setY(o.getMaxY() - 2);
                u.setWidth(o.getWidth());
                u.setHeight(1.5);
                u.setVisible(true);
            }
        }
        for (var k = n; k < underlines.size(); k++) underlines.get(k).setVisible(false);
    }

    Rectangle mark(int i) {
        while (marks.size() <= i) {
            var s = new Rectangle(0, 0, matchColor);
            s.setManaged(false);
            marks.add(s);
            overlay.getChildren().add(0, s);
        }
        return marks.get(i);
    }

    Rectangle shade(int i) {
        while (shades.size() <= i) {
            var s = new Rectangle(0, 0, selectionColor);
            s.setManaged(false);
            shades.add(s);
            overlay.getChildren().add(overlay.getChildren().size() - 1, s);
        }
        return shades.get(i);
    }

    Bounds toOverlay(BlockView tb, Rectangle2D r) {
        var scene = tb.flow.localToScene(new BoundingBox(r.getMinX(), r.getMinY(), Math.max(r.getWidth(), 1), r.getHeight()));
        return overlay.sceneToLocal(scene);
    }

    /** New colours for the shapes already drawn; shapes made later use them from the start. */
    void apply(Theme theme) {
        caretColor = theme.color("caret");
        selectionColor = theme.color("selection");
        matchColor = theme.color("match");
        caret.setFill(caretColor);
        underlines.forEach(u -> u.setFill(caretColor));
        shades.forEach(s -> s.setFill(selectionColor));
        marks.forEach(m -> m.setFill(matchColor));
    }

    void restartBlink(boolean focused) {
        blink.stop();
        caret.setVisible(focused);
        if (focused) blink.playFromStart();
    }
}
