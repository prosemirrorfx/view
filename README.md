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

`caret()` and `setCaret(int)` let the host remember where the caret was. `view()` returns the underlying `DocView` for what the class does not wrap (find, zoom, line numbers, a table of contents, `Toc`). Everything is for the JavaFX thread. Keys are the editor's own (Ctrl+B, Ctrl+I, Tab and Shift+Tab on list items, Ctrl+Z, Ctrl+F, ...); it binds no function keys, so those stay free for the host.

Undoing back to the loaded text makes `isDirty()` false again. The byte-for-byte guarantee comes from `Lossless` in `prosemirrorfx-markdown`.

## Live text

`DocView.appendCommitted(text)` adds finished text at the end of the document as bullets, one undo step per call, and `setInterim(text)` shows words that are still arriving in grey after the end of the document; they are never part of the document. Both are safe to call from any thread. The demo's `--stream` flag shows it.

## Layout

    prosemirrorfx.view         DocView and everything it draws: Toolbar, StatusBar, Toc, FindBar, Clip (copy and paste), Ime
    prosemirrorfx.view.embed   MarkdownEditor, the embedding class
    resources: editor.css, icons.tsv

## Tests

    mvn test

`ViewKeysTest` drives the editor end to end on JavaFX's headless platform: real key and mouse events go to the focused `DocView`, and after each step both the document and what is drawn are checked. `MarkdownEditorTest` drives the embedding class.
