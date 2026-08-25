package zone.rong.formatj.idea;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Optimize Imports through FormatJ's {@code imports.*} rules.
 */
public final class FormatJImportOptimizer implements ImportOptimizer {

    @Override
    public boolean supports(@NotNull PsiFile file) {
        return FormatJFiles.isJava(file) && FormatJSettings.getInstance(file.getProject()).isEnabled();
    }

    @Override
    public @NotNull Runnable processFile(@NotNull PsiFile file) {
        Project project = file.getProject();
        PsiDocumentManager documents = PsiDocumentManager.getInstance(project);
        Document document = documents.getDocument(file);
        if (document == null) {
            return EmptyAction.INSTANCE;
        }
        String original = document.getText();
        FormatJEngine.Outcome outcome = FormatJSettings.getInstance(project)
                .engine()
                .format(
                        new FormatJEngine.Request(
                                original,
                                file.getName(),
                                FormatJFiles.path(file),
                                List.of(),
                                FormatJFiles.languageLevel(file),
                                FormatJFiles.previewFeatures(file),
                                true));
        if (outcome.hasErrors() || outcome.unchanged()) {
            return EmptyAction.INSTANCE;
        }
        String formatted = outcome.text();
        return () -> {
            if (documents.isDocumentBlockedByPsi(document)) {
                documents.doPostponedOperationsAndUnblockDocument(document);
            }
            if (CharSequence.compare(original, document.getCharsSequence()) != 0) {
                return;
            }
            document.setText(formatted);
        };
    }

    private enum EmptyAction implements Runnable {

        INSTANCE;

        @Override
        public void run() { }

    }

}
