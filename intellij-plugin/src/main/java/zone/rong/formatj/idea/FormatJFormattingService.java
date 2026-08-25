package zone.rong.formatj.idea;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.SourceRange;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Hands Java Reformat Code/format-on-save to FormatJ.
 */
public final class FormatJFormattingService extends AsyncDocumentFormattingService {

    static final String NOTIFICATION_GROUP = "FormatJ";

    private static final Set<Feature> FEATURES = Set.of(Feature.FORMAT_FRAGMENTS, Feature.OPTIMIZE_IMPORTS);
    private static final Set<ImportOptimizer> IMPORT_OPTIMIZERS = Set.of(new FormatJImportOptimizer());

    @Override
    public boolean canFormat(@NotNull PsiFile file) {
        return FormatJFiles.isJava(file) && FormatJSettings.getInstance(file.getProject()).isEnabled();
    }

    @Override
    public @NotNull Set<Feature> getFeatures() {
        return FEATURES;
    }

    @Override
    public @NotNull Set<ImportOptimizer> getImportOptimizers(@NotNull PsiFile file) {
        return IMPORT_OPTIMIZERS;
    }

    @Override
    protected @Nullable FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest request) {
        return new FormatTask(request);
    }

    @Override
    protected @NotNull String getNotificationGroupId() {
        return NOTIFICATION_GROUP;
    }

    @Override
    protected @NotNull String getName() {
        return "FormatJ";
    }

    private static final class FormatTask implements FormattingTask {

        private final AsyncFormattingRequest request;

        private FormatTask(AsyncFormattingRequest request) {
            this.request = request;
        }

        @Override
        public void run() {
            PsiFile file = request.getContext().getContainingFile();
            String source = request.getDocumentText();
            FormatJSettings settings = FormatJSettings.getInstance(file.getProject());
            Path path = FormatJFiles.path(file);
            List<SourceRange> ranges = ranges(source, request.getFormattingRanges());
            try {
                FormatJEngine.Outcome outcome = settings.engine()
                        .format(
                                new FormatJEngine.Request(
                                        source,
                                        file.getName(),
                                        path,
                                        ranges,
                                        FormatJFiles.languageLevel(file),
                                        FormatJFiles.previewFeatures(file),
                                        !request.canChangeWhitespaceOnly()));
                if (outcome.hasErrors()) {
                    Diagnostic diagnostic = outcome.diagnostics()
                            .stream()
                            .filter(item -> item.severity() == Diagnostic.Severity.ERROR)
                            .findFirst()
                            .orElse(Diagnostic.error("FormatJ could not format this file"));
                    request.onError(
                            "FormatJ could not format " + file.getName(),
                            diagnostic.message(),
                            offset(source, diagnostic));
                    return;
                }
                if (outcome.unchanged()) {
                    request.onTextReady(null);
                    return;
                }
                request.onTextReady(outcome.text());
            } catch (RuntimeException e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                request.onError("FormatJ failed", message);
            }
        }

        @Override
        public boolean cancel() {
            return false;
        }

        @Override
        public boolean isRunUnderProgress() {
            return true;
        }

    }

    static List<SourceRange> ranges(String source, List<TextRange> textRanges) {
        if (textRanges == null || textRanges.isEmpty()) {
            return List.of();
        }
        if (textRanges.size() == 1) {
            TextRange range = textRanges.getFirst();
            if (range.getStartOffset() == 0 && range.getEndOffset() >= source.length()) {
                return List.of();
            }
        }
        List<SourceRange> ranges = new ArrayList<>(textRanges.size());
        for (TextRange range : textRanges) {
            int start = clamp(range.getStartOffset(), source.length());
            int end = Math.max(start, clamp(range.getEndOffset(), source.length()));
            ranges.add(new SourceRange(start, end));
        }
        return ranges;
    }

    private static int clamp(int offset, int length) {
        if (offset < 0) {
            return 0;
        }
        return Math.min(offset, length);
    }

    private static int offset(String source, Diagnostic diagnostic) {
        if (diagnostic.line() <= 0) {
            return -1;
        }
        int line = 1;
        int column = 0;
        for (int i = 0; i < source.length(); i++) {
            if (line == diagnostic.line() && column == Math.max(0, diagnostic.column() - 1)) {
                return i;
            }
            if (source.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        return -1;
    }

}
