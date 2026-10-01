package zone.rong.formatj.idea;

import java.nio.file.Path;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import zone.rong.formatj.api.LanguageLevel;

/**
 * IntelliJ-facing helpers shared by the formatting service and the import optimizer.
 */
final class FormatJFiles {

    private FormatJFiles() { }

    static boolean isJava(PsiFile file) {
        return JavaFileType.INSTANCE.equals(file.getFileType());
    }

    static Path path(PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null && virtualFile.isInLocalFileSystem()) {
            return Path.of(virtualFile.getPath());
        }
        String base = file.getProject().getBasePath();
        return base == null ? null : Path.of(base);
    }

    static LanguageLevel languageLevel(PsiFile file) {
        return FormatJLanguageLevels.of(PsiUtil.getLanguageLevel(file).toJavaVersion().feature);
    }

    static boolean previewFeatures(PsiFile file) {
        return PsiUtil.getLanguageLevel(file).isPreview();
    }

}
