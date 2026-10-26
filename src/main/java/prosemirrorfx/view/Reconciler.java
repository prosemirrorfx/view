package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import java.util.*;
import javafx.scene.layout.Pane;

import static prosemirrorfx.core.schema.EditorSchema.*;

/**
 * Brings the drawn views in line with a new document while touching as little of the scene as possible:
 * a node that is the same object keeps its view, a node of the same kind is updated in place, and a
 * container's child list is patched only between its common prefix and suffix. Like prosemirror-view's updateChildren.
 */
final class Reconciler {

    static List<BlockView> children(List<BlockView> old, Node parent, int contentStart, int listDepth) {
        var byNode = new IdentityHashMap<Node, BlockView>();
        old.forEach(v -> byNode.put(v.node, v));
        var used = Collections.newSetFromMap(new IdentityHashMap<BlockView, Boolean>());
        var next = new ArrayList<BlockView>();
        var pos = contentStart;
        var index = 0;
        for (var child : parent.getContent().getContent()) {
            var marker = child.getType() == LIST_ITEM ? DocRenderer.marker(parent, index, listDepth) : null;
            var same = byNode.get(child);
            if (same != null && !used.contains(same)) {
                same.shift(pos + 1 - same.start);
            } else {
                same = index < old.size() && !used.contains(old.get(index)) ? old.get(index) : null;
                same = same != null && canUpdate(same, child) ? update(same, child, pos + 1, listDepth) : DocRenderer.build(child, pos + 1, listDepth, marker);
            }
            if (same.marker != null && !same.marker.getText().equals(marker)) same.marker.setText(marker);
            used.add(same);
            next.add(same);
            pos += child.getNodeSize();
            index++;
        }
        return next;
    }

    /**
     * A changed textblock always gets a fresh TextFlow. A reused TextFlow that has laid out Latin text and is then
     * given Devanagari or Tamil (an IME composing "namaste" letter by letter, then committing नमस्ते) draws the
     * Indic glyph ids with Segoe UI: garbage until the next edit. JavaFX 26.0.2; reproduced by demo.ImeReplay.
     * Unchanged textblocks are still kept by identity, so only the edited paragraph is rebuilt.
     */
    static boolean canUpdate(BlockView view, Node node) {
        if (view.isTextblock()) return false;
        return view.node.getType() == node.getType() && view.node.sameMarkup(node);
    }

    static BlockView update(BlockView view, Node node, int start, int listDepth) {
        view.node = node;
        view.start = start;
        view.children = children(view.children, node, start, DocRenderer.childDepth(node, listDepth));
        patch(view.box, view.children);
        return view;
    }

    /** Replace only the middle of the child list that differs; unchanged JavaFX nodes stay where they are. */
    static void patch(Pane box, List<BlockView> views) {
        var have = box.getChildren();
        var want = views.stream().map(v -> v.fx).toList();
        int a = 0, n = have.size(), m = want.size();
        while (a < n && a < m && have.get(a) == want.get(a)) a++;
        int z = 0;
        while (z < n - a && z < m - a && have.get(n - 1 - z) == want.get(m - 1 - z)) z++;
        if (a == n && a == m) return;
        have.remove(a, n - z);
        have.addAll(a, want.subList(a, m - z));
    }

    private Reconciler() {}
}
