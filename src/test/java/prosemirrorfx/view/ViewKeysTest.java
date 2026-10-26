package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import prosemirrorfx.core.editor.EditorModel;

import static javafx.scene.input.KeyCode.*;
import static org.junit.jupiter.api.Assertions.*;
import static prosemirrorfx.view.D.*;
import static prosemirrorfx.core.schema.Build.*;

/**
 * The editor driven end to end on JavaFX's headless platform: real key and mouse events go to the
 * focused DocView, and after each step both the document and what is drawn are checked.
 */
class ViewKeysTest {

    static Stage stage;
    DocView view;
    EditorModel m;
    BorderPane root;
    Toc toc;

    @BeforeAll static void boot() throws Exception {
        FxBoot.start();
        fx(() -> stage = new Stage());
    }

    void open(Node... blocks) {
        fx(() -> {
            m = model(blocks);
            view = new DocView(m);
            toc = new Toc(view);
            root = new BorderPane(view, Toolbar.of(view), null, StatusBar.of(view), toc);
            stage.setScene(new Scene(root, 800, 600));
            stage.show();
            view.requestFocus();
        });
        pulse();
    }

    javafx.scene.paint.Paint pageColour() { return ((javafx.scene.layout.Region) view.scroll.lookup(".viewport")).getBackground().getFills().getFirst().getFill(); }

    javafx.scene.paint.Paint textColour() { return ((Text) flows().getFirst().getChildren().getFirst()).getFill(); }

    javafx.scene.paint.Paint labelColour(String selector) { return ((javafx.scene.control.Label) root.lookup(selector)).getTextFill(); }

    @Test void cutCopyAndPasteCanBeCalledFromAHostToolbar() {
        open(p("one [two] three"));
        assertTrue(fx(view::copy));
        assertEquals("two", fx(() -> Clipboard.getSystemClipboard().getString()));
        assertTrue(fx(view::cut));
        expect("p(\"one | three\")");
        assertTrue(fx(view::paste));
        expect("p(\"one two| three\")");
        putHtml("bold word", "<b>bold</b> word");
        assertTrue(fx(view::pastePlain));
        expect("p(\"one twobold word| three\")");
        fx(() -> view.model().select(prosemirrorfx.core.pm.Pm.cursor(m.state().getDoc(), 3)));
        assertFalse(fx(view::copy), "nothing selected, nothing copied");
        assertFalse(fx(view::cut));
    }

    @Test void theLightColoursApplyUntilAThemeIsSet() {
        open(p("light|"));
        assertEquals(Theme.LIGHT.color("bg"), fx(this::pageColour));
        assertEquals(Theme.LIGHT.color("text"), fx(this::textColour));
        assertEquals(Theme.LIGHT.color("caret"), fx(() -> view.caretLayer.caret.getFill()));
    }

    @Test void aThemeRestylesThePageTheDrawnShapesAndTheToolbarStatusBarAndContents() {
        open(h(1, "Title"), p("dark|"));
        fx(() -> toc.toggle());
        clickTool(EditorAction.LINES);
        fx(() -> view.setTheme(Theme.DARK));
        pulse();
        assertEquals(Theme.DARK.color("bg"), fx(this::pageColour));
        assertEquals(Theme.DARK.color("text"), fx(this::textColour));
        assertEquals(Theme.DARK.color("caret"), fx(() -> view.caretLayer.caret.getFill()));
        assertEquals(Theme.DARK.color("muted"), fx(() -> labelColour(".pm-status")), "the status bar made from the view follows it");
        assertEquals(Theme.DARK.color("muted"), fx(() -> labelColour(".pm-toc-title")), "so does the contents panel");
        assertTrue(fx(() -> visibleNumbers().stream().allMatch(t -> Theme.DARK.color("number").equals(t.getFill()))), "line numbers are drawn in code and follow too");
        fx(() -> view.setTheme(Theme.LIGHT));
        pulse();
        assertEquals(Theme.LIGHT.color("bg"), fx(this::pageColour));
        assertEquals(Theme.LIGHT.color("muted"), fx(() -> labelColour(".pm-status")));
    }

    @Test void aSelectionDrawnAfterAThemeChangeUsesTheNewColour() {
        open(p("some words here|"));
        fx(() -> view.setTheme(Theme.DARK));
        press(LEFT, false, true);
        press(LEFT, false, true);
        var shades = fx(() -> overlayChildren().stream().filter(n -> n.isVisible() && n instanceof javafx.scene.shape.Rectangle r && Theme.DARK.color("selection").equals(r.getFill())).count());
        assertTrue(shades > 0, "the selection is drawn in the dark theme's colour");
    }

    @Test void movingToAnotherWindowLeavesNothingBehindInTheOldScene() {
        open(p("moved|"));
        var second = fx(() -> new Stage());
        fx(() -> {
            root.setCenter(null);
            second.setScene(new Scene(new BorderPane(view), 400, 300));
            second.show();
            view.requestFocus();
        });
        pulse();
        var before = fx(() -> view.layoutPasses);
        pulse();
        var passes = fx(() -> view.layoutPasses) - before;
        fx(second::hide);
        assertEquals(1, passes, "one pulse paints the view once, not once per scene it has been in");
    }

    @Test void backspaceAndDeleteInsideALine() {
        open(p("hello|"));
        press(BACK_SPACE);
        press(BACK_SPACE);
        expect("p(\"hel|\")");
        press(LEFT);
        press(LEFT);
        press(DELETE);
        expect("p(\"h|l\")");
    }

    @Test void typeThenEraseEverything() {
        open(p("|"));
        type("abc");
        for (var k = 0; k < 5; k++) press(BACK_SPACE);
        expect("p(\"|\")");
    }

    @Test void enterThenBackspaceJoins() {
        open(p("ab|cd"));
        press(ENTER);
        expect("p(\"ab\"), p(\"|cd\")");
        press(BACK_SPACE);
        expect("p(\"ab|cd\")");
    }

    @Test void deleteAtEndJoinsNextParagraph() {
        open(p("ab|"), p("cd"));
        press(DELETE);
        expect("p(\"ab|cd\")");
    }

    @Test void selectAllThenBackspace() {
        open(p("one"), p("two|"));
        press(A, true, false);
        press(BACK_SPACE);
        expect("p(\"|\")");
    }

    @Test void shiftSelectionThenType() {
        open(p("abc|"));
        press(LEFT, false, true);
        press(LEFT, false, true);
        type("X");
        expect("p(\"aX|\")");
    }

    @Test void listEditingByKeys() {
        open(p("|"));
        type("- one");
        press(ENTER);
        type("two");
        press(TAB);
        expect("ul(li(p(\"one\"), ul(li(p(\"two|\")))))");
        press(BACK_SPACE);
        expect("ul(li(p(\"one\"), ul(li(p(\"tw|\")))))");
        press(HOME);
        press(BACK_SPACE);
        expect("ul(li(p(\"one|tw\")))");
        press(BACK_SPACE);
        press(BACK_SPACE);
        expect("ul(li(p(\"o|tw\")))");
        press(HOME);
        press(BACK_SPACE);
        expect("p(\"|otw\")");
    }

    @Test void standardShortcutsAndUndo() {
        open(p("[word]"));
        press(B, true, false);
        expect("p(\"[**word**]\")");
        press(DIGIT8, true, true);
        expect("ul(li(p(\"[**word**]\")))");
        press(DIGIT7, true, true);
        expect("ol(li(p(\"[**word**]\")))");
        press(Z, true, false);
        // the two list changes came within 500 ms, so history groups them into one undo step
        expect("p(\"[**word**]\")");
        press(Y, true, false);
        expect("ol(li(p(\"[**word**]\")))");
        press(BACK_SLASH, true, false);
        expect("ol(li(p(\"[word]\")))");
    }

    @Test void ctrlBackslashClearsFormatting() {
        open(p("[word]"));
        press(B, true, false);
        expect("p(\"[**word**]\")");
        press(BACK_SLASH, true, false);
        expect("p(\"[word]\")");
    }

    @Test void noFunctionKeysInTheEditor() {
        open(p("[word]"));
        for (var f : List.of(F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12)) press(f);
        expect("p(\"[word]\")");
    }

    @Test void shiftEnterBreaksTheLineAndCtrlBracketsIndent() {
        open(ul(li(p("one")), li(p("two|"))));
        press(CLOSE_BRACKET, true, false);
        expect("ul(li(p(\"one\"), ul(li(p(\"two|\")))))");
        press(OPEN_BRACKET, true, false);
        expect("ul(li(p(\"one\")), li(p(\"two|\")))");
        open(p("ab|cd"));
        press(ENTER, false, true);
        assertEquals(1, fx(() -> m.state().getDoc().getChildCount()), "Shift+Enter stays in the paragraph");
        assertEquals("ab\ncd", fx(() -> m.state().getDoc().textBetween(0, m.state().getDoc().getContent().getSize(), "", "\n")));
    }

    @Test void toolbarButtonsWorkAndShowState() {
        open(p("[word]"));
        fx(() -> tool(EditorAction.ITALIC).fire());
        pulse();
        expect("p(\"[_word_]\")");
        assertTrue(fx(() -> ((javafx.scene.control.ToggleButton) tool(EditorAction.ITALIC)).isSelected()), "italic shows pressed");
        assertFalse(fx(() -> ((javafx.scene.control.ToggleButton) tool(EditorAction.BOLD)).isSelected()));
        fx(() -> tool(EditorAction.BULLETS).fire());
        pulse();
        expect("ul(li(p(\"[_word_]\")))");
        assertTrue(fx(() -> view.isFocused()), "focus returns to the editor after a toolbar click");
        assertFalse(fx(() -> tool(EditorAction.UNDO).isDisable()), "undo is offered after an edit");
        assertTrue(fx(() -> tool(EditorAction.REDO).isDisable()), "nothing to redo yet");
        assertEquals("Bold (Ctrl+B)", fx(() -> tool(EditorAction.BOLD).getTooltip().getText()));
        fx(() -> tool(EditorAction.STYLE).fire());
        pulse();
        var menu = fx(() -> javafx.stage.Window.getWindows().stream().filter(w -> w instanceof ContextMenu && w.isShowing()).map(w -> (ContextMenu) w).findFirst().orElseThrow());
        fx(() -> menu.getItems().get(0).fire());
        pulse();
        expect("ul(li(p(\"[_word_]\")))");
        fx(menu::hide);
    }

    @Test void clickPlacesCaretThenBackspace() {
        open(p("first line"), p("second line|"));
        click(0, true);
        press(BACK_SPACE);
        expect("p(\"first lin|\"), p(\"second line\")");
    }

    @Test void ctrlZUndoesTypingAndCtrlYRedoes() {
        open(p("|"));
        type("hello");
        press(Z, true, false);
        expect("p(\"|\")");
        press(Y, true, false);
        expect("p(\"hello|\")");
        press(Z, true, false);
        press(Z, true, true);
        expect("p(\"hello|\")");
    }

    @Test void copyPasteKeepsFormattingInsideTheEditor() {
        open(ul(li(p("[one")), li(p("two]"))), p(""));
        expect("ul(li(p(\"[one\")), li(p(\"two]\"))), p(\"\")");
        press(C, true, false);
        assertEquals("one\ntwo", fx(() -> Clipboard.getSystemClipboard().getString()));
        var html = fx(() -> Clipboard.getSystemClipboard().getHtml());
        assertTrue(html.contains("<ul><li><p>one</p></li>"), html);
        press(DOWN);
        press(DOWN);
        press(V, true, false);
        expect("ul(li(p(\"one\")), li(p(\"two\"))), ul(li(p(\"one\")), li(p(\"two|\")))");
    }

    @Test void cutAndPasteBoldWord() {
        open(p("a ", b("[bold]"), " c"));
        press(X, true, false);
        expect("p(\"a | c\")");
        press(END);
        press(V, true, false);
        expect("p(\"a  c**bold**|\")");
    }

    @Test void pasteOutsideTextLineByLine() {
        open(p("x|"));
        fx(() -> { var c = new ClipboardContent(); c.putString("first\r\nsecond"); Clipboard.getSystemClipboard().setContent(c); });
        press(V, true, false);
        expect("p(\"xfirst\"), p(\"second|\")");
    }

    static void putHtml(String text, String html) {
        fx(() -> { var c = new ClipboardContent(); c.putString(text); c.putHtml(html); Clipboard.getSystemClipboard().setContent(c); });
    }

    @Test void pasteFormattedWordsIntoALine() {
        open(p("say |now"));
        putHtml("very bold", "<span>very </span><b>bold</b>");
        press(V, true, false);
        expect("p(\"say very **bold**|now\")");
    }

    @Test void pasteHeadingAndListIntoAnEmptyLine() {
        open(p("intro"), p("|"));
        putHtml("Plan\none\ntwo", "<h2>Plan</h2><ul><li>one</li><li><i>two</i></li></ul>");
        press(V, true, false);
        expect("p(\"intro\"), h2(\"Plan\"), ul(li(p(\"one\")), li(p(\"_two_|\")))");
    }

    @Test void pasteParagraphsSplitsTheLine() {
        open(p("ab|cd"));
        putHtml("x\ny", "<p>x</p><p><b>y</b></p>");
        press(V, true, false);
        expect("p(\"abx\"), p(\"**y**|cd\")");
    }

    @Test void ctrlBackspaceAndCtrlArrowsWorkByWord() {
        open(p("one two three|"));
        press(BACK_SPACE, true, false);
        expect("p(\"one two |\")");
        press(LEFT, true, false);
        expect("p(\"one |two \")");
        press(DELETE, true, false);
        expect("p(\"one |\")");
        press(HOME, true, false);
        expect("p(\"|one \")");
    }

    @Test void hashesUpToSixMakeHeadings() {
        open(p("|"));
        type("#### four");
        expect("h4(\"four|\")");
        assertTrue(fx(() -> flows().getFirst().getStyleClass().contains("pm-h4")));
    }

    @Test void selectingReusesTheSameShapes() {
        open(p("one two three"), p("four five six|"));
        press(LEFT, false, true);
        var shades = fx(this::overlayChildren);
        for (var k = 0; k < 10; k++) press(LEFT, false, true);
        press(UP, false, true);
        var after = fx(this::overlayChildren);
        assertTrue(shades.size() >= 2, "caret and at least one selection shape");
        assertTrue(after.containsAll(shades), "shapes are reused, not recreated, as the selection grows");
        assertTrue(after.stream().allMatch(n -> !(n instanceof javafx.scene.shape.Rectangle r) || r.getFill() != null && !r.getFill().equals(javafx.scene.paint.Color.BLACK)),
                "no shape is drawn with the default black fill");
    }

    @Test void styleButtonOpensMenuAndADigitPicksTheStyle() {
        open(p("title|"));
        clickTool(EditorAction.STYLE);
        var menu = fx(() -> javafx.stage.Window.getWindows().stream().filter(w -> w instanceof ContextMenu && w.isShowing()).map(w -> (ContextMenu) w).findFirst().orElseThrow());
        assertEquals(7, fx(() -> menu.getItems().size()));
        fx(() -> Event.fireEvent(menu.getScene().getRoot(), new KeyEvent(KeyEvent.KEY_PRESSED, "5", "5", DIGIT5, false, false, false, false)));
        pulse();
        expect("h5(\"title|\")");
        assertFalse(fx(menu::isShowing));
        assertTrue(fx(() -> view.isFocused()));
        assertEquals("Heading 5", fx(() -> tool(EditorAction.STYLE).getText()));
        clickTool(EditorAction.STYLE);
        var again = fx(() -> javafx.stage.Window.getWindows().stream().filter(w -> w instanceof ContextMenu && w.isShowing()).map(w -> (ContextMenu) w).findFirst().orElseThrow());
        fx(() -> Event.fireEvent(again.getScene().getRoot(), new KeyEvent(KeyEvent.KEY_PRESSED, "0", "0", DIGIT0, false, false, false, false)));
        pulse();
        expect("p(\"title|\")");
        assertEquals("Normal text", fx(() -> tool(EditorAction.STYLE).getText()));
    }

    @Test void styleButtonFollowsTheCaret() {
        open(h(2, "head"), p("body|"));
        assertEquals("Normal text", fx(() -> tool(EditorAction.STYLE).getText()));
        press(UP);
        assertEquals("Heading 2", fx(() -> tool(EditorAction.STYLE).getText()));
    }

    @Test void ctrlAltDigitSetsStyleWithoutTyping() {
        open(p("x|"));
        fx(() -> {
            var t = stage.getScene().getFocusOwner();
            Event.fireEvent(t, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", DIGIT3, false, true, true, false));
            Event.fireEvent(t, new KeyEvent(KeyEvent.KEY_TYPED, "3", "", KeyCode.UNDEFINED, false, true, true, false));
        });
        pulse();
        expect("h3(\"x|\")");
    }

    @Test void headingNotOfferedWhereTheSchemaForbidsIt() {
        open(ul(li(p("item|"))));
        clickTool(EditorAction.STYLE);
        var menu = fx(() -> javafx.stage.Window.getWindows().stream().filter(w -> w instanceof ContextMenu && w.isShowing()).map(w -> (ContextMenu) w).findFirst().orElseThrow());
        assertTrue(fx(() -> menu.getItems().get(1).isDisable()), "a list item must start with a paragraph");
        fx(() -> Event.fireEvent(menu.getScene().getRoot(), new KeyEvent(KeyEvent.KEY_PRESSED, "1", "1", DIGIT1, false, false, false, false)));
        pulse();
        expect("ul(li(p(\"item|\")))");
        fx(menu::hide);
    }

    @Test void lineNumbersButtonNumbersEveryBlockOnceEvenWhenItWraps() {
        var longText = "word ".repeat(80);
        open(h(1, "Title"), p(longText), ul(li(p("bullet"))), p("end|"));
        clickTool(EditorAction.LINES);
        var numbers = fx(() -> visibleNumbers());
        var drawn = fx(() -> flows().stream().mapToInt(f -> f.getLayoutInfo().getTextLines(false).size()).sum());
        assertTrue(drawn > 4, "the long paragraph wraps");
        assertEquals(4, numbers.size(), "a wrapped paragraph is one line");
        assertEquals("1", numbers.getFirst().getText());
        assertEquals("4", numbers.getLast().getText());
        // the long paragraph's number is on its first drawn line, not in its middle
        var second = fx(() -> flows().get(1).localToScene(flows().get(1).getLayoutBounds()));
        var y2 = fx(() -> numbers.get(1).localToScene(0, numbers.get(1).getY()).getY());
        assertTrue(y2 < second.getMinY() + second.getHeight() / 4, "number at the top of its block: " + y2 + " in " + second);
        // each number sits on its line: the last one inside the last block's bounds
        var last = fx(() -> flows().getLast().localToScene(flows().getLast().getLayoutBounds()));
        var y = fx(() -> numbers.getLast().localToScene(0, numbers.getLast().getY()).getY());
        assertTrue(y > last.getMinY() && y <= last.getMaxY() + 1, "number baseline " + y + " within " + last);
        clickTool(EditorAction.LINES);
        assertTrue(fx(this::visibleNumbers).isEmpty());
    }

    @Test void contentsButtonOpensContentsAndEnterJumpsToTheHeading() {
        open(h(1, "Intro"), p("text"), h(2, "Details"), p("more"), h(1, "End"), p("last|"));
        clickTool(EditorAction.CONTENTS);
        assertTrue(fx(() -> toc.showing()));
        var list = fx(() -> (javafx.scene.control.ListView<?>) toc.lookup(".list-view"));
        assertTrue(fx(list::isFocused), "the Contents button moves the focus into the contents");
        assertEquals(List.of("Intro", "Details", "End"), fx(() -> list.getItems().stream().map(Object::toString).toList()));
        assertEquals(2, fx(() -> list.getSelectionModel().getSelectedIndex()), "the caret's section is highlighted");
        press(UP);
        press(ENTER);
        expect("h1(\"Intro\"), p(\"text\"), h2(\"|Details\"), p(\"more\"), h1(\"End\"), p(\"last\")");
        assertTrue(fx(() -> view.isFocused()), "Enter returns to the text");
        type("More ");
        assertEquals("More Details", fx(() -> list.getItems().get(1).toString()), "contents follow edits");
        clickTool(EditorAction.CONTENTS);
        assertFalse(fx(() -> toc.showing()), "the button again hides it");
        assertTrue(fx(() -> view.isFocused()));
    }

    @Test void ctrlFFindsReplacesAndReplacesAllInOneUndo() {
        open(p("The cat sat."), p("A cat and a CAT.|"));
        press(F, true, false);
        var bar = fx(() -> (javafx.scene.layout.HBox) view.lookup(".pm-find"));
        assertTrue(fx(bar::isVisible));
        var fields = fx(() -> bar.getChildren().stream().filter(n -> n instanceof javafx.scene.control.TextField).map(n -> (javafx.scene.control.TextField) n).toList());
        var findBox = fields.get(0);
        var replaceBox = fields.get(1);
        assertTrue(fx(findBox::isFocused));
        fx(() -> findBox.setText("cat"));
        pulse();
        expect("p(\"The [cat] sat.\"), p(\"A cat and a CAT.\")");
        assertEquals("1 of 3", fx(() -> ((javafx.scene.control.Label) bar.getChildren().get(1)).getText()));
        assertEquals(3, fx(() -> matchShades().size()), "every match is shaded");
        press(ENTER);
        expect("p(\"The cat sat.\"), p(\"A [cat] and a CAT.\")");
        fx(() -> replaceBox.setText("dog"));
        fx(replaceBox::requestFocus);
        press(ENTER);
        expect("p(\"The cat sat.\"), p(\"A dog and a [CAT].\")");
        fx(() -> Event.fireEvent(replaceBox, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", ENTER, false, true, false, false)));
        pulse();
        assertEquals("p(\"The dog sat.\"), p(\"A dog and a dog|.\")", fx(() -> show(m)));
        press(ESCAPE);
        assertFalse(fx(bar::isVisible));
        assertTrue(fx(() -> view.isFocused()));
        assertTrue(fx(this::matchShades).isEmpty(), "shading goes when the bar closes");
        press(Z, true, false);
        assertEquals("p(\"The cat sat.\"), p(\"A dog and a CAT.\")", fx(() -> show(m).replaceAll("[|\\[\\]]", "")), "replace all is one undo step");
    }

    @Test void statusLineCountsWords() {
        open(p("one two three"), ul(li(p("four five|"))));
        assertTrue(fx(this::status).startsWith("5 words"), fx(this::status));
        press(LEFT, true, true);
        assertTrue(fx(this::status).startsWith("5 words · 1 selected"), fx(this::status));
        type("x");
        assertTrue(fx(this::status).startsWith("5 words"), fx(this::status));
    }

    @Test void ctrlPlusAndMinusZoomTheText() {
        open(h(1, "Big"), p("small|"));
        double before = fx(() -> ((Text) flows().get(1).getChildren().getFirst()).getFont().getSize());
        double h1Before = fx(() -> ((Text) flows().get(0).getChildren().getFirst()).getFont().getSize());
        press(EQUALS, true, false);
        press(EQUALS, true, false);
        double after = fx(() -> ((Text) flows().get(1).getChildren().getFirst()).getFont().getSize());
        double h1After = fx(() -> ((Text) flows().get(0).getChildren().getFirst()).getFont().getSize());
        assertEquals(before * 1.2, after, 0.01);
        assertEquals(h1Before * 1.2, h1After, 0.05, "headings scale with the text");
        press(DIGIT0, true, false);
        assertEquals(before, fx(() -> ((Text) flows().get(1).getChildren().getFirst()).getFont().getSize()), 0.01);
    }

    @Test void fileSessionSavesAndReopens(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var file = dir.resolve("notes.md");
        java.nio.file.Files.writeString(file, "# Notes\n\n- first\n");
        open(p("|"));
        fx(() -> { new FileSession(view, file); root.setBottom(StatusBar.of(view)); });
        pulse();
        assertEquals("h1(\"|Notes\"), ul(li(p(\"first\")))", fx(() -> show(m)));
        press(END, true, false);
        press(ENTER);
        type("second");
        assertTrue(fx(this::status).contains("notes.md · edited"), fx(this::status));
        press(S, true, false);
        assertEquals("# Notes\n\n- first\n- second\n", java.nio.file.Files.readString(file));
        assertTrue(fx(this::status).contains("notes.md · saved"), fx(this::status));
        fx(() -> view.file().open(file));
        pulse();
        assertEquals("h1(\"|Notes\"), ul(li(p(\"first\")), li(p(\"second\")))", fx(() -> show(m)));
    }

    @Test void interimIsDrawnGreyButNeverPartOfTheDocument() {
        open(h(1, "Ti|tle"), ul(li(p("Said before."))));
        fx(() -> view.setInterim("still being"));
        pulse();
        assertEquals("h1(\"Ti|tle\"), ul(li(p(\"Said before.\")))", fx(() -> show(m)));
        var last = fx(() -> flows().getLast());
        assertEquals("Said before.  still being", fx(() -> last.getChildren().stream().map(t -> ((Text) t).getText()).reduce("", String::concat)));
        assertTrue(fx(() -> last.getChildren().getLast().getStyleClass().contains("pm-interim")));
        assertEquals("# Title\n\n- Said before.\n", prosemirrorfx.markdown.Markdown.write(fx(() -> m.state().getDoc())), "interim is not saved");
        assertTrue(fx(this::status).startsWith("3 words"), "interim is not counted");
        type("X");
        assertEquals("h1(\"TiX|tle\"), ul(li(p(\"Said before.\")))", fx(() -> show(m)), "typing elsewhere keeps the interim");
        assertTrue(fx(() -> flows().getLast().getChildren().getLast().getStyleClass().contains("pm-interim")));
        fx(() -> view.appendCommitted("Still being said."));
        pulse();
        assertEquals("h1(\"TiX|tle\"), ul(li(p(\"Said before.\")), li(p(\"Still being said.\")))", fx(() -> show(m)), "the commit becomes a bullet; the caret stays where the user was");
        assertTrue(fx(() -> flows().stream().flatMap(f -> f.getChildren().stream()).noneMatch(t -> t.getStyleClass().contains("pm-interim"))), "a commit clears the interim");
        press(Z, true, false);
        assertEquals("h1(\"TiXtle\"), ul(li(p(\"Said before.\")))", fx(() -> show(m).replace("|", "")), "undo takes back the commit first");
    }

    void ime(String committed, String composed) {
        var runs = composed.isEmpty() ? List.<InputMethodTextRun>of() : List.of(new InputMethodTextRun(composed, InputMethodHighlight.SELECTED_RAW));
        fx(() -> Event.fireEvent(view, new InputMethodEvent(InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, runs, committed, composed.length())));
        pulse();
    }

    long underlines() {
        return fx(() -> overlayChildren().stream().filter(n -> n.isVisible() && n instanceof javafx.scene.shape.Rectangle r && r.getHeight() == 1.5).count());
    }

    @Test void inputMethodComposesUnderlinedThenCommitsAsOneUndoStep() {
        open(p("சொல் |"));
        assertNotNull(fx(() -> view.getInputMethodRequests()), "the editor asks the platform for input methods");
        ime("", "வ");
        expect("p(\"சொல் வ|\")");
        assertTrue(underlines() > 0, "composing text is underlined");
        ime("", "வண");
        expect("p(\"சொல் வண|\")");
        ime("வணக்கம்", "");
        expect("p(\"சொல் வணக்கம்|\")");
        assertEquals(0, underlines(), "committed text is not underlined");
        press(Z, true, false);
        expect("p(\"சொல் |\")");
        press(Y, true, false);
        expect("p(\"சொல் வணக்கம்|\")");
    }

    @Test void inputMethodCancelLeavesNothingAndReplacesASelection() {
        open(p("a [old] b"));
        ime("", "न");
        expect("p(\"a न| b\")");
        ime("", "");
        expect("p(\"a | b\")");
        ime("", "नम");
        ime("नमस्ते", "");
        expect("p(\"a नमस्ते| b\")");
        press(Z, true, false);
        expect("p(\"a | b\")");
    }

    @Test void inputMethodCommitWhileStillComposingTheNextWord() {
        open(p("|"));
        ime("", "ab");
        ime("abc", "d");
        expect("p(\"abcd|\")");
        ime("d", "");
        expect("p(\"abcd|\")");
        assertEquals("abcd\n", prosemirrorfx.markdown.Markdown.write(fx(() -> m.state().getDoc())));
    }

    @Test void interimFromAnotherThread() throws Exception {
        open(p("|"));
        var t = new Thread(() -> { view.setInterim("hello"); view.appendCommitted("hello world."); });
        t.start();
        t.join();
        pulse();
        assertEquals("ul(li(p(\"hello world.|\")))", fx(() -> show(m)));
    }

    String status() {
        return fx(() -> ((javafx.scene.control.Label) root.lookup(".pm-status")).getText());
    }

    @Test void copyAllPutsTheWholeDocumentInEachFormat() {
        open(h(1, "Notes"), ul(li(p("one")), li(p("two"), ol(li(p("deep"))))), p("end|"));
        clickTool(EditorAction.COPY_ALL);
        assertEquals("Notes\n\n• one\n• two\n    1. deep\n\nend", fx(() -> Clipboard.getSystemClipboard().getString()));
        assertTrue(fx(() -> Clipboard.getSystemClipboard().getHtml()).startsWith("<h1>Notes</h1><ul><li><p>one</p></li>"));
        fx(() -> view.copyAll(CopyFormat.MARKDOWN));
        assertTrue(fx(() -> Clipboard.getSystemClipboard().getString()).startsWith("# Notes"));
        assertFalse(fx(() -> Clipboard.getSystemClipboard().hasHtml()), "source formats are text only");
        fx(() -> view.copyAll(CopyFormat.HTML));
        assertTrue(fx(() -> Clipboard.getSystemClipboard().getString()).startsWith("<h1>Notes</h1>"));
        expect("h1(\"Notes\"), ul(li(p(\"one\")), li(p(\"two\"), ol(li(p(\"deep\"))))), p(\"end|\")");
    }

    @Test void zoomStepsByTenAndTakesAnyTypedValue() {
        open(p("text|"));
        fx(() -> view.setZoom(1.15));
        assertEquals(1.15, fx(view::zoom), 1e-9);
        press(EQUALS, true, false);
        assertEquals(1.2, fx(view::zoom), 1e-9);
        press(MINUS, true, false);
        press(MINUS, true, false);
        assertEquals(1.0, fx(view::zoom), 1e-9);
        var box = fx(() -> (javafx.scene.control.ComboBox<?>) root.lookup(".pm-zoom"));
        assertEquals("100%", fx(() -> String.valueOf(box.getValue())));
        fx(() -> { box.getEditor().setText("137"); ((javafx.scene.control.ComboBox<String>) box).commitValue(); });
        pulse();
        assertEquals(1.37, fx(view::zoom), 1e-9);
        assertEquals("137%", fx(() -> String.valueOf(box.getValue())));
        fx(() -> view.setZoom(9));
        assertEquals(4, fx(view::zoom), 1e-9);
    }

    @Test void activeLineBandCoversTheWholeWrappedBlockAndTheHandleNamesItsStyle() {
        open(h(2, "Head|"), p("word ".repeat(80)));
        var band = fx(() -> root.lookup(".pm-active-line"));
        assertFalse(fx(band::isVisible), "off until asked for");
        var handle = fx(() -> (Button) root.lookup(".pm-block-handle"));
        assertTrue(fx(handle::isVisible));
        assertEquals("H2", fx(handle::getText));
        clickTool(EditorAction.ACTIVE_LINE);
        assertTrue(fx(band::isVisible));
        press(DOWN);
        assertEquals("¶", fx(handle::getText));
        var flow = fx(() -> flows().get(1).localToScene(flows().get(1).getLayoutBounds()));
        var b = fx(() -> band.localToScene(band.getLayoutBounds()));
        assertTrue(b.getMinY() <= flow.getMinY() && b.getMaxY() >= flow.getMaxY(), "band " + b + " covers every line of " + flow);
        fx(handle::fire);
        pulse();
        var menu = fx(() -> javafx.stage.Window.getWindows().stream().filter(w -> w instanceof ContextMenu && w.isShowing()).map(w -> (ContextMenu) w).findFirst().orElseThrow());
        fx(() -> Event.fireEvent(menu.getScene().getRoot(), new KeyEvent(KeyEvent.KEY_PRESSED, "3", "3", DIGIT3, false, false, false, false)));
        pulse();
        assertEquals("H3", fx(handle::getText));
        assertTrue(fx(view::isFocused), "the caret stays in the text");
    }

    List<javafx.scene.Node> matchShades() {
        return overlayChildren().stream().filter(n -> n.isVisible() && n instanceof javafx.scene.shape.Rectangle r && r.getFill().equals(javafx.scene.paint.Color.rgb(255, 196, 0, 0.35))).toList();
    }

    List<Text> visibleNumbers() {
        var page = (javafx.scene.layout.StackPane) view.lookup(".pm-page");
        return ((javafx.scene.layout.Pane) page.getChildren().get(2)).getChildren().stream()
                .filter(javafx.scene.Node::isVisible).map(n -> (Text) n).toList();
    }

    List<javafx.scene.Node> overlayChildren() {
        var page = (javafx.scene.layout.StackPane) view.lookup(".pm-page");
        return List.copyOf(((javafx.scene.layout.Pane) page.getChildren().get(3)).getChildren());
    }

    // ---- driving

    void press(KeyCode code) { press(code, false, false); }

    void press(KeyCode code, boolean ctrl, boolean shift) {
        fx(() -> {
            var target = stage.getScene().getFocusOwner();
            assertNotNull(target, "something has focus");
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, ctrl, false, false));
            var ch = code == BACK_SPACE ? "\b" : code == DELETE ? "\u007f" : code == ENTER ? "\r" : code == TAB ? "\t" : null;
            if (ch != null) Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_TYPED, ch, "", KeyCode.UNDEFINED, shift, ctrl, false, false));
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, shift, ctrl, false, false));
        });
        pulse();
    }

    void type(String s) {
        for (var c : s.codePoints().toArray()) {
            fx(() -> Event.fireEvent(stage.getScene().getFocusOwner(), new KeyEvent(KeyEvent.KEY_TYPED, Character.toString(c), "", KeyCode.UNDEFINED, false, false, false, false)));
            pulse();
        }
    }

    /** Click at the end of the given textblock (in drawing order). */
    void click(int flowIndex, boolean atEnd) {
        fx(() -> {
            var flow = flows().get(flowIndex);
            var b = flow.localToScene(flow.getLayoutBounds());
            var x = atEnd ? b.getMaxX() - 1 : b.getMinX() + 1;
            var page = view.lookup(".pm-page");
            for (var type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED))
                Event.fireEvent(page, new MouseEvent(type, x, b.getCenterY(), 0, 0, MouseButton.PRIMARY, 1,
                        false, false, false, false, true, false, false, false, false, false, null));
        });
        pulse();
    }

    javafx.scene.control.ButtonBase tool(EditorAction a) {
        return root.lookupAll(".pm-tool").stream().map(n -> (javafx.scene.control.ButtonBase) n)
                .filter(b -> b.getTooltip() != null && b.getTooltip().getText().startsWith(a.label)).findFirst().orElseThrow();
    }

    void clickTool(EditorAction a) {
        fx(() -> tool(a).fire());
        pulse();
    }

    // ---- checking

    void expect(String shorthand) {
        assertEquals(shorthand, fx(() -> show(m)));
        assertEquals(docTexts(), fx(this::drawnTexts), "what is drawn matches the document");
    }

    List<String> docTexts() {
        return fx(() -> {
            var out = new ArrayList<String>();
            m.state().getDoc().descendants((n, pos, parent, i) -> { if (n.isTextblock()) out.add(n.getTextContent()); return !n.isTextblock(); });
            return out;
        });
    }

    List<String> drawnTexts() {
        return flows().stream().map(f -> f.getChildren().stream().map(t -> ((Text) t).getText()).reduce("", String::concat)).toList();
    }

    List<TextFlow> flows() {
        return view.lookupAll("TextFlow").stream().map(n -> (TextFlow) n).toList();
    }

    // ---- fx thread

    /** Wait for a real frame: layout, then the editor's after-layout painting, has run. */
    static void pulse() {
        var done = new CountDownLatch(1);
        fx(() -> {
            var scene = stage == null ? null : stage.getScene();
            if (scene == null) { done.countDown(); return; }
            var once = new Runnable[1];
            once[0] = () -> { scene.removePostLayoutPulseListener(once[0]); done.countDown(); };
            scene.addPostLayoutPulseListener(once[0]);
            Platform.requestNextPulse();
        });
        try { done.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); }
        fx(() -> {});
    }

    static void fx(Runnable r) { fx(() -> { r.run(); return null; }); }

    static <T> T fx(Callable<T> c) {
        if (Platform.isFxApplicationThread()) {
            try { return c.call(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        var f = new FutureTask<>(c);
        Platform.runLater(f);
        try {
            return f.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error err) throw err;
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
