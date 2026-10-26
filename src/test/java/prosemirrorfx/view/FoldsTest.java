package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import java.util.List;
import org.junit.jupiter.api.Test;
import prosemirrorfx.core.editor.EditorModel;

import static org.junit.jupiter.api.Assertions.*;
import static prosemirrorfx.core.schema.Build.*;

class FoldsTest {

    static Node outline() {
        return doc(
                h(1, "One"), p("under one"),
                h(2, "Two"), p("under two"),
                h(1, "Three"), p("under three"),
                ul(li(p("a"), ul(li(p("a1")), li(p("a2")))), li(p("b")), li(p("c"), p("more c"))));
    }

    static int posOf(Node doc, String text) {
        var found = new int[]{-1};
        doc.descendants((n, pos, parent, i) -> {
            if (n.isTextblock() && n.getTextContent().equals(text)) found[0] = pos;
            return found[0] < 0;
        });
        return found[0];
    }

    /** The position before the heading or list item that owns the textblock with this text. */
    static int ownerOf(Node doc, String text) {
        var at = doc.resolve(posOf(doc, text) + 1);
        return at.getParent().getType().getName().equals("heading") ? posOf(doc, text) : at.before(at.getDepth() - 1);
    }

    @Test void aHeadingFoldsItsSectionUpToTheNextHeadingOfTheSameOrHigherLevel() {
        var d = outline();
        var one = posOf(d, "One");
        var r = Folds.range(d, one);
        assertEquals(one + d.nodeAt(one).getNodeSize(), r[0]);
        assertEquals(posOf(d, "Three"), r[1], "heading 1 takes its heading 2 along, and stops at the next heading 1");
        var two = posOf(d, "Two");
        assertEquals(posOf(d, "Three"), Folds.range(d, two)[1], "heading 2 stops at the heading 1");
    }

    @Test void aHeadingWithNothingUnderItHasNothingToFold() {
        var d = doc(h(1, "A"), h(1, "B"));
        assertNull(Folds.range(d, posOf(d, "A")));
    }

    @Test void aListItemFoldsEverythingAfterItsFirstParagraph() {
        var d = outline();
        var a = ownerOf(d, "a");
        var r = Folds.range(d, a);
        assertEquals(posOf(d, "a") + d.nodeAt(posOf(d, "a")).getNodeSize(), r[0]);
        assertEquals(a + d.nodeAt(a).getNodeSize() - 1, r[1]);
        assertNull(Folds.range(d, ownerOf(d, "b")), "an item with only its own line has nothing to fold");
        assertNotNull(Folds.range(d, ownerOf(d, "c")), "an item with a second paragraph does");
    }

    @Test void layoutListsEveryFoldableBlock() {
        var d = outline();
        var folds = new Folds();
        folds.layout(d);
        assertEquals(5, folds.foldables.size(), "headings One, Two and Three, and the items a and c; a1, a2 and b have nothing under them");
    }

    @Test void aKeyIsTheIndexPathAndFindsTheSameBlockAgain() {
        var d = outline();
        var a = ownerOf(d, "a");
        assertEquals("6/0", Folds.key(d, a));
        assertEquals(a, Folds.position(d, "6/0"));
        assertEquals(posOf(d, "Two"), Folds.position(d, "2"));
        assertEquals(-1, Folds.position(d, "6/1"), "a block with nothing to fold is not a fold");
        assertEquals(-1, Folds.position(d, "99"));
        assertEquals(-1, Folds.position(d, "x/y"));
        assertEquals(-1, Folds.position(d, ""));
    }

    @Test void aCaretInsideAFoldedRangeIsHiddenAndOneOnTheOwnerLineIsNot() {
        var d = outline();
        var folds = new Folds();
        folds.layout(d);
        folds.folded.add(ownerOf(d, "a"));
        folds.layout(d);
        assertNull(folds.hiddenAt(posOf(d, "a") + 1), "the first line stays");
        assertNotNull(folds.hiddenAt(posOf(d, "a1") + 1), "a nested line is hidden");
        assertNull(folds.hiddenAt(posOf(d, "b") + 1), "the next sibling is not");
    }

    @Test void foldsFollowTheirBlockThroughAnEdit() {
        var model = new EditorModel(outline());
        var folds = new Folds();
        var before = model.state().getDoc();
        folds.layout(before);
        folds.folded.add(ownerOf(before, "Three"));
        model.dispatch(model.state().getTr().insertText("XYZ", 1, 1));
        folds.remap(before, model.lastTransaction(), model.state().getDoc());
        var after = model.state().getDoc();
        assertEquals(List.of(ownerOf(after, "Three")), List.copyOf(folds.folded), "the fold moved with its heading");
    }

    @Test void deletingAFoldedBlockDropsItsFold() {
        var model = new EditorModel(outline());
        var folds = new Folds();
        var before = model.state().getDoc();
        folds.layout(before);
        var three = ownerOf(before, "Three");
        folds.folded.add(three);
        var tr = model.state().getTr();
        tr.delete(three, three + before.nodeAt(three).getNodeSize());
        model.dispatch(tr);
        folds.remap(before, model.lastTransaction(), model.state().getDoc());
        assertTrue(folds.folded.isEmpty());
    }

    @Test void aLoadClearsEveryFold() {
        var folds = new Folds();
        folds.folded.add(0);
        folds.remap(outline(), null, outline());
        assertTrue(folds.folded.isEmpty());
    }

    @Test void theBlocksAroundACaretComeInnermostFirst() {
        var d = outline();
        var folds = new Folds();
        folds.layout(d);
        var around = folds.around(d, posOf(d, "a1") + 2);
        assertEquals(List.of(ownerOf(d, "a"), posOf(d, "Three")), around, "item a, which holds a1, inside the section of Three");
        var inTwo = folds.around(d, posOf(d, "under two") + 2);
        assertEquals(List.of(posOf(d, "Two"), posOf(d, "One")), inTwo, "the section of Two, inside the section of One");
    }

    @Test void layoutOnALongDocumentIsFast() {
        var items = new Node[4000];
        for (var k = 0; k < items.length; k++) items[k] = k % 5 == 0 ? h(2, "Heading " + k) : p("paragraph " + k);
        var d = doc(items);
        var folds = new Folds();
        folds.layout(d);
        var t0 = System.nanoTime();
        folds.layout(d);
        var ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 250, "layout of 4000 blocks took " + ms + " ms");
    }
}
