package prosemirrorfx.view;

/** What Copy all puts on the clipboard. FORMATTED is the default, as Docs and Confluence copy. */
public enum CopyFormat {

    FORMATTED("Formatted", "HTML and plain text: formatting kept in Word, Gmail, Docs; plain text elsewhere"),
    HTML("HTML source", "The HTML markup as text"),
    MARKDOWN("Markdown source", "Markdown, as saved"),
    PLAIN("Plain text", "Text only, bullets as •");

    public final String label, hint;

    CopyFormat(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }
}
