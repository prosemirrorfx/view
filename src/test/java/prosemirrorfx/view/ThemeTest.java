package prosemirrorfx.view;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemeTest {

    @Test void lightAndDarkNameTheSameColours() {
        assertEquals(Theme.LIGHT.names(), Theme.DARK.names());
        assertTrue(Theme.LIGHT.names().containsAll(java.util.List.of("bg", "text", "caret", "selection", "match", "number")));
    }

    @Test void withChangesOneColourAndLeavesTheOriginalAlone() {
        var pink = Theme.LIGHT.with("link", "#FF00AA");
        assertEquals("#FF00AA", pink.colour("link"));
        assertEquals("#0052CC", Theme.LIGHT.colour("link"));
        assertEquals(Theme.LIGHT.colour("bg"), pink.colour("bg"));
    }

    @Test void anUnknownColourNameIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Theme.LIGHT.with("backround", "#000000"));
    }

    @Test void colourValuesParseIncludingRgba() {
        assertEquals(Color.web("#172B4D"), Theme.LIGHT.color("caret"));
        assertEquals(0.30, Theme.LIGHT.color("selection").getOpacity(), 1e-6);
    }

    @Test void styleCarriesEveryTokenWithThePrefix() {
        var style = Theme.DARK.style();
        assertTrue(style.contains("-pm-bg: #1E1F22;"), style);
        assertTrue(style.contains("-pm-selection: rgba(76,154,255,0.35);"), style);
        assertEquals(Theme.DARK.names().size(), style.split(";").length);
    }
}
