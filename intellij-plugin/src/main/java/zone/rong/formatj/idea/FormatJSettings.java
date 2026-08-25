package zone.rong.formatj.idea;

import zone.rong.formatj.api.Preset;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Project-level FormatJ settings, stored in {@code .idea/formatj.xml}.
 */
@State(name = "FormatJSettings", storages = @Storage("formatj.xml"))
public final class FormatJSettings implements PersistentStateComponent<FormatJSettings.State> {

    private final Project project;
    private State state = new State();
    private volatile FormatJEngine engine;

    public FormatJSettings(Project project) {
        this.project = project;
    }

    public static FormatJSettings getInstance(Project project) {
        return project.getService(FormatJSettings.class);
    }

    public boolean isEnabled() {
        return state.enabled;
    }

    public void setEnabled(boolean enabled) {
        state.enabled = enabled;
        CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged();
    }

    public String styleFile() {
        return state.styleFile;
    }

    public void setStyleFile(String styleFile) {
        state.styleFile = styleFile == null ? "" : styleFile;
        engine = null;
    }

    public String preset() {
        return state.preset;
    }

    public void setPreset(String preset) {
        state.preset = preset == null ? "" : preset;
        engine = null;
    }

    public FormatJEngine engine() {
        FormatJEngine.Settings wanted = engineSettings();
        FormatJEngine current = engine;
        if (current == null || !current.settings().equals(wanted)) {
            current = new FormatJEngine(wanted);
            engine = current;
        }
        return current;
    }

    public Path projectPath() {
        String base = project.getBasePath();
        return base == null ? null : Path.of(base);
    }

    FormatJEngine.Settings engineSettings() {
        Path styleFile = null;
        if (state.styleFile != null && !state.styleFile.isBlank()) {
            Path path = Path.of(state.styleFile);
            if (!path.isAbsolute()) {
                Path base = projectPath();
                if (base != null) {
                    path = base.resolve(path);
                }
            }
            styleFile = path;
        }
        Preset preset = null;
        if (state.preset != null && !state.preset.isBlank()) {
            preset = Preset.of(state.preset);
        }
        return new FormatJEngine.Settings(styleFile, preset);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
        engine = null;
    }

    public static final class State {

        public boolean enabled = true;
        public String styleFile = "";
        public String preset = "";

    }

}
