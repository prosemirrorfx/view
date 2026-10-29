package prosemirrorfx.view;

import java.util.regex.Pattern;

/**
 * The `>>` comment convention: a paragraph starting with `>>` is a comment in a thread, one starting `>> AI:` a reply
 * from the assistant (emphasis marks around "AI" ignored, as an editor may have added them). Only the look changes;
 * the text stays exactly as written.
 */
final class Comments {

    static final Pattern AI = Pattern.compile("^[\s^*_~]*AI[\s^*_~]*:", Pattern.CASE_INSENSITIVE);

    /** pm-comment-ai, pm-comment-user, or null for an ordinary paragraph. */
    static String kind(String text) {
        var t = text.stripLeading();
        if (!t.startsWith(">>")) return null;
        return AI.matcher(t.substring(2)).find() ? "pm-comment-ai" : "pm-comment-user";
    }

    private Comments() {}
}
