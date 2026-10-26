package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import com.atlassian.prosemirror.state.Transaction;
import java.util.*;

import static prosemirrorfx.core.pm.Pm.intAttr;
import static prosemirrorfx.core.schema.EditorSchema.*;

/**
 * Which blocks are folded. A list item folds away everything after its first paragraph; a heading folds away its
 * section, up to the next heading of the same or a higher level. A fold is kept as the position before its block, and
 * mapped through every edit, so it stays on its block while the text changes. The stable key handed to a host is the
 * block's index path ("5/0/1": child 5 of the document, its child 0, that one's child 1), which is the same on reopening
 * a file that was not edited.
 */
final class Folds {

    /** Positions before the folded blocks. */
    final TreeSet<Integer> folded = new TreeSet<>();
    /** Positions before every block that has something to fold, in document order. */
    List<Integer> foldables = List.of();
    Set<Integer> foldableSet = Set.of();
    /** The ranges [from, to) that the folds hide now. A caret strictly inside one is hidden. */
    List<int[]> hidden = List.of();

    /** What the block at pos hides when folded, as [from, to); null when it has nothing to fold. */
    static int[] range(Node doc, int pos) {
        var n = doc.nodeAt(pos);
        if (n == null) return null;
        if (n.getType() == LIST_ITEM) return n.getChildCount() > 1 ? new int[]{pos + 1 + n.child(0).getNodeSize(), pos + n.getNodeSize() - 1} : null;
        if (n.getType() != HEADING) return null;
        var at = doc.resolve(pos);
        var parent = at.getParent();
        var level = intAttr(n, "level");
        var from = pos + n.getNodeSize();
        var end = from;
        for (var j = at.index(null) + 1; j < parent.getChildCount(); j++) {
            var c = parent.child(j);
            if (c.getType() == HEADING && intAttr(c, "level") <= level) break;
            end += c.getNodeSize();
        }
        return end > from ? new int[]{from, end} : null;
    }

    /** The block's index path from the document root, the same on reopening an unedited file. */
    static String key(Node doc, int pos) {
        var at = doc.resolve(pos);
        var path = new StringJoiner("/");
        for (var d = 0; d <= at.getDepth(); d++) path.add(Integer.toString(at.index(d)));
        return path.toString();
    }

    /** The position of the foldable block a key names, or -1 when the document has none there. */
    static int position(Node doc, String key) {
        var node = doc;
        var pos = 0;
        var parts = key.split("/");
        try {
            for (var k = 0; k < parts.length; k++) {
                var index = Integer.parseInt(parts[k].strip());
                if (index < 0 || index >= node.getChildCount()) return -1;
                for (var i = 0; i < index; i++) pos += node.child(i).getNodeSize();
                if (k == parts.length - 1) return range(doc, pos) != null ? pos : -1;
                node = node.child(index);
                pos++;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return -1;
    }

    /** Recomputes what can be folded and what is hidden, for this document. Folds that no longer have anything to hide are dropped. */
    void layout(Node doc) {
        var list = new ArrayList<Integer>();
        doc.descendants((n, pos, parent, i) -> {
            if ((n.getType() == HEADING || n.getType() == LIST_ITEM) && range(doc, pos) != null) list.add(pos);
            return !n.isTextblock();
        });
        foldables = list;
        foldableSet = new HashSet<>(list);
        folded.retainAll(foldableSet);
        hidden = folded.stream().map(p -> range(doc, p)).toList();
    }

    /** Carries the folds through the edit that turned oldDoc into doc; a block the edit removed loses its fold. A load (no transaction) loses them all. */
    void remap(Node oldDoc, Transaction tr, Node doc) {
        if (tr == null) { folded.clear(); return; }
        var mapping = tr.getMapping();
        var next = new TreeSet<Integer>();
        for (var p : folded) {
            var n = oldDoc.nodeAt(p);
            if (n == null) continue;
            var start = mapping.map(p, 1);
            var end = mapping.map(p + n.getNodeSize(), -1);
            if (end - start >= 2 && range(doc, start) != null) next.add(start);
        }
        folded.clear();
        folded.addAll(next);
    }

    /** The hidden range a caret at pos is inside, or null. */
    int[] hiddenAt(int pos) {
        for (var r : hidden) if (r[0] < pos && pos < r[1]) return r;
        return null;
    }

    boolean hiddenNode(int nodePos, int size) {
        for (var r : hidden) if (r[0] <= nodePos && nodePos + size <= r[1]) return true;
        return false;
    }

    /** Opens every fold that hides pos (a find or a jump landed inside folded text); true when any opened. */
    boolean openAround(Node doc, int pos) {
        var opened = false;
        for (var p : List.copyOf(folded)) {
            var r = range(doc, p);
            if (r != null && r[0] < pos && pos < r[1]) { folded.remove(p); opened = true; }
        }
        return opened;
    }

    /** Foldable blocks around the caret, the innermost first: the item or heading it is in, then what holds them. */
    List<Integer> around(Node doc, int caret) {
        var out = new ArrayList<Integer>();
        for (var p : foldables) {
            var n = doc.nodeAt(p);
            var end = n.getType() == HEADING ? range(doc, p)[1] : p + n.getNodeSize();
            if (p <= caret && caret < end) out.add(p);
        }
        out.sort(Comparator.reverseOrder());
        return out;
    }

    /** The keys of the folds in force, in a fixed order. */
    SortedSet<String> keys(Node doc) {
        var keys = new TreeSet<String>();
        for (var p : folded) keys.add(key(doc, p));
        return keys;
    }

    /** Marks the views the folds hide; a hidden view is neither shown nor laid out. */
    void mark(List<BlockView> views) {
        for (var v : views) {
            var hide = hiddenNode(v.start - 1, v.node.getNodeSize());
            v.hidden = hide;
            v.fx.setVisible(!hide);
            v.fx.setManaged(!hide);
            if (!hide) mark(v.children);
        }
    }
}
