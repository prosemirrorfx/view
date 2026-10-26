package prosemirrorfx.view;

import com.atlassian.prosemirror.model.*;
import com.atlassian.prosemirror.state.PMEditorState;
import com.atlassian.prosemirror.state.Transaction;
import java.util.ArrayList;
import javafx.scene.input.*;
import prosemirrorfx.markdown.Html;
import prosemirrorfx.markdown.Markdown;

import static prosemirrorfx.core.pm.Pm.*;
import static prosemirrorfx.core.schema.EditorSchema.*;

/**
 * Copy, cut and paste through the system clipboard. Copy puts plain text and HTML, so formatting
 * survives into Word, Gmail or Confluence. Paste of our own copy keeps its structure; HTML from Word,
 * Gmail, Google Docs or a browser keeps headings, lists, bold and italic (Html); plain text comes in one paragraph per line.
 */
final class Clip {

    /** What we last copied, so pasting it back keeps lists, headings and marks. */
    static Slice lastSlice;
    static String lastText;

    static boolean copy(PMEditorState state) {
        var sel = state.getSelection();
        if (sel.getEmpty()) return false;
        var doc = state.getDoc();
        var slice = doc.slice(sel.getFrom(), sel.getTo(), false);
        var text = doc.textBetween(sel.getFrom(), sel.getTo(), "\n", "\n");
        var content = new ClipboardContent();
        content.putString(text);
        var from = doc.resolve(sel.getFrom());
        var whole = withEnclosingLists(from, sel.getTo(), slice.getContent());
        content.putHtml(html(whole));
        Clipboard.getSystemClipboard().setContent(content);
        var added = from.sharedDepth(sel.getTo()) - wrappedTextblocks(from, sel.getTo());
        // Copied bullets go back in as a list (closed start), not merged into the line they are pasted on.
        lastSlice = whole.getFirstChild() != null && isList(whole.getFirstChild())
                ? slice(whole, 0, slice.getOpenEnd() + added)
                : slice(whole, slice.getOpenStart() + added, slice.getOpenEnd() + added);
        lastText = text;
        return true;
    }

    /** The whole document, in the chosen format. */
    static void copyAll(Node doc, CopyFormat format) {
        var content = new ClipboardContent();
        switch (format) {
            case FORMATTED -> { content.putHtml(html(doc.getContent())); content.putString(plain(doc)); }
            case HTML -> content.putString(html(doc.getContent()));
            case MARKDOWN -> content.putString(Markdown.write(doc));
            case PLAIN -> content.putString(plain(doc));
        }
        Clipboard.getSystemClipboard().setContent(content);
        lastSlice = null;
    }

    static Transaction cut(PMEditorState state) {
        return copy(state) ? state.getTr().deleteSelection().scrollIntoView() : null;
    }

    static Transaction paste(PMEditorState state) {
        var cb = Clipboard.getSystemClipboard();
        var text = cb.hasString() ? cb.getString().replace("\r\n", "\n").replace('\r', '\n') : "";
        var tr = state.getTr();
        if (lastSlice != null && !text.isEmpty() && text.equals(lastText)) return tr.replaceSelection(lastSlice).scrollIntoView();
        if (cb.hasHtml()) {
            var blocks = Html.read(cb.getHtml());
            if (!blocks.isEmpty()) return tr.replaceSelection(Html.pasteSlice(blocks)).scrollIntoView();
        }
        return pasteText(tr, text);
    }

    /** Ctrl+Shift+V: the clipboard's plain text only, whatever formatting came with it. */
    static Transaction pastePlain(PMEditorState state) {
        var cb = Clipboard.getSystemClipboard();
        return pasteText(state.getTr(), cb.hasString() ? cb.getString().replace("\r\n", "\n").replace('\r', '\n') : "");
    }

    static Transaction pasteText(Transaction tr, String text) {
        if (text.isEmpty()) return null;
        if (text.indexOf('\n') < 0) return tr.insertText(clean(text), null, null).scrollIntoView();
        var paras = new ArrayList<Node>();
        for (var line : text.split("\n", -1)) paras.add(line.isEmpty() ? PARAGRAPH.create(null, EMPTY, null) : PARAGRAPH.create(null, frag(SCHEMA.text(clean(line), null)), null));
        return tr.replaceSelection(slice(frag(paras.toArray(Node[]::new)), 1, 1)).scrollIntoView();
    }

    static String clean(String s) {
        return s.replace('\t', ' ').replaceAll("[\\p{Cc}&&[^\n]]", "");
    }

    /** A slice leaves out the ancestors both ends share; two copied bullets still need their &lt;ul&gt; elsewhere. */
    static Fragment withEnclosingLists(ResolvedPos from, int to, Fragment content) {
        for (var d = from.sharedDepth(to); d > 0; d--) {
            var n = from.node(d);
            if (!n.isTextblock()) content = frag(prosemirrorfx.core.pm.Pm.copy(n, content));
        }
        return content;
    }

    static int wrappedTextblocks(ResolvedPos from, int to) {
        var n = 0;
        for (var d = from.sharedDepth(to); d > 0; d--) if (from.node(d).isTextblock()) n++;
        return n;
    }

    static boolean isList(Node n) { return n.getType() == BULLET_LIST || n.getType() == ORDERED_LIST; }

    // ---- plain text out: a line per paragraph, "• " and "1. " for list items, a blank line between top-level blocks

    static String plain(Node doc) {
        var out = new StringBuilder();
        for (var n : doc.getContent().getContent()) {
            if (!out.isEmpty()) out.append('\n');
            plain(n, "", out);
        }
        return out.toString().stripTrailing();
    }

    static void plain(Node n, String indent, StringBuilder out) {
        if (n.isTextblock()) { out.append(indent).append(n.textBetween(0, n.getContent().getSize(), "\n", "\n")).append('\n'); return; }
        if (!isList(n)) { for (var c : n.getContent().getContent()) plain(c, indent, out); return; }
        var k = n.getType() == ORDERED_LIST ? intAttr(n, "order") : 0;
        for (var item : n.getContent().getContent()) {
            var mark = k > 0 ? (k++) + ". " : "• ";
            var first = true;
            for (var c : item.getContent().getContent()) {
                if (first && c.isTextblock()) out.append(indent).append(mark).append(c.textBetween(0, c.getContent().getSize(), "\n", "\n")).append('\n');
                else plain(c, indent + "    ", out);
                first = false;
            }
        }
    }

    // ---- HTML out

    static String html(Fragment content) {
        var out = new StringBuilder();
        for (var n : content.getContent()) node(n, out);
        return out.toString();
    }

    static void node(Node n, StringBuilder out) {
        var type = n.getType();
        if (n.isText()) {
            var open = new StringBuilder();
            var close = new StringBuilder();
            for (var m : n.getMarks()) {
                var tag = m.getType() == STRONG ? "strong" : m.getType() == EM ? "em" : null;
                if (tag == null) continue;
                open.append('<').append(tag).append('>');
                close.insert(0, "</" + tag + ">");
            }
            out.append(open).append(escape(n.getText())).append(close);
            return;
        }
        if (type == HARD_BREAK) { out.append("<br>"); return; }
        var tag = type == PARAGRAPH ? "p" : type == HEADING ? "h" + intAttr(n, "level")
                : type == BULLET_LIST ? "ul" : type == ORDERED_LIST ? "ol" : type == LIST_ITEM ? "li" : null;
        if (tag == null) { for (var c : n.getContent().getContent()) node(c, out); return; }
        out.append('<').append(tag);
        if (type == ORDERED_LIST && intAttr(n, "order") != 1) out.append(" start=\"").append(intAttr(n, "order")).append('"');
        out.append('>');
        for (var c : n.getContent().getContent()) node(c, out);
        out.append("</").append(tag).append('>');
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private Clip() {}
}
