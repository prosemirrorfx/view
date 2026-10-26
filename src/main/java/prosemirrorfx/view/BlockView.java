package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.text.TextFlow;

/**
 * One block of the document as drawn, like prosemirror-view's ViewDesc. A textblock has a flow whose
 * character i is document position start + i; a container has children drawn inside box. Views are
 * kept and updated in place across transactions, so only what changed is touched in the scene.
 */
public final class BlockView {

    Node node;
    final Region fx;
    final Pane box;
    final TextFlow flow;
    final Label marker;
    List<BlockView> children;
    int start;

    BlockView(Node node, Region fx, Pane box, TextFlow flow, Label marker, List<BlockView> children, int start) {
        this.node = node;
        this.fx = fx;
        this.box = box;
        this.flow = flow;
        this.marker = marker;
        this.children = children;
        this.start = start;
    }

    boolean isTextblock() { return flow != null; }

    void shift(int delta) {
        if (delta == 0) return;
        start += delta;
        children.forEach(c -> c.shift(delta));
    }
}
