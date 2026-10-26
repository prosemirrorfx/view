package prosemirrorfx.view;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import prosemirrorfx.core.commands.Commands;
import prosemirrorfx.view.embed.MarkdownEditor;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownEditorTest {

    static Stage stage;

    @BeforeAll static void boot() throws Exception {
        FxBoot.start();
        fx(() -> stage = new Stage());
    }

    static <T> T fx(Callable<T> c) {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> { try { result.complete(c.call()); } catch (Throwable t) { result.completeExceptionally(t); } });
        try { return result.get(20, TimeUnit.SECONDS); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static void fx(Runnable r) { fx(() -> { r.run(); return null; }); }

    MarkdownEditor shown(String md) {
        return fx(() -> {
            var ed = new MarkdownEditor();
            ed.setMarkdown(md);
            stage.setScene(new Scene(new BorderPane(ed.node(), ed.toolbar(), null, ed.statusBar(), null), 800, 500));
            stage.show();
            ed.focus();
            return ed;
        });
    }

    static void typed(MarkdownEditor ed, String s) {
        fx(() -> ed.node().fireEvent(new KeyEvent(KeyEvent.KEY_TYPED, s, "", KeyCode.UNDEFINED, false, false, false, false)));
    }

    @Test void textSetIsTextGotBack() {
        var md = Fixtures.fixture("thread.md");
        var ed = shown(md);
        assertEquals(md, fx(ed::getMarkdown));
        assertFalse(fx(ed::isDirty));
    }

    @Test void typingMakesItDirtyAndChangesOnlyTheEditedLine() {
        var md = "first\n\n>> a comment\n\n- item\n";
        var ed = shown(md);
        fx(() -> ed.setCaret(Fixtures.posAfter(ed.view().model().state().getDoc(), "a comment")));
        typed(ed, "X");
        assertTrue(fx(ed::isDirty));
        var out = fx(ed::getMarkdown);
        assertEquals(md.split("\n").length, out.split("\n").length);
        assertEquals("first\n\n>> a commentX\n\n- item\n", out);
    }

    @Test void listenerHearsEditsButNotLoads() {
        var calls = new AtomicInteger();
        var ed = shown("a\n");
        fx(() -> ed.onChange(calls::incrementAndGet));
        fx(() -> ed.setMarkdown("b\n"));
        assertEquals(0, calls.get());
        typed(ed, "c");
        assertEquals(1, calls.get());
    }

    @Test void undoBackToTheLoadedTextIsClean() {
        var ed = shown("a\n");
        typed(ed, "z");
        assertTrue(fx(ed::isDirty));
        fx(() -> ed.view().model().run(Commands.undo));
        assertFalse(fx(ed::isDirty));
        assertEquals("a\n", fx(ed::getMarkdown));
    }

    @Test void markSavedMakesTheCurrentTextTheCleanOne() {
        var ed = shown("a\n");
        typed(ed, "z");
        var saved = fx(ed::markSaved);
        assertFalse(fx(ed::isDirty));
        assertEquals(saved, fx(ed::getMarkdown));
        assertTrue(saved.contains("z"));
    }

    @Test void loadingAnotherTextClearsUndoAndDirt() {
        var ed = shown("a\n");
        typed(ed, "z");
        fx(() -> ed.setMarkdown("fresh\n"));
        assertFalse(fx(ed::isDirty));
        fx(() -> ed.view().model().run(Commands.undo));
        assertEquals("fresh\n", fx(ed::getMarkdown));
    }
}
