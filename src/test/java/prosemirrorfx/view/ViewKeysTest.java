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

    void open(Node... blocks) { showWith(() -> model(blocks)); }

    /** A document read from Markdown text, the way a file is loaded, with the caret at its end. */
    void openMarkdown(String md) { showWith(() -> new EditorModel(prosemirrorfx.markdown.Lossless.read(md))); }

    void showWith(java.util.function.Supplier<EditorModel> make) {
        fx(() -> {
            m = make.get();
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

    // ---- find options and the search API

    javafx.scene.control.ToggleButton optionButton(String text) {
        return fx(() -> view.lookupAll(".pm-find .pm-option").stream().map(n -> (javafx.scene.control.ToggleButton) n).filter(b -> b.getText().equals(text)).findFirst().orElseThrow());
    }

    void findFor(String query) {
        press(F, true, false);
        var box = (javafx.scene.control.TextField) fx(() -> view.lookup(".pm-find .text-field"));
        fx(() -> box.setText(query));
        pulse();
    }

    String findCount() { return fx(() -> ((javafx.scene.control.Label) view.lookup(".pm-find .label")).getText()); }

    @Test void theFindBarCanMatchCaseWholeWordAndRegex() {
        open(p("The cat sat. A CAT."), p("concat category|"));
        findFor("cat");
        assertEquals(4, fx(this::matchShades).size());
        fx(() -> optionButton("Aa").setSelected(true));
        pulse();
        assertEquals(3, fx(this::matchShades).size(), "match case drops CAT");
        fx(() -> optionButton("ab").setSelected(true));
        pulse();
        assertEquals(1, fx(this::matchShades).size(), "whole word as well leaves the lone cat");
        fx(() -> { optionButton("Aa").setSelected(false); optionButton("ab").setSelected(false); optionButton(".*").setSelected(true); });
        findFor("c.t");
        assertEquals(4, fx(this::matchShades).size());
    }

    @Test void anInvalidRegexIsNamedInTheBarAndShadesNothing() {
        open(p("some (text|"));
        fx(() -> view.find());
        fx(() -> optionButton(".*").setSelected(true));
        findFor("(");
        assertEquals("invalid", findCount());
        assertTrue(fx(this::matchShades).isEmpty());
    }

    @Test void altCAltWAndAltRFlipTheOptionsFromTheFindBox() {
        open(p("one|"));
        findFor("o");
        var box = fx(() -> view.lookup(".pm-find .text-field"));
        for (var code : List.of(C, W, R)) fx(() -> Event.fireEvent(box, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, true, false)));
        assertTrue(fx(() -> optionButton("Aa").isSelected() && optionButton("ab").isSelected() && optionButton(".*").isSelected()));
        assertEquals("o", fx(() -> ((javafx.scene.control.TextField) box).getText()), "the letters were not typed into the box");
    }

    @Test void replaceAllInTheBarExpandsGroupsInRegexMode() {
        open(p("mail ann@example and bob@test|"));
        fx(() -> view.find());
        fx(() -> optionButton(".*").setSelected(true));
        findFor("(\\w+)@(\\w+)");
        var replaceBox = (javafx.scene.control.TextField) fx(() -> view.lookupAll(".pm-find .text-field").stream().skip(1).findFirst().orElseThrow());
        fx(() -> { replaceBox.setText("$2 at $1"); replaceBox.requestFocus(); });
        fx(() -> Event.fireEvent(replaceBox, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", ENTER, false, true, false, false)));
        pulse();
        assertEquals("mail example at ann and test at bob", fx(() -> m.state().getDoc().textBetween(0, m.state().getDoc().getContent().getSize(), "", "")));
    }

    @Test void findAllGivesHitsWithLineAndColumnAndSelectHitScrollsToOne() {
        var blocks = new Node[60];
        for (var k = 0; k < blocks.length; k++) blocks[k] = p(k == 55 ? "the needle is here" : "filler paragraph number " + k);
        open(blocks);
        var hits = fx(() -> view.findAll("needle", prosemirrorfx.core.editor.Find.Options.PLAIN));
        assertEquals(1, hits.size());
        assertEquals("the needle is here", hits.getFirst().line());
        assertEquals(4, hits.getFirst().column());
        assertEquals(55, hits.getFirst().block());
        assertEquals(0.0, fx(() -> view.scroll.getVvalue()), 1e-9);
        fx(() -> view.selectHit(hits.getFirst()));
        pulse();
        pulse();
        assertTrue(fx(() -> view.scroll.getVvalue()) > 0.7, "the page scrolled down to the hit");
        assertEquals("needle", fx(() -> m.state().getDoc().textBetween(m.state().getSelection().getFrom(), m.state().getSelection().getTo(), "", "")));
    }

    @Test void replaceAllThroughTheApiCountsAndUndoesInOneStep() {
        open(p("a cat, a cat"), p("cat|"));
        var done = fx(() -> view.replaceAll("cat", prosemirrorfx.core.editor.Find.Options.PLAIN, "dog"));
        assertEquals(3, done);
        expect("p(\"a dog, a dog\"), p(\"dog|\")");
        press(Z, true, false);
        expect("p(\"a cat, a cat\"), p(\"cat|\")");
        assertEquals(0, fx(() -> view.replaceAll("zebra", prosemirrorfx.core.editor.Find.Options.PLAIN, "x")));
    }

    @Test void aHitInsideFoldedTextOpensTheFoldWhenSelected() {
        open(family());
        press(UP, true, false);
        assertEquals(List.of("parent", "sibling"), shownTexts());
        var hit = fx(() -> view.findAll("child two", prosemirrorfx.core.editor.Find.Options.PLAIN).getFirst());
        fx(() -> view.selectHit(hit));
        pulse();
        assertEquals(List.of("parent", "child one", "child two", "sibling"), shownTexts());
    }

    // ---- source lines

    List<String> numberTexts() { return fx(() -> visibleNumbers().stream().map(Text::getText).toList()); }

    @Test void lineNumbersAreTheFilesOwnAndAFoldShowsAsAJump() {
        openMarkdown("# One\n\ntext a\n\n## Two\n\ntext b\n\n# Three\n\nend\n");
        clickTool(EditorAction.LINES);
        assertEquals(List.of("1", "3", "5", "7", "9", "11"), numberTexts());
        fx(() -> view.setFolded(List.of("0")));
        pulse();
        assertEquals(List.of("1", "9", "11"), numberTexts(), "folding the first heading jumps from line 1 to line 9");
        fx(view::expandAll);
        pulse();
        assertEquals(List.of("1", "3", "5", "7", "9", "11"), numberTexts());
    }

    @Test void everySourceLineOfAParagraphThatWrapsInTheFileGetsItsOwnNumber() {
        openMarkdown("first\nsecond\nthird\n\nlast\n");
        clickTool(EditorAction.LINES);
        assertEquals(List.of("1", "2", "3", "5"), numberTexts());
    }

    @Test void theCaretLineAndOffsetFollowTheSourceAndGoToLinePutsTheCaretThere() {
        openMarkdown("# One\n\nsecond block\n\n- item\n");
        fx(() -> view.goToLine(2));
        pulse();
        assertEquals(2, fx(view::caretLine));
        assertEquals("# One\n\nsecond block\n\n- item\n".indexOf("second"), fx(view::caretSourceOffset));
        fx(() -> view.setCaretSourceOffset("# One\n\nsecond block\n\n- item\n".indexOf("item") + 2));
        assertEquals(4, fx(view::caretLine));
        fx(() -> view.goToSource(0, 2));
        assertEquals(0, fx(view::caretLine));
        assertEquals(2, fx(() -> view.sourceMap().column(m.state().getSelection().getHead())));
    }

    @Test void aSourceViewToggleCanKeepTheCaretLineThroughAnEdit() {
        openMarkdown("alpha\n\nbeta\n\ngamma\n");
        fx(() -> view.goToLine(4));
        type("X");
        assertEquals(4, fx(view::caretLine));
        assertEquals("alpha\n\nbeta\n\nXgamma\n", fx(() -> prosemirrorfx.markdown.Lossless.write(m.state().getDoc())));
        assertEquals(fx(() -> view.sourceMap().text()), fx(() -> prosemirrorfx.markdown.Lossless.write(m.state().getDoc())), "the map describes the edited text, not the loaded one");
    }

    // ---- code

    static final String FENCED = "intro\n\n```java\nint x;\n  y();\n```\n\nafter\n";

    List<javafx.scene.Node> visibleOf(String selector) {
        return fx(() -> view.lookupAll(selector).stream().filter(javafx.scene.Node::isVisible).toList());
    }

    double fenceSize() {
        return fx(() -> ((Text) view.lookupAll(".pm-fence").stream().filter(n -> n instanceof Text).findFirst().orElseThrow()).getFont().getSize());
    }

    @Test void aFencedBlockIsDrawnInABoxWithItsLanguageAndShrunkFences() {
        openMarkdown(FENCED);
        assertEquals(1, visibleOf(".pm-code-box").size());
        assertEquals("java", fx(() -> ((javafx.scene.control.Label) visibleOf(".pm-code-language").getFirst()).getText()));
        assertTrue(fenceSize() <= 2, "the fence lines are a hairline while the caret is outside: " + fenceSize());
        var text = fx(() -> view.lookupAll(".pm-code-block .pm-text").stream().filter(n -> !n.getStyleClass().contains("pm-fence")).map(n -> ((Text) n).getText()).toList());
        assertTrue(text.contains("int x;"), text.toString());
    }

    @Test void theFenceLinesComeBackWhileTheCaretIsInsideTheBlock() {
        openMarkdown(FENCED);
        fx(() -> m.select(prosemirrorfx.core.pm.Pm.cursor(m.state().getDoc(), Fixtures.posAfter(m.state().getDoc(), "int x;"))));
        pulse();
        pulse();
        assertTrue(fenceSize() > 8, "the fence lines show while editing the block: " + fenceSize());
        fx(() -> m.select(prosemirrorfx.core.pm.Pm.cursor(m.state().getDoc(), Fixtures.posAfter(m.state().getDoc(), "after"))));
        pulse();
        pulse();
        assertTrue(fenceSize() <= 2, "and shrink again once the caret leaves");
    }

    @Test void theFenceLinesNeverDrawOverTheFirstLineOfCode() {
        openMarkdown(FENCED);
        fx(() -> m.select(prosemirrorfx.core.pm.Pm.cursor(m.state().getDoc(), Fixtures.posAfter(m.state().getDoc(), "int x;"))));
        pulse();
        pulse();
        var overlap = fx(() -> {
            var texts = view.lookupAll(".pm-code-block .pm-text").stream().map(n -> (Text) n).toList();
            var fence = texts.stream().filter(t -> t.getStyleClass().contains("pm-fence")).findFirst().orElseThrow();
            var code = texts.stream().filter(t -> !t.getStyleClass().contains("pm-fence") && t.getText().equals("int x;")).findFirst().orElseThrow();
            return fence.localToScene(fence.getBoundsInLocal()).getMaxY() - code.localToScene(code.getBoundsInLocal()).getMinY();
        });
        assertTrue(overlap <= 1, "the opening fence line ends where the first code line begins, overlap " + overlap);
    }

    @Test void theCopyButtonCopiesTheCodeAndNotTheFences() {
        openMarkdown(FENCED);
        var copy = (javafx.scene.control.Label) visibleOf(".pm-code-copy").getFirst();
        var click = new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, false, null);
        fx(() -> Event.fireEvent(copy, click));
        assertEquals("int x;\n  y();", fx(() -> Clipboard.getSystemClipboard().getString()));
        assertEquals("copied", fx(copy::getText));
        assertEquals(FENCED, fx(() -> prosemirrorfx.markdown.Lossless.write(m.state().getDoc())), "copying does not touch the text");
    }

    @Test void inlineCodeGetsABoxAndACopyButtonOnHover() {
        openMarkdown("use `foo()` here\n");
        var boxes = visibleOf(".pm-code-inline");
        assertEquals(1, boxes.size());
        var at = fx(() -> boxes.getFirst().localToScene(boxes.getFirst().getLayoutBounds()));
        var hover = (javafx.scene.control.Label) fx(() -> view.lookup(".pm-code-inline-copy"));
        assertFalse(fx(hover::isVisible), "no copy button until the pointer is on the code");
        var move = new MouseEvent(MouseEvent.MOUSE_MOVED, at.getCenterX(), at.getCenterY(), 0, 0, MouseButton.NONE, 0, false, false, false, false, false, false, false, false, false, false, null);
        fx(() -> Event.fireEvent(view.page, move));
        assertTrue(fx(hover::isVisible));
        var click = new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, false, null);
        fx(() -> Event.fireEvent(hover, click));
        assertEquals("foo()", fx(() -> Clipboard.getSystemClipboard().getString()));
    }

    @Test void aThemeRestylesTheCodeBoxes() {
        openMarkdown(FENCED);
        fx(() -> view.setTheme(Theme.DARK));
        pulse();
        var fill = fx(() -> ((javafx.scene.layout.Region) visibleOf(".pm-code-box").getFirst()).getBackground().getFills().getFirst().getFill());
        assertEquals(Theme.DARK.color("code-bg"), fill);
    }

    // ---- folding

    boolean shown(javafx.scene.Node n) {
        for (var p = n; p != null && p != view; p = p.getParent()) if (!p.isVisible()) return false;
        return true;
    }

    /** The text of every block that is actually on screen, in order. */
    List<String> shownTexts() {
        return fx(() -> flows().stream().filter(this::shown).map(f -> f.getChildren().stream().map(t -> ((Text) t).getText()).reduce("", String::concat)).toList());
    }

    Node family() {
        return ul(li(p("parent|"), ul(li(p("child one")), li(p("child two")))), li(p("sibling")));
    }

    @Test void ctrlUpFoldsTheItemAtTheCaretAndCtrlDownUnfoldsIt() {
        open(family());
        assertEquals(List.of("parent", "child one", "child two", "sibling"), shownTexts());
        press(UP, true, false);
        assertEquals(List.of("parent", "sibling"), shownTexts());
        assertEquals(java.util.Set.of("0/0"), fx(view::folded));
        expect("ul(li(p(\"parent|\"), ul(li(p(\"child one\")), li(p(\"child two\")))), li(p(\"sibling\")))");
        press(DOWN, true, false);
        assertEquals(List.of("parent", "child one", "child two", "sibling"), shownTexts());
        assertTrue(fx(view::folded).isEmpty());
    }

    @Test void foldingMovesACaretThatWasInsideTheHiddenLinesToTheOwnerLine() {
        open(ul(li(p("parent"), ul(li(p("child one|")), li(p("child two")))), li(p("sibling"))));
        assertTrue(fx(view::foldAtCaret));
        expect("ul(li(p(\"parent|\"), ul(li(p(\"child one\")), li(p(\"child two\")))), li(p(\"sibling\")))");
        assertEquals(List.of("parent", "sibling"), shownTexts());
    }

    @Test void arrowKeysStepOverFoldedLinesInBothDirections() {
        open(family());
        press(UP, true, false);
        press(RIGHT);
        expect("ul(li(p(\"parent\"), ul(li(p(\"child one\")), li(p(\"child two\")))), li(p(\"|sibling\")))");
        press(LEFT);
        expect("ul(li(p(\"parent|\"), ul(li(p(\"child one\")), li(p(\"child two\")))), li(p(\"sibling\")))");
        press(DOWN);
        assertTrue(fx(() -> show(m)).contains("li(p(\"sibl"), "Down from the folded item goes to the next visible line: " + fx(() -> show(m)));
    }

    @Test void aHeadingFoldsItsSectionUpToTheNextHeadingOfTheSameOrHigherLevel() {
        open(h(1, "Head|"), p("body"), h(2, "Sub"), p("more"), h(1, "Next"), p("end"));
        press(UP, true, false);
        assertEquals(List.of("Head", "Next", "end"), shownTexts());
        assertEquals(java.util.Set.of("0"), fx(view::folded));
        press(DOWN, true, false);
        assertEquals(List.of("Head", "body", "Sub", "more", "Next", "end"), shownTexts());
    }

    @Test void clickingTheFoldControlFoldsAndUnfolds() {
        open(family());
        var control = fx(() -> view.foldControls.pool.stream().filter(l -> l.isVisible()).findFirst().orElseThrow());
        assertEquals("▾", fx(control::getText));
        var click = new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, false, null);
        fx(() -> Event.fireEvent(control, click));
        pulse();
        assertEquals(List.of("parent", "sibling"), shownTexts());
        assertEquals("▸", fx(control::getText));
        fx(() -> Event.fireEvent(control, click));
        pulse();
        assertEquals(List.of("parent", "child one", "child two", "sibling"), shownTexts());
    }

    @Test void aFoldStaysOnItsHeadingWhileTheTextAroundItIsEdited() {
        open(p("first"), h(1, "Head"), p("body|"));
        assertTrue(fx(view::foldAtCaret));
        fx(() -> m.dispatch(m.state().getTr().insertText("ZZ", 1, 1)));
        pulse();
        assertEquals(List.of("ZZfirst", "Head"), shownTexts(), "the section is still folded after text went in before it");
        type("X");
        assertEquals(List.of("ZZfirst", "HeadX"), shownTexts(), "and after typing on the heading itself");
        assertEquals(java.util.Set.of("1"), fx(view::folded));
    }

    @Test void theFoldedKeysComeBackOnAFreshViewOfTheSameText() {
        open(h(1, "A"), p("under a"), ul(li(p("item"), p("second line")), li(p("plain"))));
        fx(view::collapseAll);
        var keys = fx(view::folded);
        var folded = shownTexts();
        assertEquals(List.of("A"), folded, "the fold on A hides everything under it");
        assertEquals(java.util.Set.of("0", "2/0"), keys);
        open(h(1, "A"), p("under a"), ul(li(p("item"), p("second line")), li(p("plain"))));
        assertEquals(5, shownTexts().size(), "a fresh view shows everything");
        fx(() -> view.setFolded(keys));
        pulse();
        assertEquals(folded, shownTexts());
        fx(() -> view.setFolded(List.of("0", "17/3/9", "nonsense")));
        assertEquals(java.util.Set.of("0"), fx(view::folded), "keys that name no foldable block are ignored");
    }

    @Test void collapseAllAndExpandAllAndTheChangeEvent() {
        open(h(1, "A"), p("under a"), h(2, "B"), p("under b"), ul(li(p("item"), p("second"))));
        var events = new java.util.ArrayList<java.util.Set<String>>();
        fx(() -> view.foldedProperty().addListener((o, was, now) -> events.add(now)));
        fx(view::collapseAll);
        assertEquals(List.of("A"), shownTexts(), "everything is inside the section of A");
        assertEquals(java.util.Set.of("0", "2", "4/0"), fx(view::folded));
        fx(view::expandAll);
        assertEquals(6, shownTexts().size(), "A, under a, B, under b, item and second are all shown again");
        assertEquals(2, events.size());
        fx(view::expandAll);
        assertEquals(2, events.size(), "nothing changed, so nothing fired");
    }

    @Test void aJumpIntoFoldedTextOpensTheFold() {
        open(family());
        press(UP, true, false);
        assertEquals(List.of("parent", "sibling"), shownTexts());
        fx(() -> view.model().select(prosemirrorfx.core.pm.Pm.cursor(m.state().getDoc(), Fixtures.posAfter(m.state().getDoc(), "child two"))));
        pulse();
        assertEquals(List.of("parent", "child one", "child two", "sibling"), shownTexts());
        assertTrue(fx(view::folded).isEmpty());
    }

    @Test void loadingADocumentClearsTheFolds() {
        open(family());
        press(UP, true, false);
        fx(() -> m.load(doc(ul(li(p("a"), p("b"))))));
        pulse();
        assertTrue(fx(view::folded).isEmpty());
        assertEquals(List.of("a", "b"), shownTexts());
    }

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

    @Test void typingMeasuresOnlyTheEditedParagraph() {
        var blocks = new java.util.ArrayList<Node>();
        blocks.add(p("ab|cd"));
        for (var k = 0; k < 60; k++) blocks.add(p("another paragraph that stays as it is, number " + k));
        open(blocks.toArray(Node[]::new));
        Flow.shaped = 0;
        type("x");
        assertTrue(Flow.shaped <= 4, "only the edited paragraph is shaped again, not all 61: " + Flow.shaped);
        expect("p(\"abx|cd\"), " + java.util.stream.IntStream.range(0, 60).mapToObj(k -> "p(\"another paragraph that stays as it is, number " + k + "\")").collect(java.util.stream.Collectors.joining(", ")));
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

    @Test void lineNumbersButtonNumbersEverySourceLineOnceEvenWhenABlockWraps() {
        var longText = "word ".repeat(80);
        open(h(1, "Title"), p(longText), ul(li(p("bullet"))), p("end|"));
        clickTool(EditorAction.LINES);
        var numbers = fx(() -> visibleNumbers());
        var drawn = fx(() -> flows().stream().mapToInt(f -> f.getLayoutInfo().getTextLines(false).size()).sum());
        assertTrue(drawn > 4, "the long paragraph wraps");
        assertEquals(4, numbers.size(), "a wrapped paragraph is one line");
        assertEquals("1", numbers.getFirst().getText());
        assertEquals("7", numbers.getLast().getText(), "the numbers are source lines: a blank line sits between top-level blocks");
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
        assertEquals("1 of 3", fx(() -> ((javafx.scene.control.Label) bar.getChildren().stream().filter(n -> n instanceof javafx.scene.control.Label).findFirst().orElseThrow()).getText()));
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
