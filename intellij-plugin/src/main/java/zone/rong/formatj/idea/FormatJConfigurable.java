package zone.rong.formatj.idea;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.FormBuilder;
import java.awt.BorderLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Settings | Tools | FormatJ.
 */
public final class FormatJConfigurable implements SearchableConfigurable, Configurable.NoScroll {

    private static final String PRESET_NONE = "";
    private static final String PRESET_FORMATJ = "formatj";
    private static final String PRESET_GOOGLE = "google";

    private final Project project;
    private JCheckBox enabled;
    private TextFieldWithBrowseButton styleFile;
    private ComboBox<PresetItem> preset;
    private JLabel discovered;
    private JPanel panel;

    public FormatJConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String getId() {
        return "zone.rong.formatj.settings";
    }

    @Override
    public @Nls String getDisplayName() {
        return "FormatJ";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (panel == null) {
            enabled = new JCheckBox("Enable FormatJ as the Java formatter");
            styleFile = new TextFieldWithBrowseButton();
            styleFile.addBrowseFolderListener(
                    project,
                    FileChooserDescriptorFactory.createSingleFileDescriptor("toml").withTitle("FormatJ style file"));
            preset =
                    new ComboBox<>(
                            new PresetItem[] {
                                new PresetItem(PRESET_NONE, "None (discover formatj.toml)"),
                                new PresetItem(PRESET_FORMATJ, "formatj"),
                                new PresetItem(PRESET_GOOGLE, "google")
                            });
            discovered = new JLabel();
            panel = new JPanel(new BorderLayout());
            panel.add(
                    FormBuilder.createFormBuilder()
                            .addComponent(enabled)
                            .addLabeledComponent("Style file:", styleFile)
                            .addLabeledComponent("Preset:", preset)
                            .addComponent(discovered)
                            .addComponentFillVertically(new JPanel(), 0)
                            .getPanel(),
                    BorderLayout.CENTER);
        }
        return panel;
    }

    @Override
    public boolean isModified() {
        FormatJSettings settings = FormatJSettings.getInstance(project);
        return enabled.isSelected() != settings.isEnabled()
                || !styleFile.getText().trim().equals(nullToEmpty(settings.styleFile()))
                || !selectedPreset().equals(nullToEmpty(settings.preset()));
    }

    @Override
    public void apply() {
        FormatJSettings settings = FormatJSettings.getInstance(project);
        settings.setEnabled(enabled.isSelected());
        settings.setStyleFile(styleFile.getText().trim());
        settings.setPreset(selectedPreset());
        refreshDiscovered();
    }

    @Override
    public void reset() {
        FormatJSettings settings = FormatJSettings.getInstance(project);
        enabled.setSelected(settings.isEnabled());
        styleFile.setText(nullToEmpty(settings.styleFile()));
        String value = nullToEmpty(settings.preset());
        for (int i = 0; i < preset.getItemCount(); i++) {
            if (preset.getItemAt(i).id.equals(value)) {
                preset.setSelectedIndex(i);
                break;
            }
        }
        refreshDiscovered();
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        enabled = null;
        styleFile = null;
        preset = null;
        discovered = null;
    }

    private void refreshDiscovered() {
        discovered.setText(
                FormatJSettings.getInstance(project)
                        .engine()
                        .describeStyle(FormatJSettings.getInstance(project).projectPath()));
    }

    private String selectedPreset() {
        PresetItem item = preset.getItem();
        return item == null ? PRESET_NONE : item.id;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record PresetItem(String id, String label) {

        @Override
        public String toString() {
            return label;
        }

    }

}
