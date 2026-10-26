package prosemirrorfx.view;

import javafx.scene.text.TextFlow;

/**
 * A TextFlow that remembers its measured size until its content changes. JavaFX 26.0.2's TextFlow answers every
 * prefHeight(width) and minHeight(width) by setting its wrap width, shaping the whole text, and setting it back (a
 * second shaping), with no cache; a VBox asks each child that two or three times per layout pass. So one keystroke
 * re-shaped every paragraph of the document: about 1 s per key in a 33 KB note of Tamil text (DirectWrite shaping is
 * slow), 120 ms in a 195 KB English one. Any change to the text, its fonts, CSS or children reaches requestLayout(),
 * which forgets the sizes; the requestLayout calls TextFlow makes on itself while laying out are ignored, as it does.
 */
final class Flow extends TextFlow {
    // two widths: the one the flow is laid out at and USE_COMPUTED_SIZE, which is what containers ask for
    private double w1 = Double.NaN, h1, w2 = Double.NaN, h2, prefWidth = Double.NaN;
    private boolean laying;
    // how many measurements missed the cache and shaped the text: read by the tests
    static int shaped;

    // CSS and lookups see it as the TextFlow it is
    @Override public String getTypeSelector() { return "TextFlow"; }

    @Override protected double computePrefHeight(double width) {
        if (width == w1) return h1;
        if (width == w2) return h2;
        shaped++;
        var h = super.computePrefHeight(width);
        w2 = w1; h2 = h1;
        w1 = width; h1 = h;
        return h;
    }

    @Override protected double computeMinHeight(double width) { return computePrefHeight(width); }

    @Override protected double computePrefWidth(double height) {
        if (Double.isNaN(prefWidth)) prefWidth = super.computePrefWidth(height);
        return prefWidth;
    }

    @Override public void requestLayout() {
        if (!laying) { w1 = w2 = prefWidth = Double.NaN; }
        super.requestLayout();
    }

    @Override protected void layoutChildren() {
        laying = true;
        try { super.layoutChildren(); } finally { laying = false; }
    }
}
