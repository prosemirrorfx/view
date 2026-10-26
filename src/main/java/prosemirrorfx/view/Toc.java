package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import java.util.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

import static prosemirrorfx.core.pm.Pm.intAttr;
import static prosemirrorfx.core.schema.EditorSchema.HEADING;

/**
 * An optional, navigable table of contents: the document's headings, indented by level, with the
 * section the caret is in highlighted. The toolbar's Contents button shows it and moves the focus into it;
 * Up/Down choose, Enter or a click jumps there and returns to the text (the panel stays open); Esc goes back
 * without moving; the button again hides it. Place it wherever suits, e.g. borderPane.left(new Toc(view)).
 */
public final class Toc extends VBox {

    public record Entry(int level, String text, int pos) {
        @Override public String toString() { return text.isBlank() ? "(untitled)" : text; }
    }

    final DocView view;
    final ListView<Entry> list = new ListView<>();
    boolean following;

    public Toc(DocView view) {
        this.view = view;
        view.toc = this;
        view.follow(this);
        getStyleClass().add("pm-toc");
        getStylesheets().add(Toc.class.getResource("editor.css").toExternalForm());
        var title = new Label("CONTENTS");
        title.getStyleClass().add("pm-toc-title");
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(title, list);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Entry e, boolean empty) {
                super.updateItem(e, empty);
                setPrefWidth(0);
                setTextOverrun(OverrunStyle.ELLIPSIS);
                getStyleClass().removeIf(c -> c.startsWith("pm-toc-h"));
                setText(empty || e == null ? null : e.toString());
                setStyle(empty || e == null ? null : "-fx-padding: 4 12 4 " + (12 + 14 * (e.level() - 1)) + ";");
                if (!empty && e != null) getStyleClass().add("pm-toc-h" + e.level());
            }
        });
        list.addEventFilter(KeyEvent.KEY_PRESSED, this::key);
        list.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> { if (e.getButton() == MouseButton.PRIMARY) jump(); });
        refresh();
        view.model.onChange((old, now) -> {
            if (old.getDoc() != now.getDoc()) refresh();
            follow();
        });
        setVisible(false);
        setManaged(false);
    }

    public boolean showing() { return isVisible(); }

    /** The Contents button: show it with the focus in it, or hide it. */
    public void toggle() {
        if (isVisible()) { hide(); return; }
        setVisible(true);
        setManaged(true);
        follow();
        list.requestFocus();
    }

    void hide() {
        setVisible(false);
        setManaged(false);
        view.requestFocus();
    }

    void key(KeyEvent e) {
        switch (e.getCode()) {
            case ENTER -> jump();
            case ESCAPE -> view.requestFocus();
            default -> { return; }
        }
        e.consume();
    }

    void jump() {
        var entry = list.getSelectionModel().getSelectedItem();
        if (entry == null) return;
        view.revealAtTop = true;
        view.model.select(prosemirrorfx.core.pm.Pm.cursor(view.model.state().getDoc(), entry.pos()));
        view.requestFocus();
    }

    void refresh() {
        var now = entries(view.model.state().getDoc());
        if (!now.equals(list.getItems())) list.getItems().setAll(now);
    }

    /** Highlight the section the caret is in: the last heading at or before it. */
    void follow() {
        if (!isVisible()) return;
        var head = view.model.state().getSelection().getHead();
        var items = list.getItems();
        var k = -1;
        for (var i = 0; i < items.size() && items.get(i).pos() <= head; i++) k = i;
        if (k < 0) list.getSelectionModel().clearSelection();
        else if (list.getSelectionModel().getSelectedIndex() != k) {
            list.getSelectionModel().select(k);
            list.scrollTo(Math.max(0, k - 3));
        }
    }

    public static List<Entry> entries(Node doc) {
        var out = new ArrayList<Entry>();
        doc.descendants((n, pos, parent, i) -> {
            if (n.getType() == HEADING) out.add(new Entry(intAttr(n, "level"), n.getTextContent(), pos + 1));
            return !n.isTextblock();
        });
        return out;
    }
}
