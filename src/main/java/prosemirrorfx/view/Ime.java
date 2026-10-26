package prosemirrorfx.view;

import javafx.geometry.Point2D;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.InputMethodRequests;
import javafx.scene.input.InputMethodTextRun;

import static prosemirrorfx.core.pm.Pm.*;

/**
 * Input methods that compose a word before committing it (Tamil and Hindi phonetic, Japanese, Chinese).
 * The text being composed is put into the document at the caret, underlined, outside undo history, so it
 * lays out and wraps like any other text; each update replaces it, and the committed text then goes in
 * as typing does (input rules included), as one undo step. Keyboard layouts that type letters directly,
 * like Tamil 99 or InScript, never come here: they arrive as ordinary typed characters.
 */
final class Ime {

    final DocView view;
    /** Where the composing text sits in the document, or -1; and the text, to check it is still ours. */
    int from = -1;
    String composing = "";

    Ime(DocView view) {
        this.view = view;
        view.setInputMethodRequests(new Requests());
        view.setOnInputMethodTextChanged(this::changed);
    }

    void changed(InputMethodEvent e) {
        var composed = new StringBuilder();
        for (InputMethodTextRun run : e.getComposed()) composed.append(run.getText());
        update(e.getCommitted(), composed.toString(), e.getCaretPosition());
        e.consume();
    }

    /** One input method update: what was committed, what is now being composed, and the caret within it. */
    void update(String committed, String composed, int caret) {
        var model = view.model;
        removeComposing();
        if (committed != null && !committed.isEmpty()) model.type(committed);
        if (!composed.isEmpty()) {
            // typing over a selection replaces it, and that much is undoable
            if (!model.state().getSelection().getEmpty()) model.dispatch(model.state().getTr().deleteSelection());
            var tr = model.state().getTr();
            from = tr.getSelection().getFrom();
            tr.insertText(composed, null, null);
            tr.setSelection(cursor(tr.getDoc(), from + Math.max(0, Math.min(caret, composed.length()))));
            tr.setMeta("addToHistory", false);
            composing = composed;
            model.dispatch(tr);
        }
        view.invalidate();
    }

    /** Take the composing text back out, if it is still where we put it. */
    void removeComposing() {
        if (from < 0) return;
        var doc = view.model.state().getDoc();
        var to = from + composing.length();
        if (to <= doc.getContent().getSize() && doc.textBetween(from, to, "", "").equals(composing)) {
            var tr = view.model.state().getTr();
            tr.delete(from, to);
            tr.setSelection(cursor(tr.getDoc(), from));
            tr.setMeta("addToHistory", false);
            view.model.dispatch(tr);
        }
        from = -1;
        composing = "";
    }

    /** Range to underline, empty when nothing is being composed. */
    int composingFrom() { return from; }

    int composingTo() { return from < 0 ? from : from + composing.length(); }

    final class Requests implements InputMethodRequests {
        /** Screen position for the candidate window: under the caret. */
        @Override public Point2D getTextLocation(int offset) {
            var b = view.caretLayer.caretBounds;
            return b == null ? view.localToScreen(0, 0) : view.overlay.localToScreen(b.getMinX(), b.getMaxY());
        }

        @Override public int getLocationOffset(int x, int y) { return 0; }

        @Override public void cancelLatestCommittedText() {}

        @Override public String getSelectedText() {
            var s = view.model.state();
            return s.getDoc().textBetween(s.getSelection().getFrom(), s.getSelection().getTo(), "\n", "\n");
        }
    }
}
