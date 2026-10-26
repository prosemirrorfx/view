package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import com.atlassian.prosemirror.state.Transaction;
import java.util.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
    final Folds folds = new Folds();
    final FoldControls foldControls = new FoldControls(this, front);
    final ReadOnlyObjectWrapper<Set<String>> foldedKeys = new ReadOnlyObjectWrapper<>(Set.of());
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
    /** How often a scene asked this view to paint; with the view in one scene that is once per pulse. */
    int layoutPasses;
    /** The one listener a scene holds for this view: kept so it can be taken out when the view moves to another scene. */
    final Runnable paint = this::afterLayout;
    static final boolean TRACE = System.getenv("PM_TRACE") != null;

    public DocView(EditorModel model) {
        this.model = model;
        getStyleClass().add("pm-view");
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
            if (old.getDoc() != now.getDoc()) {
                folds.remap(old.getDoc(), model.lastTransaction(), now.getDoc());
                render(now.getDoc());
            }
            renderedAt = System.nanoTime();
            if (folds.openAround(now.getDoc(), now.getSelection().getHead())) refreshFolds(); else publishFolded();
            reveal = true;
            invalidate();
        });
        focusedProperty().addListener((o, a, b) -> invalidate());
        page.widthProperty().addListener((o, a, b) -> invalidate());
        theme.addListener((o, was, t) -> {
            var style = t.style();
            scroll.setStyle(style);
            findBar.setStyle(style);
            caretLayer.apply(t);
            lineNumbers.apply(t);
            invalidate();
        });
        sceneProperty().addListener((o, was, scene) -> {
            if (was != null) was.removePostLayoutPulseListener(paint);
            if (scene != null) { scene.addPostLayoutPulseListener(paint); invalidate(); }
        });
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

    final javafx.beans.property.ObjectProperty<Theme> theme = new javafx.beans.property.SimpleObjectProperty<>(Theme.LIGHT);

    /** The colours of the editor and of the toolbar, status bar and contents panel made from it. Light until set. */
    public void setTheme(Theme t) { theme.set(t); }

    public Theme theme() { return theme.get(); }

    public javafx.beans.property.ObjectProperty<Theme> themeProperty() { return theme; }

    /** Keeps a part made from this view (toolbar, status bar, contents) in the view's theme, for as long as the part lives. */
    void follow(javafx.scene.Node part) {
        part.setStyle(theme.get().style());
        javafx.beans.value.ChangeListener<Theme> l = (o, was, t) -> part.setStyle(t.style());
        part.getProperties().put("pm-theme-listener", l);
        theme.addListener(new javafx.beans.value.WeakChangeListener<>(l));
    }

    /** Copy the selection to the system clipboard, as plain text and HTML. False when nothing is selected. JavaFX thread. */
    public boolean copy() { return Clip.copy(model.state()); }

    /** Copy the selection, then delete it. False when nothing is selected. */
    public boolean cut() { return apply(Clip.cut(model.state())); }

    /** Paste the clipboard at the caret, keeping its formatting where it has any. False when the clipboard holds no text. */
    public boolean paste() { return apply(Clip.paste(model.state())); }

    /** Paste only the clipboard's plain text, whatever formatting came with it. */
    public boolean pastePlain() { return apply(Clip.pastePlain(model.state())); }

    boolean apply(Transaction tr) {
        if (tr == null) return false;
        model.dispatch(tr);
        return true;
    }

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
        layoutPasses++;
        if (!dirty || getScene() == null) return;
        dirty = false;
        caretLayer.paint(model.state().getSelection(), positions, isFocused());
        caretLayer.paintMatches(highlights, positions);
        caretLayer.paintComposition(ime.composingFrom(), ime.composingTo(), positions);
        if (numbered) lineNumbers.paint(positions);
        active.paint(positions);
        foldControls.paint(views, folds);
        if (TRACE && changedAt > 0) System.err.printf("pm-trace render=%.2fms change-to-caret=%.2fms%n", (renderedAt - changedAt) / 1e6, (System.nanoTime() - changedAt) / 1e6);
        changedAt = 0;
        if (reveal) revealCaret();
        reveal = false;
    }

    void render(Node doc) {
        views = Reconciler.children(views, doc, 0, -1);
        Reconciler.patch(blocks, views);
        folds.layout(doc);
        folds.mark(views);
        positions = new Positions(views);
        placeInterim();
    }

    /** After the folds changed with no new document: what is hidden is worked out again, and the view is laid out and painted afresh. */
    void refreshFolds() {
        folds.layout(model.state().getDoc());
        folds.mark(views);
        positions = new Positions(views);
        placeInterim();
        publishFolded();
        invalidate();
    }

    void publishFolded() {
        var keys = Collections.unmodifiableSortedSet(folds.keys(model.state().getDoc()));
        if (!keys.equals(foldedKeys.get())) foldedKeys.set(keys);
    }

    /** Folds changed (by the host, a key or a click): a caret that is now hidden moves to the end of the line that owns the fold. */
    void foldsChanged() {
        var doc = model.state().getDoc();
        folds.layout(doc);
        var hidden = folds.hiddenAt(model.state().getSelection().getHead());
        if (hidden != null) model.select(prosemirrorfx.core.pm.Pm.cursor(doc, hidden[0] - 1));
        refreshFolds();
    }

    /** The keys of the blocks that are folded (sorted, unmodifiable). A key is the block's index path, the same on reopening a file that was not edited. */
    public Set<String> folded() { return foldedKeys.get(); }

    /** Changes when a block is folded or unfolded, by the user, by an edit that removes a folded block, or by the host. */
    public ReadOnlyObjectProperty<Set<String>> foldedProperty() { return foldedKeys.getReadOnlyProperty(); }

    /** Folds exactly these blocks, as remembered from folded(). A key that names no foldable block is ignored. */
    public void setFolded(Collection<String> keys) {
        var doc = model.state().getDoc();
        folds.folded.clear();
        for (var key : keys) {
            var pos = Folds.position(doc, key);
            if (pos >= 0) folds.folded.add(pos);
        }
        foldsChanged();
    }

    public void collapseAll() {
        folds.layout(model.state().getDoc());
        folds.folded.addAll(folds.foldables);
        foldsChanged();
    }

    public void expandAll() {
        folds.folded.clear();
        foldsChanged();
    }

    /** Folds the innermost block around the caret that is not folded yet (an item with nested lines, a heading with its section). False when there is none. */
    public boolean foldAtCaret() {
        var doc = model.state().getDoc();
        for (var pos : folds.around(doc, model.state().getSelection().getHead())) {
            if (folds.folded.contains(pos)) continue;
            folds.folded.add(pos);
            foldsChanged();
            return true;
        }
        return false;
    }

    /** Unfolds the innermost folded block around the caret. False when there is none. */
    public boolean unfoldAtCaret() {
        for (var pos : folds.around(model.state().getDoc(), model.state().getSelection().getHead())) {
            if (!folds.folded.remove(pos)) continue;
            foldsChanged();
            return true;
        }
        return false;
    }

    void toggleFold(int pos) {
        if (!folds.folded.remove(pos)) folds.folded.add(pos);
        foldsChanged();
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
