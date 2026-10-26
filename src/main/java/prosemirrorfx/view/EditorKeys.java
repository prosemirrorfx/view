package prosemirrorfx.view;

import com.atlassian.prosemirror.state.Selection;
import javafx.scene.input.*;
import prosemirrorfx.core.commands.Command;

import static javafx.scene.input.KeyCode.*;
import static prosemirrorfx.core.commands.Basic.selectAll;
import static prosemirrorfx.core.commands.Commands.*;

/** Keyboard: typed characters become text, editing and movement keys become commands and selections. */
final class EditorKeys {

    final DocView view;
    Double goalX;
    /** A key that ran a command must not also type (Ctrl+Alt+digit is AltGr+digit on some layouts). */
    boolean swallowTyped;

    EditorKeys(DocView view) {
        this.view = view;
        view.addEventHandler(KeyEvent.KEY_TYPED, this::typed);
        view.addEventHandler(KeyEvent.KEY_PRESSED, this::pressed);
    }

    void typed(KeyEvent e) {
        if (swallowTyped) { swallowTyped = false; e.consume(); return; }
        var ch = e.getCharacter();
        var shortcut = (e.isControlDown() && !e.isAltDown()) || e.isMetaDown();
        if (ch.isEmpty() || ch.charAt(0) < 32 || ch.charAt(0) == 127 || shortcut) return;
        goalX = null;
        view.model.type(ch);
        e.consume();
    }

    void pressed(KeyEvent e) {
        var ctrl = e.isShortcutDown();
        var shift = e.isShiftDown();
        swallowTyped = false;
        var code = e.getCode();
        if (code != UP && code != DOWN) goalX = null;
        // Confluence's own keys for block style, for those used to them: Ctrl+Alt+0 normal text, Ctrl+Alt+1..6 headings.
        if (e.isControlDown() && e.isAltDown() && !shift && code.isDigitKey()) {
            var d = code.getCode() - (code.isKeypadKey() ? KeyCode.NUMPAD0.getCode() : KeyCode.DIGIT0.getCode());
            if (d >= 0 && d <= 6) {
                run(setBlock(d));
                swallowTyped = true;
                e.consume();
                return;
            }
        }
        // Google Docs' list keys: Ctrl+Shift+8 bulleted, Ctrl+Shift+7 numbered. The editor has no F-keys: those are the host app's.
        if (ctrl && shift && !e.isAltDown() && (code == DIGIT8 || code == DIGIT7)) {
            run(code == DIGIT8 ? bulletList : orderedList);
            swallowTyped = true;
            e.consume();
            return;
        }
        var state = view.model.state();
        var head = state.getSelection().getHead();
        var handled = switch (code) {
            case ENTER -> run(shift ? hardBreak : enter);
            case BACK_SPACE -> ctrl ? deleteWord(-1) : run(backspace);
            case DELETE -> shift ? dispatch(Clip.cut(state)) : ctrl ? deleteWord(1) : run(delete);
            case INSERT -> shift ? dispatch(Clip.paste(state)) : ctrl && Clip.copy(state);
            case TAB -> { run(shift ? outdent : indent); yield true; }
            case LEFT, RIGHT -> ctrl ? moveTo(Motion.word(view.positions, head, code == LEFT ? -1 : 1), shift)
                    : move(Motion.horizontal(state, code == LEFT ? -1 : 1, shift, view.folds));
            case UP, DOWN -> ctrl ? (code == UP ? view.foldAtCaret() : view.unfoldAtCaret()) : vertical(code == UP ? -1 : 1, shift);
            case HOME, END -> ctrl ? moveTo(code == HOME ? view.positions.textblocks.getFirst().start : Positions.end(view.positions.textblocks.getLast()), shift)
                    : moveTo(Motion.lineEdge(view.positions, head, code == END), shift);
            case A -> ctrl && run(selectAll);
            case Z -> ctrl && run(shift ? redo : undo);
            case Y -> ctrl && run(redo);
            case B -> ctrl && run(bold);
            case I -> ctrl && run(italic);
            case C -> ctrl && Clip.copy(state);
            case X -> ctrl && dispatch(Clip.cut(state));
            case V -> ctrl && dispatch(shift ? Clip.pastePlain(state) : Clip.paste(state));
            case F -> ctrl && does(view::find);
            case H -> ctrl && does(() -> { view.find(); view.findBar.replace.requestFocus(); });
            case S -> ctrl && view.file != null && does(view.file::save);
            case CLOSE_BRACKET -> ctrl && run(indent);
            case OPEN_BRACKET -> ctrl && run(outdent);
            case BACK_SLASH -> ctrl && run(clearFormatting);
            case O -> ctrl && view.file != null && does(view.file::chooseAndOpen);
            case EQUALS, PLUS, ADD -> ctrl && does(() -> view.zoomStep(1));
            case MINUS, SUBTRACT -> ctrl && does(() -> view.zoomStep(-1));
            case DIGIT0, NUMPAD0 -> ctrl && does(() -> view.setZoom(1));
            default -> false;
        };
        if (handled) e.consume();
    }

    boolean does(Runnable r) {
        r.run();
        return true;
    }

    boolean dispatch(com.atlassian.prosemirror.state.Transaction tr) {
        if (tr != null) view.model.dispatch(tr);
        return true;
    }

    /** Ctrl+Backspace / Ctrl+Delete: the word before or after the caret; at a block edge, the ordinary join. */
    boolean deleteWord(int dir) {
        var state = view.model.state();
        var sel = state.getSelection();
        if (!sel.getEmpty()) return run(dir < 0 ? backspace : delete);
        var head = sel.getHead();
        var tb = view.positions.at(head);
        var to = Motion.word(view.positions, head, dir);
        if (to < tb.start || to > Positions.end(tb) || to == head) return run(dir < 0 ? backspace : delete);
        var tr = state.getTr();
        tr.delete(Math.min(head, to), Math.max(head, to));
        return dispatch(tr.scrollIntoView());
    }

    boolean run(Command c) {
        c.apply(view.model.state(), view.model::dispatch);
        return true;
    }

    boolean move(Selection s) {
        if (s != null) view.model.select(s);
        return true;
    }

    boolean moveTo(int head, boolean extend) { return move(Motion.select(view.model.state(), head, extend)); }

    boolean vertical(int dir, boolean extend) {
        if (goalX == null) goalX = view.caretSceneX();
        var head = Motion.vertical(view.positions, view.model.state().getSelection().getHead(), dir, goalX);
        return move(Motion.select(view.model.state(), head, extend));
    }
}
