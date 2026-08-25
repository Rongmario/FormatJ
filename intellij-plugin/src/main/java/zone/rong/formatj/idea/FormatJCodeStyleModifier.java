package zone.rong.formatj.idea;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.FileRules;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.WrappingRules;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Overlays indent and right-margin from FormatJ onto the editor so the gutter matches
 * {@code formatj.toml} even though Enter-key indent stays on the platform formatter.
 */
public final class FormatJCodeStyleModifier implements CodeStyleSettingsModifier {

    @Override
    public boolean modifySettings(@NotNull TransientCodeStyleSettings settings, @NotNull PsiFile file) {
        if (!FormatJFiles.isJava(file)) {
            return false;
        }
        FormatJSettings projectSettings = FormatJSettings.getInstance(file.getProject());
        if (!projectSettings.isEnabled()) {
            return false;
        }
        Style style = projectSettings.engine().styleFor(FormatJFiles.path(file));
        CommonCodeStyleSettings.IndentOptions indent = settings.getIndentOptions();
        indent.INDENT_SIZE = style.get(IndentRules.SIZE);
        indent.CONTINUATION_INDENT_SIZE = style.get(IndentRules.CONTINUATION);
        indent.USE_TAB_CHARACTER = style.get(IndentRules.USE_TABS);
        boolean tabs = style.get(IndentRules.USE_TABS);
        indent.TAB_SIZE = tabs ? style.get(FileRules.TAB_WIDTH) : style.get(IndentRules.SIZE);
        settings.setRightMargin(JavaLanguage.INSTANCE, style.get(WrappingRules.MAX_LINE_LENGTH));
        settings.setModifier(this);
        return true;
    }

    @Override
    public @NotNull String getName() {
        return "FormatJ";
    }

    @Override
    public CodeStyleStatusBarUIContributor getStatusBarUiContributor(@NotNull TransientCodeStyleSettings settings) {
        return new StatusBar();
    }

    private static final class StatusBar implements CodeStyleStatusBarUIContributor {

        @Override
        public boolean areActionsAvailable(@NotNull VirtualFile file) {
            return true;
        }

        @Override
        public AnAction[] getActions(@NotNull PsiFile file) {
            return AnAction.EMPTY_ARRAY;
        }

        @Override
        public @NotNull String getTooltip() {
            return "Indent and right margin from FormatJ";
        }

        @Override
        public @NotNull AnAction createDisableAction(@NotNull Project project) {
            return new DisableAction(project);
        }

    }

    private static final class DisableAction extends AnAction {

        private final Project project;

        private DisableAction(Project project) {
            super("Disable FormatJ");
            this.project = project;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            FormatJSettings.getInstance(project).setEnabled(false);
        }

    }

}
