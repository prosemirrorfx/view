package prosemirrorfx.view;

import com.atlassian.prosemirror.model.Node;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class Fixtures {

    static String fixture(String name) {
        try (var in = Fixtures.class.getResourceAsStream("/lossless/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static int posAfter(Node doc, String snippet) {
        var found = new int[]{-1};
        doc.descendants((node, pos, parent, index) -> {
            if (node.isText() && found[0] < 0 && node.getText().contains(snippet)) found[0] = pos + node.getText().indexOf(snippet) + snippet.length();
            return true;
        });
        assertTrue(found[0] >= 0, "no text " + snippet);
        return found[0];
    }

    private Fixtures() {}
}
