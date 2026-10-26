package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import java.util.List;
import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import prosemirrorfx.core.editor.EditorModel;

/**
 * The editor control: the document drawn as JavaFX nodes on a scrolling page, a caret layer above it,
 * and keyboard and mouse turned into transactions. Views are reconciled, not rebuilt (see Reconciler).
 */
public final class DocView extends StackPane {

    final EditorModel model;
    final VBox blocks = new VBox();
    final Pane overlay = new Pane();
    final Pane gutter = new Pane();
    /** Behind the text (the active-line band) and in front of it (the block-type control, which takes clicks). */
    final Pane back = new Pane(), front = new Pane();
    final StackPane page = new StackPane(back, blocks, gutter, overlay, front);
    final ScrollPane scroll = new ScrollPane(page);
    final CaretLayer caretLayer = new CaretLayer(overlay);
    List<BlockView> views = List.of();
    Positions positions;
    final LineNumbers lineNumbers = new LineNumbers(gutter);
    final ActiveBlock active = new ActiveBlock(this, back, front);
    boolean numbered;
    /** Set by the TOC when it is used: the next reveal puts the caret line near the top. */
    boolean revealAtTop;
    Toc toc;
    final FindBar findBar;
    final Ime ime;
    /** Find matches to shade; empty when the find bar is closed. */
    List<prosemirrorfx.core.editor.Find.Match> highlights = List.of();
    double zoom = 1;
    FileSession file;
    /** What the attached FileSession reports ("notes.md · saved 10:42"); empty without one. */
    final javafx.beans.property.StringProperty fileStatus = new javafx.beans.property.SimpleStringProperty("");
    boolean dirty = true, reveal;
    long changedAt, renderedAt;
    static final boolean TRACE = System.getenv("PM_TRACE") != null;

    public DocView(EditorModel model) {
        this.model = model;
        blocks.getStyleClass().add("pm-doc");
        interim.getStyleClass().addAll("pm-text", "pm-interim");
        overlay.setMouseTransparent(true);
        overlay.setManaged(false);
        gutter.setMouseTransparent(true);
        gutter.setManaged(false);
        back.setMouseTransparent(true);
        back.setManaged(false);
        front.setPickOnBounds(false);
        front.setManaged(false);
        page.getStyleClass().add("pm-page");
        page.minHeightProperty().bind(scroll.heightProperty().subtract(2));
        scroll.setFitToWidth(true);
        scroll.setFocusTraversable(false);
        scroll.getStyleClass().add("pm-scroll");
        findBar = new FindBar(this);
        getChildren().addAll(scroll, findBar);
        getStylesheets().add(DocView.class.getResource("editor.css").toExternalForm());
        setFocusTraversable(true);
        new EditorKeys(this);
        ime = new Ime(this);
        new EditorMouse(this, page);
        addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            if (!e.isControlDown() || e.getDeltaY() == 0) return;
            zoomStep(e.getDeltaY() > 0 ? 1 : -1);
            e.consume();
        });
        render(model.state().getDoc());
        model.onChange((old, now) -> {
            changedAt = System.nanoTime();
            if (old.getDoc() != now.getDoc()) render(now.getDoc());
            renderedAt = System.nanoTime();
            reveal = true;
            invalidate();
        });
        focusedProperty().addListener((o, a, b) -> invalidate());
        page.widthProperty().addListener((o, a, b) -> invalidate());
        sceneProperty().addListener((o, a, scene) -> { if (scene != null) scene.addPostLayoutPulseListener(this::afterLayout); });
    }

    public EditorModel model() { return model; }

    /** Line numbers in the left margin, one per paragraph, heading or list item. */
    public void setLineNumbers(boolean on) {
        numbered = on;
        if (on) page.getStyleClass().add("pm-numbered"); else { page.getStyleClass().remove("pm-numbered"); lineNumbers.clear(); }
        lineNumbersShown.set(on);
        invalidate();
    }

    public boolean lineNumbers() { return numbered; }

    final javafx.beans.property.ReadOnlyBooleanWrapper lineNumbersShown = new javafx.beans.property.ReadOnlyBooleanWrapper();

    /** Whether line numbers are shown, for a toolbar button that shows it. */
    public javafx.beans.property.ReadOnlyBooleanProperty lineNumbersProperty() { return lineNumbersShown.getReadOnlyProperty(); }

    /** Highlight the whole block holding the caret, gently. */
    public void setActiveLine(boolean on) {
        active.highlight = on;
        activeLineShown.set(on);
        invalidate();
    }

    public boolean activeLine() { return active.highlight; }

    final javafx.beans.property.ReadOnlyBooleanWrapper activeLineShown = new javafx.beans.property.ReadOnlyBooleanWrapper();

    public javafx.beans.property.ReadOnlyBooleanProperty activeLineProperty() { return activeLineShown.getReadOnlyProperty(); }

    /** The whole document onto the clipboard; the status line says so for a moment. */
    public void copyAll(CopyFormat format) {
        Clip.copyAll(model.state().getDoc(), format);
        notice.set("");
        notice.set("Copied all as " + format.label.toLowerCase());
    }

    /** A short message for the status line ("Copied all as markdown source"); the status line clears it. */
    final javafx.beans.property.StringProperty notice = new javafx.beans.property.SimpleStringProperty("");

    static final double MIN_ZOOM = 0.25, MAX_ZOOM = 4;

    /** Text size, 1 = 100%, any value from 25% to 400%. Everything in the page scales; the toolbar and status line do not. */
    public void setZoom(double z) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.round(z * 100) / 100.0));
        page.setStyle("-fx-font-size: " + (15 * zoom) + "px;");
        lineNumbers.setScale(zoom);
        zoomProperty.set(zoom);
        invalidate();
    }

    public double zoom() { return zoom; }

    /** Ctrl+= / Ctrl+-: to the next 10% step up or down, so 115% goes to 120% or 110%. */
    public void zoomStep(int direction) {
        var tenths = zoom * 10;
        setZoom((direction > 0 ? Math.floor(tenths + 1e-6) + 1 : Math.ceil(tenths - 1e-6) - 1) / 10);
    }

    final javafx.beans.property.ReadOnlyDoubleWrapper zoomProperty = new javafx.beans.property.ReadOnlyDoubleWrapper(1);

    /** The zoom, for a status line that shows it. */
    public javafx.beans.property.ReadOnlyDoubleProperty zoomProperty() { return zoomProperty.getReadOnlyProperty(); }

    public void find() { findBar.open(); }

    // ---- live text: speech recognition, a stream, a script

    final javafx.scene.text.Text interim = new javafx.scene.text.Text();
    String interimText = "";

    /**
     * Finished text from a live source: added at the end of the document as bullets, one undo step per call (see
     * editor.Tail). Clears the grey interim text, which the commit normally replaces. Safe to call
     * from any thread.
     */
    public void appendCommitted(String text) {
        onFx(() -> {
            interimText = "";
            var tr = prosemirrorfx.core.editor.Tail.append(model.state(), text);
            if (tr != null) model.dispatch(tr);
            placeInterim();
        });
    }

    /** A plain paragraph at the end (e.g. a timestamp line); appended text then starts a list under it. Any thread. */
    public void appendLine(String text) {
        onFx(() -> {
            var tr = prosemirrorfx.core.editor.Tail.appendLine(model.state(), text);
            model.dispatch(tr);
            placeInterim();
        });
    }

    /**
     * Words still arriving and not final, drawn in grey after the end of the document. They are never part
     * of the document: not saved, not copied, not counted, not undoable. Empty or null clears them.
     * Safe to call from any thread.
     */
    public void setInterim(String text) {
        onFx(() -> {
            interimText = text == null ? "" : text.strip();
            placeInterim();
            invalidate();
        });
    }

    /** Put the interim text at the end of the last line, where the next commit will land. */
    void placeInterim() {
        if (interim.getParent() instanceof Pane p) p.getChildren().remove(interim);
        if (interimText.isEmpty() || positions == null || positions.textblocks.isEmpty()) return;
        var tb = positions.textblocks.getLast();
        var line = tb.node.getTextContent();
        var newBullet = prosemirrorfx.core.editor.Tail.lineEndsSentence(model.state().getDoc()) && !line.isBlank();
        var gap = line.isEmpty() || Character.isWhitespace(line.charAt(line.length() - 1)) ? "" : newBullet ? "  " : " ";
        interim.setText(gap + interimText);
        tb.flow.getChildren().add(interim);
    }

    static void onFx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run(); else Platform.runLater(r);
    }

    /** The file this editor is saving to, or null. */
    public FileSession file() { return file; }

    /** The table of contents attached to this editor, or null. */
    public Toc toc() { return toc; }

    void invalidate() {
        dirty = true;
        Platform.requestNextPulse();
    }

    void afterLayout() {
        if (!dirty || getScene() == null) return;
        dirty = false;
        caretLayer.paint(model.state().getSelection(), positions, isFocused());
        caretLayer.paintMatches(highlights, positions);
        caretLayer.paintComposition(ime.composingFrom(), ime.composingTo(), positions);
        if (numbered) lineNumbers.paint(positions);
        active.paint(positions);
        if (TRACE && changedAt > 0) System.err.printf("pm-trace render=%.2fms change-to-caret=%.2fms%n", (renderedAt - changedAt) / 1e6, (System.nanoTime() - changedAt) / 1e6);
        changedAt = 0;
        if (reveal) revealCaret();
        reveal = false;
    }

    void render(Node doc) {
        views = Reconciler.children(views, doc, 0, -1);
        Reconciler.patch(blocks, views);
        positions = new Positions(views);
        placeInterim();
    }

    void revealCaret() {
        var b = caretLayer.caretBounds;
        var viewH = scroll.getViewportBounds().getHeight();
        var range = page.getHeight() - viewH;
        if (b == null || range <= 0) { revealAtTop = false; return; }
        var top = scroll.getVvalue() * range;
        var target = revealAtTop ? b.getMinY() - 8 : b.getMinY() < top + 8 ? b.getMinY() - 8 : b.getMaxY() > top + viewH - 8 ? b.getMaxY() - viewH + 8 : top;
        scroll.setVvalue(Math.max(0, Math.min(1, target / range)));
        revealAtTop = false;
    }

    double caretSceneX() { return caretLayer.caret.localToScene(0, 0).getX(); }
}
