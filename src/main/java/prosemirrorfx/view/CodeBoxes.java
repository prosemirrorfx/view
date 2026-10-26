package prosemirrorfx.view;

import com.atlassian.prosemirror.state.Selection;
import java.util.*;
import javafx.animation.PauseTransition;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.control.Label;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import static prosemirrorfx.core.schema.EditorSchema.CODE;
import static prosemirrorfx.view.Positions.end;

/**
 * Code, drawn after layout like the caret. A fenced block gets a shaded, bordered box behind it, its language in the
 * top right corner and a Copy button beside that; the fence lines themselves shrink to a hairline until the caret is
 * in the block. Inline code gets a subtle box behind each run, and a small copy button appears when the pointer is on
 * one. Everything is reused from pools and styled by CSS, so a Theme restyles it.
 */
final class CodeBoxes {

    /** Where an inline code run is, in the front layer, and what it says. */
    record Run(Bounds at, String text) {}

    final DocView view;
    final Pane back, front;
    final List<Region> boxes = new ArrayList<>(), inline = new ArrayList<>();
    final List<HBox> tools = new ArrayList<>();
    final List<Run> runs = new ArrayList<>();
    final Label hoverCopy = new Label("copy");

    CodeBoxes(DocView view, Pane back, Pane front) {
        this.view = view;
        this.back = back;
        this.front = front;
        hoverCopy.getStyleClass().add("pm-code-inline-copy");
        hoverCopy.setManaged(false);
        hoverCopy.setVisible(false);
        hoverCopy.setOnMousePressed(e -> e.consume());
        hoverCopy.setOnMouseClicked(e -> { copy(hoverCopy, (String) hoverCopy.getUserData(), "copy"); e.consume(); });
        front.getChildren().add(hoverCopy);
        view.page.addEventHandler(MouseEvent.MOUSE_MOVED, e -> hover(front.sceneToLocal(e.getSceneX(), e.getSceneY())));
        view.page.addEventHandler(MouseEvent.MOUSE_EXITED, e -> hoverCopy.setVisible(false));
    }

    void paint(Positions positions, Selection sel) {
        var block = 0;
        var run = 0;
        runs.clear();
        for (var tb : positions.textblocks) {
            if (Code.isBlock(tb.node)) block(tb, sel, block++);
            else run = inline(tb, run);
        }
        for (var k = block; k < boxes.size(); k++) { boxes.get(k).setVisible(false); tools.get(k).setVisible(false); }
        for (var k = run; k < inline.size(); k++) inline.get(k).setVisible(false);
    }

    /**
     * Shows the fence lines of the fenced block the selection touches and shrinks the others. The runs are rebuilt
     * rather than restyled: JavaFX does not measure a line again when only a font size changes under it.
     */
    void sync(Positions positions, Selection sel) {
        for (var tb : positions.textblocks) {
            if (!Code.isBlock(tb.node)) continue;
            var open = sel.getFrom() <= end(tb) && sel.getTo() >= tb.start;
            if (open == Boolean.TRUE.equals(tb.flow.getProperties().get("pm-open"))) continue;
            tb.flow.getProperties().put("pm-open", open);
            tb.flow.getChildren().setAll(DocRenderer.runs(tb.node, open));
        }
    }

    void block(BlockView tb, Selection sel, int slot) {
        var scene = tb.flow.localToScene(tb.flow.getLayoutBounds());
        var b = back.sceneToLocal(scene);
        var box = box(slot);
        box.setVisible(true);
        box.resizeRelocate(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
        var bar = tools(slot);
        bar.setUserData(tb.start - 1);
        ((Label) bar.getChildren().getFirst()).setText(Code.language(tb.node));
        bar.setVisible(true);
        bar.applyCss();
        bar.autosize();
        var f = front.sceneToLocal(scene);
        bar.relocate(f.getMaxX() - bar.getWidth() - 8, f.getMinY() + 4);
    }

    int inline(BlockView tb, int slot) {
        var offset = 0;
        for (var c : tb.node.getContent().getContent()) {
            var size = c.getNodeSize();
            if (c.isText() && CODE.isInSet(c.getMarks()) != null)
                for (var r : tb.flow.getLayoutInfo().getSelectionGeometry(offset, offset + size, true)) {
                    var local = new BoundingBox(r.getMinX(), r.getMinY(), r.getWidth(), r.getHeight());
                    var o = back.sceneToLocal(tb.flow.localToScene(local));
                    var box = inlineBox(slot++);
                    box.setVisible(true);
                    box.resizeRelocate(o.getMinX() - 2, o.getMinY() + 1, o.getWidth() + 4, o.getHeight() - 2);
                    runs.add(new Run(front.sceneToLocal(tb.flow.localToScene(local)), c.getText()));
                }
            offset += size;
        }
        return slot;
    }

    /** The copy button for the inline code run under the pointer; gone when the pointer is on neither the run nor the button. */
    void hover(javafx.geometry.Point2D p) {
        for (var r : runs) {
            if (!r.at().contains(p.getX(), p.getY())) continue;
            hoverCopy.setText("copy");
            hoverCopy.setUserData(r.text());
            hoverCopy.setVisible(true);
            hoverCopy.applyCss();
            hoverCopy.autosize();
            hoverCopy.relocate(r.at().getMaxX() - hoverCopy.getWidth(), r.at().getMinY() - hoverCopy.getHeight() - 1);
            return;
        }
        if (!hoverCopy.isVisible() || !hoverCopy.getBoundsInParent().contains(p)) hoverCopy.setVisible(false);
    }

    static void copy(Label button, String text, String label) {
        var content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        button.setText("copied");
        var back = new PauseTransition(Duration.millis(1200));
        back.setOnFinished(e -> button.setText(label));
        back.play();
    }

    Region box(int slot) {
        while (boxes.size() <= slot) {
            var r = new Region();
            r.getStyleClass().add("pm-code-box");
            r.setManaged(false);
            boxes.add(r);
            back.getChildren().add(r);
        }
        return boxes.get(slot);
    }

    Region inlineBox(int slot) {
        while (inline.size() <= slot) {
            var r = new Region();
            r.getStyleClass().add("pm-code-inline");
            r.setManaged(false);
            inline.add(r);
            back.getChildren().add(r);
        }
        return inline.get(slot);
    }

    HBox tools(int slot) {
        while (tools.size() <= slot) {
            var language = new Label();
            language.getStyleClass().add("pm-code-language");
            var copy = new Label("Copy");
            copy.getStyleClass().add("pm-code-copy");
            var bar = new HBox(language, copy);
            bar.getStyleClass().add("pm-code-tools");
            bar.setManaged(false);
            bar.setPickOnBounds(false);
            language.setMouseTransparent(true);
            copy.setOnMousePressed(e -> e.consume());
            copy.setOnMouseClicked(e -> {
                var node = view.model.state().getDoc().nodeAt((Integer) bar.getUserData());
                if (node != null) copy(copy, Code.text(node), "Copy");
                e.consume();
            });
            tools.add(bar);
            front.getChildren().add(bar);
        }
        return tools.get(slot);
    }
}
