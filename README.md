# prosemirrorfx-view

A document editor drawn natively in JavaFX, with no WebView: the prosemirrorfx document model with headings, bold and italic, nested bullet and numbered lists, undo, find and replace, a toolbar, a status line, an optional table of contents, line numbers and zoom. Input methods that compose a word before committing it (Tamil and Hindi phonetic, Japanese, Chinese) are supported.

Built on [prosemirror-kotlin](https://github.com/atlassian-labs/prosemirror-kotlin) by Atlassian (Apache License 2.0) through `prosemirrorfx-core`. prosemirrorfx is an independent project and is not affiliated with ProseMirror or Atlassian.

## Status

Not yet published. Snapshots only to begin with: `0.1.0-SNAPSHOT` on `https://central.sonatype.com/repository/maven-snapshots/`. Java 25 and JavaFX 26 (`javafx-controls`).

    <dependency>
        <groupId>io.github.prosemirrorfx</groupId>
        <artifactId>prosemirrorfx-view</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>

It brings `prosemirrorfx-core` and `prosemirrorfx-markdown` with it.

## Embedding a Markdown note

    var editor = new MarkdownEditor();                 // prosemirrorfx.view.embed
    editor.setMarkdown(text);                          // load: caret at the start, undo cleared, not dirty
    root.setCenter(editor.node());                     // a javafx Region; it takes the keyboard when focused
    root.setTop(editor.toolbar());                     // optional
    root.setBottom(editor.statusBar());                // optional
    editor.onChange(() -> scheduleSave());             // after every edit, not after setMarkdown
    editor.dirtyProperty().addListener(...);           // true while the text differs from the loaded one
    var text = editor.getMarkdown();                   // unedited: exactly what was set
    editor.markSaved();                                // after writing it: this text is the clean one
    editor.focus();

`caret()` and `setCaret(int)` let the host remember where the caret was. `copy()`, `cut()`, `paste()` and `pastePlain()` do what Ctrl+C, X, V and Shift+V do, for a host toolbar or menu, and each returns whether it did anything (`DocView` has the same four). `view()` returns the underlying `DocView` for what the class does not wrap (find, zoom, line numbers, a table of contents, `Toc`). Everything is for the JavaFX thread. Keys are the editor's own (Ctrl+B, Ctrl+I, Tab and Shift+Tab on list items, Ctrl+Z, Ctrl+F, ...); it binds no function keys, so those stay free for the host.

Undoing back to the loaded text makes `isDirty()` false again. The byte-for-byte guarantee comes from `Lossless` in `prosemirrorfx-markdown`.

## Folding

A list item with nested lines folds away everything after its first paragraph, and a heading folds away its section, up to the next heading of the same or a higher level. A small triangle in the left margin shows and toggles each fold; Ctrl+Up folds the innermost block around the caret (then the one around that), Ctrl+Down unfolds it. Folded lines are skipped by the arrow keys, and anything that moves the caret into folded text (find, the contents panel) opens the fold first. Folding is view state: the document, its Markdown and the undo history are not touched.

    view.collapseAll();  view.expandAll();             // every foldable block
    var keys = view.folded();                          // a sorted set of strings such as "5" or "6/0/1"
    view.setFolded(keys);                              // restore them later; keys that fit no foldable block are ignored
    view.foldedProperty().addListener(...);            // changes when the user, an edit or the host changes a fold

A key is the block's index path (child 6 of the document, its child 0, that one's child 1), so it names the same block when an unedited file is reopened. Edits made while the editor is open move the folds with their blocks. `MarkdownEditor` has the same five calls.

## Code

A fenced block in a Markdown file is drawn in a shaded, bordered box, in a monospace font, with its language (the word after the opening fence) in the top right corner and a Copy button beside it that copies the code without the fences. The fence lines stay in the text, so the file round-trips, but they shrink to a hairline until the caret is in the block. Inline code gets a subtle box of its own, and a small copy button appears over it when the pointer is on it. The boxes are styled by CSS from the theme colours `code-bg` and `code-border`, so `setTheme` restyles them. Only the lossless Markdown reader produces fenced blocks; text pasted from HTML and read by `Markdown` has none.

## Source lines

The line numbers the toolbar toggles are the Markdown file's own source line numbers, not a running count: a blank line between blocks takes a number, a paragraph written over several lines has a number on each, a line the text merely wraps onto has none, and a fold shows as a jump. `DocView` and `MarkdownEditor` also give the position map for a source view or a search jump: `caretLine()`, `goToLine(line)`, `goToSource(line, column)`, `caretSourceOffset()`, `setCaretSourceOffset(offset)` (offsets index `getMarkdown()`), and `sourceMap()` for the whole `SourceMap` of the markdown module.

## Find and search

The find bar (Ctrl+F) has three options beside the box: `Aa` match case (Alt+C), `ab` whole word (Alt+W) and `.*` regular expression (Alt+R). A word is letters of any script, digits, combining marks and `_`. In regex mode `^` and `$` match at each line of a block and the replacement may use `$1` and `${name}`; an unparseable pattern says "invalid" and finds nothing. A match never spans two blocks.

For a host's own search panel, `DocView` and `MarkdownEditor` have:

    var hits = view.findAll(query, new Find.Options(regex, matchCase, wholeWord));  // from, to, block, line text, column
    view.selectHit(hits.getFirst());           // or selectRange(from, to): selects, scrolls, and opens folded text around it
    var n = view.replaceAll(query, options, "$2 at $1");       // one undo step, returns the count
    var why = view.findProblem(query, options);                // null, or why a regex does not parse

The matching itself is in core (`Find.hits`, `Find.replacements`), so a document read from a file with `Lossless.read` can be searched without a view, for a search across files; `SourceMap.of(doc).line(hit.from())` gives the hit's source line.

## Colours

`view.setTheme(Theme.DARK)` restyles the view and the toolbar, status bar and contents panel made from it; `Theme.LIGHT` is the default. A theme is a set of named colours: `bg`, `panel`, `text`, `muted`, `interim`, `code`, `link`, `line`, `border`, `active-line`, `hover`, `handle`, `select`, `select-focus`, and the four the view draws in code, `caret`, `selection`, `match` (find) and `number` (line numbers). Change one with `Theme.LIGHT.with("link", "#FF00AA")`, or build your own with `Theme.of("bg", "#101010", ...)` listing every name.

The stylesheet reads the same names as looked-up colours (`-pm-bg`, `-pm-text`, ...), set inline by the theme so they beat any stylesheet rule. JavaFX's own buttons and text fields keep the host's theme.

## Live text

`DocView.appendCommitted(text)` adds finished text at the end of the document as bullets, one undo step per call, and `setInterim(text)` shows words that are still arriving in grey after the end of the document; they are never part of the document. Both are safe to call from any thread. The demo's `--stream` flag shows it.

## Layout

    prosemirrorfx.view         DocView and everything it draws: Toolbar, StatusBar, Toc, FindBar, Clip (copy and paste), Ime
    prosemirrorfx.view.embed   MarkdownEditor, the embedding class
    resources: editor.css, icons.tsv

## Tests

    mvn test

`ViewKeysTest` drives the editor end to end on JavaFX's headless platform: real key and mouse events go to the focused `DocView`, and after each step both the document and what is drawn are checked. `MarkdownEditorTest` drives the embedding class.
