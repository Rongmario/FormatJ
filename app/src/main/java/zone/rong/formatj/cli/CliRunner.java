package zone.rong.formatj.cli;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.FileRules;
import zone.rong.formatj.core.FormatJ;
import zone.rong.formatj.core.config.TomlWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs one CLI invocation. Kept separate from {@link Main} so it can be tested without exiting. */
final class CliRunner {

    /** Process exit codes, which scripts and CI depend on. */
    static final int SUCCESS = 0;
    static final int WOULD_REFORMAT = 1;
    static final int ERROR = 2;

    private final CliOptions options;
    private final PrintStream out;
    private final PrintStream err;
    private final InputStream in;

    CliRunner(CliOptions options, PrintStream out, PrintStream err, InputStream in) {
        this.options = options;
        this.out = out;
        this.err = err;
        this.in = in;
    }

    int run() {
        StyleResolver styles = new StyleResolver(options);
        return switch (options.mode()) {
            case HELP -> {
                out.print(CliOptions.usage());
                yield SUCCESS;
            }
            case VERSION -> {
                out.println("formatj " + version());
                yield SUCCESS;
            }
            case DUMP_CONFIG -> {
                out.print(TomlWriter.write(styles.forStandardInput()));
                yield SUCCESS;
            }
            case WRITE, CHECK, DIFF -> options.readStdin() ? runStandardInput(styles) : runFiles(styles);
        };
    }

    private int runStandardInput(StyleResolver styles) {
        Style style = styles.forStandardInput();
        String source;
        try {
            source = new String(in.readAllBytes(), FileRules.charset(style));
        } catch (IOException e) {
            err.println("formatj: cannot read standard input: " + e.getMessage());
            return ERROR;
        }
        FormatResult result = formatter(style).format(FormatRequest.of(source).withName(options.stdinName()));
        reportDiagnostics(options.stdinName(), result);
        if (result.hasErrors()) {
            return ERROR;
        }
        return switch (options.mode()) {
            case DIFF -> {
                out.print(UnifiedDiff.between(options.stdinName(), source, result.text()));
                yield result.isUnchanged() ? SUCCESS : WOULD_REFORMAT;
            }
            case CHECK -> {
                if (!result.isUnchanged()) {
                    out.println(options.stdinName());
                }
                yield result.isUnchanged() ? SUCCESS : WOULD_REFORMAT;
            }
            default -> {
                out.print(result.text());
                yield SUCCESS;
            }
        };
    }

    private int runFiles(StyleResolver styles) {
        List<Path> files;
        try {
            files = discover();
        } catch (IOException e) {
            err.println("formatj: " + e.getMessage());
            return ERROR;
        }
        if (files.isEmpty()) {
            err.println("formatj: no Java sources matched");
            return SUCCESS;
        }

        AtomicInteger changed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<Future<?>> pending = new ArrayList<>(files.size());
        try (ExecutorService pool = Executors.newFixedThreadPool(options.parallelism())) {
            for (Path file : files) {
                pending.add(pool.submit(() -> processFile(file, styles, changed, failed)));
            }
            for (Future<?> future : pending) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ERROR;
                } catch (ExecutionException e) {
                    err.println("formatj: " + e.getCause().getMessage());
                    failed.incrementAndGet();
                }
            }
        }

        if (options.verbose()) {
            err.println(
                    "formatj: " + files.size() + " files, " + changed.get() + " changed, " + failed.get() + " failed");
        }
        if (failed.get() > 0) {
            return ERROR;
        }
        if (changed.get() > 0 && options.mode() != CliOptions.Mode.WRITE) {
            return WOULD_REFORMAT;
        }
        return SUCCESS;
    }

    private void processFile(Path file, StyleResolver styles, AtomicInteger changed, AtomicInteger failed) {
        Style style = styles.forFile(file);
        Charset charset = FileRules.charset(style);
        String source;
        try {
            source = Files.readString(file, charset);
        } catch (IOException e) {
            err.println("formatj: cannot read " + file + ": " + e.getMessage());
            failed.incrementAndGet();
            return;
        }

        FormatResult result = formatter(style).format(FormatRequest.of(source).withName(file.toString()));
        reportDiagnostics(file.toString(), result);
        if (result.hasErrors()) {
            failed.incrementAndGet();
            return;
        }
        if (result.isUnchanged()) {
            if (options.verbose()) {
                out.println("unchanged " + file);
            }
            return;
        }

        changed.incrementAndGet();
        switch (options.mode()) {
            case WRITE -> {
                try {
                    Files.writeString(file, result.text(), charset);
                    out.println("formatted " + file);
                } catch (IOException e) {
                    err.println("formatj: cannot write " + file + ": " + e.getMessage());
                    failed.incrementAndGet();
                }
            }
            case DIFF -> out.print(UnifiedDiff.between(file.toString(), source, result.text()));
            default -> out.println(file);
        }
    }

    private void reportDiagnostics(String name, FormatResult result) {
        for (Diagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.severity() == Diagnostic.Severity.INFO && !options.verbose()) {
                continue;
            }
            err.println(diagnostic.format(name));
        }
    }

    private Formatter formatter(Style style) {
        return FormatJ.newFormatter()
                .style(style)
                .languageLevel(options.languageLevel())
                .previewFeatures(options.previewFeatures())
                .verify(options.verify())
                .build();
    }

    /** Expands the given paths into the Java files to format. */
    private List<Path> discover() throws IOException {
        List<PathMatcher> includes = matchers(options.includes());
        List<PathMatcher> excludes = matchers(options.excludes());
        List<Path> files = new ArrayList<>();
        for (Path path : options.paths()) {
            if (!Files.exists(path)) {
                throw new IOException("no such file or directory: " + path);
            }
            if (Files.isRegularFile(path)) {
                if (matches(path, includes, excludes)) {
                    files.add(path);
                }
                continue;
            }
            try (var walk = Files.walk(path)) {
                walk.filter(Files::isRegularFile)
                        .filter(candidate -> candidate.toString().endsWith(".java"))
                        .filter(candidate -> matches(candidate, includes, excludes))
                        .sorted()
                        .forEach(files::add);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }
        return List.copyOf(files);
    }

    private static boolean matches(Path path, List<PathMatcher> includes, List<PathMatcher> excludes) {
        for (PathMatcher exclude : excludes) {
            if (exclude.matches(path)) {
                return false;
            }
        }
        if (includes.isEmpty()) {
            return true;
        }
        for (PathMatcher include : includes) {
            if (include.matches(path)) {
                return true;
            }
        }
        return false;
    }

    private static List<PathMatcher> matchers(List<String> globs) {
        List<PathMatcher> matchers = new ArrayList<>(globs.size());
        for (String glob : globs) {
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob));
        }
        return List.copyOf(matchers);
    }

    private static String version() {
        return CliRunner.class.getPackage().getImplementationVersion();
    }

}
