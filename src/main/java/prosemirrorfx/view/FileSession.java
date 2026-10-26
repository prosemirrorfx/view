package prosemirrorfx.view;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javafx.animation.*;
import javafx.beans.property.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import prosemirrorfx.markdown.Markdown;

/**
 * The editor's document as a Markdown file. Edits are saved automatically a few seconds after they stop
 * (and Ctrl+S saves at once); there is no "unsaved changes?" question, because nothing is ever left
 * unsaved for long. Saving writes a temporary file and moves it over the old one, so a crash mid-save
 * cannot leave half a file. Ctrl+O opens another Markdown file, saving this one first.
 */
public final class FileSession {

    static final Duration QUIET = Duration.seconds(3);
    static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    final DocView view;
    Path path;
    boolean dirty, loading;
    final Timeline autosave = new Timeline(new KeyFrame(QUIET, e -> saveIfDirty()));

    public FileSession(DocView view, Path path) {
        this.view = view;
        view.file = this;
        view.model.onChange((old, now) -> {
            if (loading || old.getDoc() == now.getDoc()) return;
            dirty = true;
            autosave.playFromStart();
            show("edited");
        });
        open(path);
    }

    public Path path() { return path; }

    /** "notes.md · saved 10:42", for a status line or window title. */
    public javafx.beans.property.ReadOnlyStringProperty status() { return view.fileStatus; }

    public void open(Path p) {
        saveIfDirty();
        path = p.toAbsolutePath();
        loading = true;
        try {
            var text = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
            view.model.load(Markdown.read(text));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            loading = false;
        }
        dirty = false;
        show(Files.exists(path) ? "opened" : "new file");
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            var tmp = path.resolveSibling(path.getFileName() + ".saving");
            Files.writeString(tmp, Markdown.write(view.model.state().getDoc()), StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
            autosave.stop();
            show("saved " + LocalTime.now().format(HHMM));
        } catch (IOException e) {
            show("NOT SAVED: " + e.getMessage());
        }
    }

    public void saveIfDirty() {
        if (dirty && path != null) save();
    }

    /** Ctrl+O: pick another Markdown file. */
    void chooseAndOpen() {
        var chooser = new FileChooser();
        chooser.setTitle("Open Markdown");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md", "*.markdown", "*.txt"));
        if (path != null && Files.isDirectory(path.getParent())) chooser.setInitialDirectory(path.getParent().toFile());
        var picked = chooser.showOpenDialog(view.getScene() == null ? null : view.getScene().getWindow());
        if (picked != null) open(picked.toPath());
        view.requestFocus();
    }

    void show(String what) {
        view.fileStatus.set((path == null ? "Untitled" : path.getFileName().toString()) + " · " + what);
    }
}
