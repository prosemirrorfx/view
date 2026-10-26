package prosemirrorfx.view;

import com.atlassian.prosemirror.state.Selection;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

import static prosemirrorfx.core.pm.Pm.*;
import static prosemirrorfx.view.Positions.*;

/** Mouse on the page: click places the caret, drag and Shift+click extend, double and triple click select. */
final class EditorMouse {

    final DocView view;

    EditorMouse(DocView view, Region page) {
        this.view = view;
        page.addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
        page.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::dragged);
    }

    void pressed(MouseEvent e) {
        view.requestFocus();
        var pos = view.positions.posAt(e.getSceneX(), e.getSceneY());
        var state = view.model.state();
        var doc = state.getDoc();
        var tb = view.positions.at(pos);
        view.model.select(switch (e.getClickCount()) {
            case 1 -> Motion.select(state, pos, e.isShiftDown());
            case 2 -> word(tb, pos);
            default -> text(doc, tb.start, end(tb));
        });
        e.consume();
    }

    void dragged(MouseEvent e) {
        var pos = view.positions.posAt(e.getSceneX(), e.getSceneY());
        view.model.select(Motion.select(view.model.state(), pos, true));
        e.consume();
    }

    Selection word(BlockView tb, int pos) {
        var t = tb.node.getTextContent();
        int a = pos - tb.start, b = a;
        while (a > 0 && Character.isLetterOrDigit(t.charAt(a - 1))) a--;
        while (b < t.length() && Character.isLetterOrDigit(t.charAt(b))) b++;
        return text(view.model.state().getDoc(), tb.start + a, tb.start + b);
    }
}
