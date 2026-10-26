package prosemirrorfx.view;

import com.atlassian.prosemirror.state.*;
import javafx.geometry.Point2D;
import javafx.scene.text.TextLineInfo;

import static prosemirrorfx.core.pm.Pm.*;
import static prosemirrorfx.view.Positions.*;

/** Caret movement: by character through ResolvedPos, by line and to line ends through the TextFlow layout. */
final class Motion {

    static Selection horizontal(PMEditorState s, int dir, boolean extend) {
        var sel = s.getSelection();
        var doc = s.getDoc();
        if (!extend && !sel.getEmpty()) return cursor(doc, dir < 0 ? sel.getFrom() : sel.getTo());
        var next = sel.getHead() + dir;
        if (next < 0 || next > doc.getContent().getSize()) return null;
        var found = findFrom(doc.resolve(next), dir);
        return found == null ? null : select(s, found.getHead(), extend);
    }

    static Selection select(PMEditorState s, int head, boolean extend) {
        var doc = s.getDoc();
        return extend ? between(doc.resolve(s.getSelection().getAnchor()), doc.resolve(head)) : cursor(doc, head);
    }

    /** Head position one line up or down, keeping the remembered x (scene coordinates). */
    static int vertical(Positions p, int head, int dir, double goalX) {
        var i = p.indexAt(head);
        var tb = p.textblocks.get(i);
        var lines = tb.flow.getLayoutInfo().getTextLines(false);
        var li = lineOf(lines.stream().toList(), head - tb.start);
        TextLineInfo target;
        if (dir < 0 && li > 0) target = lines.get(li - 1);
        else if (dir > 0 && li < lines.size() - 1) target = lines.get(li + 1);
        else {
            var j = i + dir;
            if (j < 0) return p.textblocks.getFirst().start;
            if (j >= p.textblocks.size()) return end(p.textblocks.getLast());
            tb = p.textblocks.get(j);
            var nl = tb.flow.getLayoutInfo().getTextLines(false);
            target = dir < 0 ? nl.getLast() : nl.getFirst();
        }
        var x = tb.flow.sceneToLocal(goalX, 0).getX();
        return hit(tb, new Point2D(x, target.bounds().getMinY() + target.bounds().getHeight() / 2));
    }

    static int lineEdge(Positions p, int head, boolean end) {
        var tb = p.at(head);
        var lines = tb.flow.getLayoutInfo().getTextLines(false);
        var line = lines.get(lineOf(lines, head - tb.start));
        var isLast = line == lines.getLast();
        return tb.start + Math.min(size(tb), end ? (isLast ? size(tb) : Math.max(line.start(), line.end() - 1)) : line.start());
    }

    /** Ctrl+Left/Right as on Windows: back to the start of a word, forward past a word and the spaces after it. At a block edge, into the next block. */
    static int word(Positions p, int head, int dir) {
        var i = p.indexAt(head);
        var tb = p.textblocks.get(i);
        var t = drawn(tb);
        var off = head - tb.start;
        if (dir < 0) {
            if (off == 0) return i > 0 ? end(p.textblocks.get(i - 1)) : head;
            while (off > 0 && !isWord(t.charAt(off - 1))) off--;
            while (off > 0 && isWord(t.charAt(off - 1))) off--;
        } else {
            if (off == t.length()) return i < p.textblocks.size() - 1 ? p.textblocks.get(i + 1).start : head;
            while (off < t.length() && isWord(t.charAt(off))) off++;
            while (off < t.length() && !isWord(t.charAt(off))) off++;
        }
        return tb.start + off;
    }

    static boolean isWord(char c) { return Character.isLetterOrDigit(c) || c == '_' || Character.getType(c) == Character.NON_SPACING_MARK || Character.getType(c) == Character.COMBINING_SPACING_MARK; }

    /** The block's text as drawn: one character per document position (a hard break is "\n"). */
    static String drawn(BlockView tb) {
        var sb = new StringBuilder();
        for (var n : tb.flow.getChildren()) if (n instanceof javafx.scene.text.Text t) sb.append(t.getText());
        // only the document's own characters: the grey interim text may follow them
        return sb.substring(0, Math.min(sb.length(), size(tb)));
    }

    static int lineOf(java.util.List<TextLineInfo> lines, int offset) {
        for (var k = 0; k < lines.size(); k++) if (offset < lines.get(k).end()) return k;
        return lines.size() - 1;
    }

    private Motion() {}
}
