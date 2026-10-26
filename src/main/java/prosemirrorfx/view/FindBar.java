package prosemirrorfx.view;

import java.util.List;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import prosemirrorfx.core.editor.Find;

/**
 * Find and replace, floating at the top right of the editor. Ctrl+F (or the toolbar button) opens it with the focus in
 * the find box. Enter finds the next match, Shift+Enter the previous; in the replace box Enter replaces
 * the current match and moves on, Ctrl+Enter replaces all (one undo step). Esc closes it and leaves the
 * current match selected in the text. Every match is shaded while the bar is open.
 */
final class FindBar extends HBox {

    final DocView view;
    final TextField find = new TextField();
    final TextField replace = new TextField();
    final Label count = new Label();
    List<Find.Match> matches = List.of();

    FindBar(DocView view) {
        this.view = view;
        getStyleClass().add("pm-find");
        setSpacing(6);
        setAlignment(Pos.CENTER_LEFT);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        StackPane.setAlignment(this, Pos.TOP_RIGHT);
        StackPane.setMargin(this, new Insets(8, 24, 0, 0));
        find.setPromptText("Find");
        replace.setPromptText("Replace with");
        find.setPrefColumnCount(14);
        replace.setPrefColumnCount(12);
        count.setMinWidth(64);
        getChildren().addAll(find, count, replace, button("Replace", this::replaceOne), button("All", this::replaceAll), button("✕", this::close));
        find.textProperty().addListener((o, a, b) -> { search(); jump(true, true); });
        find.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case ENTER -> jump(!e.isShiftDown(), false);
                case ESCAPE -> close();
                default -> { return; }
            }
            e.consume();
        });
        replace.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case ENTER -> { if (e.isControlDown()) replaceAll(); else replaceOne(); }
                case ESCAPE -> close();
                default -> { return; }
            }
            e.consume();
        });
        view.model.onChange((old, now) -> { if (isVisible() && old.getDoc() != now.getDoc()) search(); });
        setVisible(false);
    }

    Button button(String text, Runnable r) {
        var b = new Button(text);
        b.setMnemonicParsing(false);
        b.setFocusTraversable(false);
        b.setOnAction(e -> r.run());
        return b;
    }

    /** Open with the selected text (if any, on one line) as the query, focus in the find box. */
    void open() {
        var sel = view.model.state().getSelection();
        if (!sel.getEmpty()) {
            var picked = view.model.state().getDoc().textBetween(sel.getFrom(), sel.getTo(), "\n", "\n");
            if (!picked.contains("\n")) find.setText(picked);
        }
        setVisible(true);
        search();
        find.requestFocus();
        find.selectAll();
    }

    void close() {
        setVisible(false);
        matches = List.of();
        view.highlights = List.of();
        view.invalidate();
        view.requestFocus();
    }

    void search() {
        matches = Find.all(view.model.state().getDoc(), find.getText());
        view.highlights = matches;
        view.invalidate();
        showCount();
    }

    void showCount() {
        var sel = view.model.state().getSelection();
        var k = matches.indexOf(new Find.Match(sel.getFrom(), sel.getTo()));
        count.setText(find.getText().isEmpty() ? "" : matches.isEmpty() ? "no match" : (k < 0 ? "" : (k + 1) + " of ") + matches.size());
    }

    /** Select the next (or previous) match; from the caret's own match when the query is still being typed. */
    void jump(boolean forward, boolean fromStart) {
        var sel = view.model.state().getSelection();
        var m = Find.next(matches, forward ? (fromStart ? sel.getFrom() : sel.getTo()) : sel.getFrom(), forward);
        if (m != null) view.model.dispatch(Find.selectMatch(view.model.state(), m));
        showCount();
    }

    Find.Match current() {
        var sel = view.model.state().getSelection();
        var m = new Find.Match(sel.getFrom(), sel.getTo());
        return matches.contains(m) ? m : null;
    }

    void replaceOne() {
        var m = current();
        if (m == null) { jump(true, true); return; }
        view.model.dispatch(Find.replace(view.model.state(), m, replace.getText()));
        jump(true, true);
    }

    void replaceAll() {
        if (matches.isEmpty()) return;
        view.model.dispatch(Find.replaceAll(view.model.state(), matches, replace.getText()));
        showCount();
    }
}
