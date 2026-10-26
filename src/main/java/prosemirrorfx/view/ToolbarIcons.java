package prosemirrorfx.view;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.SVGPath;

/**
 * Vector icons from a name&lt;TAB&gt;SVG-path table (Material Design icons, 24x24 paths). The editor's own are in
 * icons.tsv; a host application loads its table the same way with load(itsClass, "its-icons.tsv").
 */
public final class ToolbarIcons {

    static final Map<String, String> PATHS = load(ToolbarIcons.class, "icons.tsv");

    public static Map<String, String> load(Class<?> owner, String resource) {
        var map = new HashMap<String, String>();
        try (var in = owner.getResourceAsStream(resource);
             var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            r.lines().filter(l -> !l.startsWith("#") && l.contains("\t"))
                    .forEach(l -> map.put(l.substring(0, l.indexOf('\t')), l.substring(l.indexOf('\t') + 1).trim()));
        } catch (IOException | NullPointerException e) {
            // no icons: the buttons fall back to their names
        }
        return map;
    }

    /** One of the editor's icons, or null if unknown. */
    public static Node of(String name) { return draw(name == null ? null : PATHS.get(name)); }

    /** The path at 18 px, filled with the theme's text colour (style class pm-icon), or null for no path. */
    public static Node draw(String path) {
        if (path == null) return null;
        var p = new SVGPath();
        p.setContent(path);
        p.getStyleClass().add("pm-icon");
        p.setScaleX(0.75);
        p.setScaleY(0.75);
        return new Group(p);
    }

    private ToolbarIcons() {}
}
