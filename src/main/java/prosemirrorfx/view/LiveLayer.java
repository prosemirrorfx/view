package prosemirrorfx.view;

import javafx.geometry.*;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import static prosemirrorfx.view.Positions.*;

/**
 * Live sources drawn over the text: the word being read aloud with a band across its line, and words still being
 * dictated shown grey at the caret. Neither is part of the document. Painted after layout, like the caret.
 */
final class LiveLayer {

    final Pane overlay;
    final Rectangle line = rect(), word = rect();
    final Label interim = new Label();
    int from = -1, to = -1;
    boolean estimated;
    String interimText = "";
    Color lineColor = Theme.LIGHT.color("reading-line"), wordColor = Theme.LIGHT.color("reading-word");
    Bounds wordBounds;
    Theme theme = Theme.LIGHT;

    LiveLayer(Pane overlay) {
        this.overlay = overlay;
        interim.getStyleClass().add("pm-interim-caret");
        interim.setManaged(false);
        interim.setWrapText(true);
        interim.setVisible(false);
        overlay.getChildren().addAll(0, java.util.List.of(line, word));
        overlay.getChildren().add(interim);
    }

    static Rectangle rect() {
        var r = new Rectangle(0, 0, Color.TRANSPARENT);
        r.setManaged(false);
        r.setVisible(false);
        return r;
    }

    void paint(Positions positions, CaretLayer caret, double pageWidth) {
        paintReading(positions, pageWidth);
        paintInterim(caret, pageWidth, positions != null && caret.lastHead >= 0 && caret.lastHead >= end(positions.at(caret.lastHead)));
    }

    void paintReading(Positions positions, double pageWidth) {
        line.setVisible(false);
        word.setVisible(false);
        wordBounds = null;
        if (from < 0 || positions == null || positions.textblocks.isEmpty()) return;
        var tb = positions.at(from);
        var a = Math.max(0, from - tb.start);
        var b = Math.max(a, Math.min(to, end(tb)) - tb.start);
        var rects = tb.flow.getLayoutInfo().getSelectionGeometry(a, b, true);
        var r = rects.isEmpty() ? caretRect(tb, a) : rects.getFirst();
        var o = toOverlay(tb, r);
        wordBounds = o;
        var flow = overlay.sceneToLocal(tb.flow.localToScene(tb.flow.getLayoutBounds()));
        place(line, flow.getMinX() - 4, o.getMinY(), flow.getWidth() + 8, o.getHeight(), lineColor);
        if (b > a) place(word, o.getMinX() - 1, o.getMinY(), o.getWidth() + 2, o.getHeight(), estimated ? wordColor.deriveColor(0, 1, 1, 0.6) : wordColor);
    }

    /** At the end of a line the words run on from the caret; mid-line they float just under it, hiding nothing of the line. */
    void paintInterim(CaretLayer caret, double pageWidth, boolean atLineEnd) {
        var c = caret.caretBounds;
        if (interimText.isEmpty() || c == null) { interim.setVisible(false); return; }
        interim.setText(interimText);
        interim.pseudoClassStateChanged(FLOATING, !atLineEnd);
        interim.setStyle("-fx-background-color: " + theme.colour(atLineEnd ? "bg" : "panel") + "; -fx-text-fill: " + theme.colour("interim") + ";");
        interim.applyCss();
        var x = atLineEnd ? c.getMaxX() + 2 : Math.max(0, c.getMinX() - 4);
        var w = Math.min(interim.prefWidth(-1), Math.max(120, pageWidth - x - 16));
        interim.resize(w, interim.prefHeight(w));
        interim.relocate(x, atLineEnd ? c.getMinY() : c.getMaxY() + 2);
        interim.setVisible(true);
    }

    static final javafx.css.PseudoClass FLOATING = javafx.css.PseudoClass.getPseudoClass("floating");

    static void place(Rectangle r, double x, double y, double w, double h, Color fill) {
        r.setX(x);
        r.setY(y);
        r.setWidth(w);
        r.setHeight(h);
        r.setFill(fill);
        r.setVisible(true);
    }

    Bounds toOverlay(BlockView tb, Rectangle2D r) {
        var scene = tb.flow.localToScene(new BoundingBox(r.getMinX(), r.getMinY(), Math.max(r.getWidth(), 1), r.getHeight()));
        return overlay.sceneToLocal(scene);
    }

    void apply(Theme theme) {
        this.theme = theme;
        lineColor = theme.color("reading-line");
        wordColor = theme.color("reading-word");
    }
}
