package prosemirrorfx.view;

import java.util.*;
import javafx.scene.paint.Color;

/**
 * The editor's colours as named tokens. The stylesheet reads them as looked-up colours (-pm-bg, -pm-text, ...), and
 * the caret, selection, find matches and line numbers, which are drawn in code, read the same values. One call,
 * view.setTheme(Theme.DARK), restyles the view and the toolbar, status bar and contents panel made from it.
 * Names without the -pm- prefix: bg page, panel contents panel, text, muted, interim (words still arriving), code,
 * link, code-bg and code-border (code boxes), reading-line and reading-word (read aloud), line and border (hairlines), active-line, hover, handle (block control), select and select-focus (contents
 * row), caret, selection, match (find), number (line numbers). A value is any CSS colour, rgba() included.
 * JavaFX's own controls (buttons, text fields) keep the host's theme; set that on the host.
 */
public final class Theme {

    public static final Theme LIGHT = of(
            "bg", "#FFFFFF", "panel", "#FAFBFC", "text", "#172B4D", "muted", "#5E6C84", "interim", "#8993A4",
            "code", "#403294", "link", "#0052CC", "line", "#DFE1E6", "border", "#C1C7D0", "active-line", "#F1F4F9",
            "hover", "#EBECF0", "handle", "#6B778C", "select", "#DEEBFF", "select-focus", "#4C9AFF",
            "caret", "#172B4D", "selection", "rgba(76,154,255,0.30)", "match", "rgba(255,196,0,0.35)", "number", "#97A0AF",
            "code-bg", "#F4F5F7", "code-border", "#DFE1E6",
            "reading-line", "rgba(76,154,255,0.10)", "reading-word", "rgba(76,154,255,0.38)");

    public static final Theme DARK = of(
            "bg", "#1E1F22", "panel", "#25262A", "text", "#E3E5E8", "muted", "#9CA3AF", "interim", "#7A828E",
            "code", "#C4A7FF", "link", "#6CB2FF", "line", "#3A3C42", "border", "#4A4D55", "active-line", "#2A2C31",
            "hover", "#34363C", "handle", "#9AA1AC", "select", "#26476B", "select-focus", "#2F6FB3",
            "caret", "#E3E5E8", "selection", "rgba(76,154,255,0.35)", "match", "rgba(255,196,0,0.30)", "number", "#6B7280",
            "code-bg", "#2B2D31", "code-border", "#3F4148",
            "reading-line", "rgba(76,154,255,0.13)", "reading-word", "rgba(76,154,255,0.45)");

    final Map<String, String> tokens;

    Theme(Map<String, String> tokens) { this.tokens = Collections.unmodifiableMap(tokens); }

    /** name, colour, name, colour... */
    public static Theme of(String... namesAndColours) {
        var m = new LinkedHashMap<String, String>();
        for (var k = 0; k < namesAndColours.length; k += 2) m.put(namesAndColours[k], namesAndColours[k + 1]);
        return new Theme(m);
    }

    /** This theme with one colour changed; the name must be one of the tokens. */
    public Theme with(String name, String colour) {
        if (!tokens.containsKey(name)) throw new IllegalArgumentException("no colour token " + name);
        var m = new LinkedHashMap<>(tokens);
        m.put(name, colour);
        return new Theme(m);
    }

    public String colour(String name) { return tokens.get(name); }

    public Color color(String name) { return Color.web(colour(name)); }

    public Set<String> names() { return tokens.keySet(); }

    /** The tokens as an inline style, which beats any stylesheet rule. */
    String style() {
        var sb = new StringBuilder();
        tokens.forEach((name, colour) -> sb.append("-pm-").append(name).append(": ").append(colour).append(';'));
        return sb.toString();
    }
}
