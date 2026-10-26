package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import java.util.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.text.*;

import static prosemirrorfx.core.pm.Pm.intAttr;
import static prosemirrorfx.core.pm.Pm.str;
import static prosemirrorfx.core.schema.EditorSchema.*;

/** Builds fresh JavaFX nodes for document nodes. Every inline node is one Text, so offsets map one to one. */
final class DocRenderer {

    static final String[] BULLETS = {"•", "◦", "▪"};

    static BlockView build(Node node, int start, int listDepth, String marker) {
        if (node.isTextblock()) return textblock(node, start, listDepth < 0);
        var kids = Reconciler.children(List.of(), node, start, childDepth(node, listDepth));
        var box = new VBox();
        kids.forEach(k -> box.getChildren().add(k.fx));
        if (node.getType() != LIST_ITEM) {
            box.getStyleClass().add(isList(node) ? "pm-list" : "pm-" + node.getType().getName());
            return new BlockView(node, box, box, null, null, kids, start);
        }
        var label = new Label(marker);
        label.getStyleClass().add("pm-marker");
        box.getStyleClass().add("pm-item-body");
        HBox.setHgrow(box, Priority.ALWAYS);
        var row = new HBox(label, box);
        row.getStyleClass().add("pm-item");
        return new BlockView(node, row, box, null, label, kids, start);
    }

    /** A paragraph read from a file keeps its leading whitespace as a margin when it stands in the document itself, so a reply stays under its comment. */
    static BlockView textblock(Node node, int start, boolean topLevel) {
        var flow = new TextFlow();
        flow.getStyleClass().add(styleClass(node));
        if (Code.isBlock(node)) flow.getStyleClass().add("pm-code-block");
        var lead = topLevel ? str(node, "lead") : null;
        if (lead != null && !lead.isEmpty()) flow.setStyle("-fx-padding: 0 0 0 " + lead.chars().map(c -> c == 9 ? 4 : 1).sum() * 0.5 + "em;");
        flow.getChildren().setAll(runs(node));
        return new BlockView(node, flow, null, flow, null, List.of(), start);
    }

    static String styleClass(Node textblock) {
        return textblock.getType() == HEADING ? "pm-h" + intAttr(textblock, "level") : "pm-p";
    }

    static List<Text> runs(Node textblock) { return runs(textblock, false); }

    /** open: the caret is inside a fenced block, so its fence lines are shown at full size. */
    static List<Text> runs(Node textblock, boolean open) {
        var texts = new ArrayList<Text>();
        var kids = textblock.getContent().getContent();
        var fence = Code.isBlock(textblock) ? Code.fenceChildren(textblock) : new BitSet();
        for (var k = 0; k < kids.size(); k++) {
            var t = text(kids.get(k));
            if (fence.get(k)) t.getStyleClass().addAll(open ? List.of("pm-fence", "pm-fence-open") : List.of("pm-fence"));
            texts.add(t);
        }
        if (texts.isEmpty()) texts.add(text("", List.of()));
        return texts;
    }

    static Text text(Node inline) {
        return text(inline.isText() ? inline.getText() : inline.getType() == HARD_BREAK ? "\n" : "￼",
                inline.getMarks().stream().map(m -> m.getType().getName()).toList());
    }

    static Text text(String s, List<String> markNames) {
        var t = new Text(s);
        t.getStyleClass().add("pm-text");
        t.getStyleClass().addAll(markNames);
        return t;
    }

    static boolean isList(Node node) { return node.getType() == BULLET_LIST || node.getType() == ORDERED_LIST; }

    static int childDepth(Node node, int listDepth) { return isList(node) ? listDepth + 1 : listDepth; }

    static String marker(Node list, int index, int depth) {
        return list.getType() == ORDERED_LIST ? (intAttr(list, "order") + index) + "." : BULLETS[depth % BULLETS.length];
    }

    private DocRenderer() {}
}
