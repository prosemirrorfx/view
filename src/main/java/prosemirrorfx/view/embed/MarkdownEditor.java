package prosemirrorfx.view.embed;

import com.atlassian.prosemirror.model.Node;
import java.util.*;
import javafx.beans.property.*;
import prosemirrorfx.core.editor.EditorModel;
import prosemirrorfx.markdown.Lossless;
import prosemirrorfx.core.pm.Pm;
import prosemirrorfx.markdown.SourceMap;
import prosemirrorfx.view.*;

/**
 * The WYSIWYG editor as a control for another application, holding one Markdown text. Load it with
 * setMarkdown, read it back with getMarkdown: a text that was not edited comes back byte for byte, and an
 * edited one differs only in the blocks that were edited (see Lossless in prosemirrorfx-markdown). The keys, toolbar and status line
 * behave as in the editor's own window. Use from the JavaFX thread only.
 */
public final class MarkdownEditor {

    final DocView view;
    final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper();
    final List<Runnable> listeners = new ArrayList<>();
    Node saved;
    String savedText = "";
    boolean loading;

    public MarkdownEditor() {
        var model = new EditorModel(Lossless.read(""));
        view = new DocView(model);
        saved = model.state().getDoc();
        model.onChange((old, now) -> {
            if (loading) return;
            dirty.set(!saved.equals(now.getDoc()));
            if (old.getDoc() != now.getDoc()) List.copyOf(listeners).forEach(Runnable::run);
        });
    }

    /** The control to put in a scene. It takes the keyboard when focused. */
    public javafx.scene.Node node() { return view; }

    /** The underlying control, for what this class does not wrap (find, zoom, line numbers, a table of contents). */
    public DocView view() { return view; }

    /** A toolbar for this editor, to place above it. */
    public javafx.scene.Node toolbar() { return Toolbar.of(view); }

    /** A status line for this editor, to place below it. */
    public javafx.scene.Node statusBar() { return StatusBar.of(view); }

    /** Replace the text. The caret goes to the start, undo history is cleared, and the editor is no longer dirty. Listeners are not called. */
    public void setMarkdown(String markdown) {
        loading = true;
        try {
            view.model().load(Lossless.read(markdown));
        } finally {
            loading = false;
        }
        mark(markdown);
    }

    /** The text as Markdown. Unedited: exactly what was set. */
    public String getMarkdown() {
        var doc = view.model().state().getDoc();
        return saved.equals(doc) ? savedText : Lossless.write(doc);
    }

    /** Edited since the last setMarkdown or markSaved. Undoing back to the loaded text makes it clean again. */
    public boolean isDirty() { return dirty.get(); }

    public ReadOnlyBooleanProperty dirtyProperty() { return dirty.getReadOnlyProperty(); }

    /** Call after saving: the text as it is now becomes the clean state. Returns it. */
    public String markSaved() {
        var text = Lossless.write(view.model().state().getDoc());
        mark(text);
        return text;
    }

    /** Called on the JavaFX thread after every edit (not after setMarkdown). */
    public void onChange(Runnable listener) { listeners.add(listener); }

    public void focus() { view.requestFocus(); }

    /** Source positions, for a source view that keeps the caret line and for search jumps. Lines and columns count from 0; offsets index getMarkdown(). */
    public SourceMap sourceMap() { return view.sourceMap(); }

    public int caretLine() { return view.caretLine(); }

    public int caretSourceOffset() { return view.caretSourceOffset(); }

    public void goToLine(int line) { view.goToLine(line); }

    public void goToSource(int line, int column) { view.goToSource(line, column); }

    public void setCaretSourceOffset(int offset) { view.setCaretSourceOffset(offset); }

    /** Folding, for a host that remembers it per file: the keys of the folded blocks (valid for the same text), and restoring them. */
    public Set<String> folded() { return view.folded(); }

    public ReadOnlyObjectProperty<Set<String>> foldedProperty() { return view.foldedProperty(); }

    public void setFolded(Collection<String> keys) { view.setFolded(keys); }

    public void collapseAll() { view.collapseAll(); }

    public void expandAll() { view.expandAll(); }

    /** Clipboard actions for a host's own toolbar or menu: what Ctrl+C, X, V and Shift+V do. Each says whether it did anything. */
    public boolean copy() { return view.copy(); }

    public boolean cut() { return view.cut(); }

    public boolean paste() { return view.paste(); }

    public boolean pastePlain() { return view.pastePlain(); }

    /** The caret as a position in the document, for a host that remembers it; see setCaret. */
    public int caret() { return view.model().state().getSelection().getHead(); }

    public void setCaret(int position) {
        var doc = view.model().state().getDoc();
        view.model().select(Pm.near(doc.resolve(Math.max(0, Math.min(position, doc.getContent().getSize()))), 1));
    }

    void mark(String text) {
        saved = view.model().state().getDoc();
        savedText = text;
        dirty.set(false);
    }
}
