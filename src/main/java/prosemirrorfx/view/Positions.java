package prosemirrorfx.view;

import java.util.*;
import javafx.geometry.*;

/** The bridge between document positions and the drawn text: textblocks in order, by position and by point. */
final class Positions {

    final List<BlockView> textblocks;

    Positions(List<BlockView> top) {
        textblocks = new ArrayList<>();
        collect(top);
    }

    void collect(List<BlockView> views) {
        for (var v : views) {
            if (v.hidden) continue;
            if (v.isTextblock()) textblocks.add(v);
            else collect(v.children);
        }
    }

    int indexAt(int pos) {
        int lo = 0, hi = textblocks.size() - 1;
        while (lo < hi) {
            var mid = (lo + hi + 1) >>> 1;
            if (textblocks.get(mid).start <= pos) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    BlockView at(int pos) { return textblocks.get(indexAt(pos)); }

    static int size(BlockView tb) { return tb.node.getContent().getSize(); }

    static int end(BlockView tb) { return tb.start + size(tb); }

    /** The document position under a scene point: the textblock on that line, or the nearest one. */
    int posAt(double sceneX, double sceneY) {
        BlockView best = null;
        var bestDist = Double.MAX_VALUE;
        for (var tb : textblocks) {
            var b = tb.flow.localToScene(tb.flow.getLayoutBounds());
            var dist = sceneY < b.getMinY() ? b.getMinY() - sceneY : sceneY > b.getMaxY() ? sceneY - b.getMaxY() : 0;
            if (dist < bestDist) { best = tb; bestDist = dist; }
            if (dist == 0) break;
        }
        var b = best.flow.localToScene(best.flow.getLayoutBounds());
        var y = Math.min(Math.max(sceneY, b.getMinY() + 1), b.getMaxY() - 1);
        return hit(best, best.flow.sceneToLocal(sceneX, y));
    }

    static int hit(BlockView tb, Point2D local) {
        var i = tb.flow.hitTest(local).getInsertionIndex();
        return tb.start + Math.max(0, Math.min(i, size(tb)));
    }

    static Rectangle2D caretRect(BlockView tb, int offset) {
        return tb.flow.getLayoutInfo().caretInfoAt(offset, true).getSegmentAt(0);
    }
}
