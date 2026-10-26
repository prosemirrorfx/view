package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import java.util.List;
import org.junit.jupiter.api.Test;
import prosemirrorfx.markdown.Lossless;

import static org.junit.jupiter.api.Assertions.*;

class CodeTest {

    static Node block(String md) {
        var doc = Lossless.read(md);
        for (var c : doc.getContent().getContent()) if (Code.isBlock(c)) return c;
        throw new AssertionError("no code block in " + md);
    }

    @Test void aFencedBlockKnowsItsLanguageAndItsCodeWithoutTheFences() {
        var b = block("intro\n\n```java\nint x;\n  y();\n```\n\nafter\n");
        assertEquals("java", Code.language(b));
        assertEquals("int x;\n  y();", Code.text(b));
        assertTrue(Code.closed(Code.lines(b)));
    }

    @Test void aBlockWithoutALanguageHasAnEmptyOne() {
        var b = block("~~~\ncode\n~~~\n");
        assertEquals("", Code.language(b));
        assertEquals("code", Code.text(b));
    }

    @Test void anUnclosedFenceTakesEverythingAfterTheOpeningLine() {
        var b = block("```\nfirst\nsecond");
        assertFalse(Code.closed(Code.lines(b)));
        assertEquals("first\nsecond", Code.text(b));
    }

    @Test void emptyLinesInsideCodeAreKept() {
        assertEquals("a\n\nb", Code.text(block("```\na\n\nb\n```\n")));
    }

    @Test void onlyTheLanguageWordIsTheLanguageNotTheRestOfTheInfoString() {
        assertEquals("js", Code.language(block("```js title=\"x\"\nlet a;\n```\n")));
    }

    @Test void aPlainParagraphIsNotACodeBlock() {
        var doc = Lossless.read("just text\n");
        assertFalse(Code.isBlock(doc.getFirstChild()));
    }

    @Test void theFenceLinesAreTheFirstAndLastChildrenAndTheirBreaks() {
        var b = block("```java\nint x;\n```\n");
        var fence = Code.fenceChildren(b);
        var kids = b.getContent().getContent();
        assertEquals(5, kids.size(), "fence, break, code, break, fence");
        assertEquals(List.of(0, 1, 3, 4), fence.stream().boxed().toList());
    }
}
