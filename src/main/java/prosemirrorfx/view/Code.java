package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import java.util.*;
import java.util.regex.Pattern;

import static prosemirrorfx.core.pm.Pm.attr;
import static prosemirrorfx.core.schema.EditorSchema.*;

/**
 * Fenced code as the lossless reader keeps it: one verbatim paragraph whose lines are text separated by hard breaks,
 * the opening fence line first (with the language after it) and, when the fence is closed, the closing fence last.
 * The fence lines stay in the document so that the text round-trips; the view only shrinks them.
 */
final class Code {

    static final Pattern OPEN = Pattern.compile("^(`{3,}|~{3,})[ \\t]*([^\\s`]*)"), CLOSE = Pattern.compile("^(`{3,}|~{3,})[ \\t]*$");

    /** The text of each line of the block (a line with no text is empty), in order. */
    static List<String> lines(Node block) {
        var out = new ArrayList<String>();
        var line = new StringBuilder();
        for (var c : block.getContent().getContent()) {
            if (c.getType() == HARD_BREAK) { out.add(line.toString()); line.setLength(0); }
            else if (c.isText()) line.append(c.getText());
        }
        out.add(line.toString());
        return out;
    }

    static boolean isBlock(Node n) {
        return n.getType() == PARAGRAPH && Boolean.TRUE.equals(attr(n, "verbatim")) && OPEN.matcher(lines(n).getFirst().stripLeading()).find();
    }

    /** The language after the opening fence ("java" in ```java), or "". */
    static String language(Node block) {
        var m = OPEN.matcher(lines(block).getFirst().stripLeading());
        return m.find() ? m.group(2) : "";
    }

    /** True when the last line is a closing fence (and there is more than the opening line). */
    static boolean closed(List<String> lines) {
        return lines.size() > 1 && CLOSE.matcher(lines.getLast().strip()).find();
    }

    /** The code itself, without the fence lines. */
    static String text(Node block) {
        var lines = lines(block);
        return String.join("\n", lines.subList(1, closed(lines) ? lines.size() - 1 : lines.size()));
    }

    /** Which children of the block belong to a fence line: the first line and its break, and the closing line and the break before it. */
    static BitSet fenceChildren(Node block) {
        var fence = new BitSet();
        var kids = block.getContent().getContent();
        var firstBreak = -1;
        var lastBreak = -1;
        for (var k = 0; k < kids.size(); k++) {
            if (kids.get(k).getType() != HARD_BREAK) continue;
            if (firstBreak < 0) firstBreak = k;
            lastBreak = k;
        }
        if (firstBreak < 0) { fence.set(0, kids.size()); return fence; }
        fence.set(0, firstBreak + 1);
        if (closed(lines(block))) fence.set(lastBreak, kids.size());
        return fence;
    }
}
