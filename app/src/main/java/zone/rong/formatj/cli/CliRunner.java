package zone.rong.formatj.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.SourceRange;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.FileRules;
import zone.rong.formatj.core.FormatJ;
import zone.rong.formatj.core.config.FileSelection;
import zone.rong.formatj.core.config.TomlReader;
import zone.rong.formatj.core.config.TomlWriter;
import zone.rong.formatj.core.io.SourceFiles;

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

    /** Hidden directories and the usual build output directories are skipped unless named explicitly. */
    private static boolean isSkippedDirectory(Path directory) {
        Path name = directory.getFileName();
        if (name == null) {
            return false;
        }
        String text = name.toString();
        return text.startsWith(".") || text.equals("build") || text.equals("target") || text.equals("out");
    }

    /** A path must pass both the CLI's own --include/--exclude and the style file's [files] table. */
    private static boolean selected(Path path, StyleResolver styles, FileSelection cliSelection) {
        return cliSelection.matches(path) && styles.fileSelectionFor(path).matches(path);
    }

    private static String version() {
        return CliRunner.class.getPackage().getImplementationVersion();
    }

    int run() {
        StyleResolver styles;
        try {
            styles = new StyleResolver(options);
        } catch (TomlReader.TomlException | IllegalArgumentException e) {
            err.println("formatj: " + e.getMessage());
            return ERROR;
        }
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
            source = SourceFiles.decode(in.readAllBytes(), FileRules.charset(style));
        } catch (IOException e) {
            err.println("formatj: cannot read standard input: " + e.getMessage());
            return ERROR;
        }
        FormatResult result = formatter(style).format(request(source, options.stdinName()));
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
            files = discover(styles);
        } catch (IOException e) {
            err.println("formatj: " + e.getMessage());
            return ERROR;
        }
        if (files.isEmpty()) {
            err.println("formatj: no Java sources matched");
            return ERROR;
        }
        if (!options.lines().isEmpty() && files.size() != 1) {
            err.println("formatj: --lines needs exactly one file");
            return ERROR;
        }

        AtomicInteger changed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<Future<FileOutput>> pending = new ArrayList<>(files.size());
        try (ExecutorService pool = Executors.newFixedThreadPool(options.parallelism())) {
            for (Path file : files) {
                pending.add(pool.submit(() -> processFile(file, styles, changed, failed)));
            }
            // Printed in submission order, not completion order, so -j keeps output deterministic.
            for (int i = 0; i < pending.size(); i++) {
                try {
                    FileOutput output = pending.get(i).get();
                    err.print(output.err());
                    out.print(output.out());
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
                "formatj: " + files.size() + " files, " + changed.get() + " changed, " + failed.get() + " failed"
            );
        }
        if (failed.get() > 0) {
            return ERROR;
        }
        if (changed.get() > 0 && options.mode() != CliOptions.Mode.WRITE) {
            return WOULD_REFORMAT;
        }
        return SUCCESS;
    }

    private FileOutput processFile(Path file, StyleResolver styles, AtomicInteger changed, AtomicInteger failed) {
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        Style style = styles.forFile(file);
        Charset charset = FileRules.charset(style);
        String source;
        try {
            source = SourceFiles.readString(file, charset);
        } catch (IOException e) {
            err.append("formatj: cannot read ").append(file).append(": ").append(e.getMessage()).append('\n');
            failed.incrementAndGet();
            return new FileOutput(out.toString(), err.toString());
        }

        FormatResult result = formatter(style).format(request(source, file.toString()));
        appendDiagnostics(err, file.toString(), result);
        if (result.hasErrors()) {
            failed.incrementAndGet();
            return new FileOutput(out.toString(), err.toString());
        }
        if (result.isUnchanged()) {
            if (options.verbose()) {
                out.append("unchanged ").append(file).append('\n');
            }
            return new FileOutput(out.toString(), err.toString());
        }

        changed.incrementAndGet();
        switch (options.mode()) {
            case WRITE -> {
                try {
                    SourceFiles.writeAtomic(file, result.text(), charset);
                    out.append("formatted ").append(file).append('\n');
                } catch (IOException e) {
                    err.append("formatj: cannot write ").append(file).append(": ").append(e.getMessage()).append('\n');
                    failed.incrementAndGet();
                }
            }
            case DIFF -> out.append(UnifiedDiff.between(file.toString(), source, result.text()));
            default -> out.append(file).append('\n');
        }
        return new FileOutput(out.toString(), err.toString());
    }

    private FormatRequest request(String source, String name) {
        FormatRequest request = FormatRequest.of(source).withName(name);
        if (options.lines().isEmpty()) {
            return request;
        }
        List<Integer> starts = new ArrayList<>(List.of(0));
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        List<SourceRange> ranges = new ArrayList<>();
        for (int[] range : options.lines()) {
            int from = Math.min(range[0] - 1, starts.size() - 1);
            int to = range[1] < starts.size() ? starts.get(range[1]) : source.length();
            ranges.add(new SourceRange(starts.get(from), Math.max(to, starts.get(from))));
        }
        return request.withRanges(ranges);
    }

    private void reportDiagnostics(String name, FormatResult result) {
        StringBuilder buffer = new StringBuilder();
        appendDiagnostics(buffer, name, result);
        err.print(buffer);
    }

    private void appendDiagnostics(StringBuilder buffer, String name, FormatResult result) {
        for (Diagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.severity() == Diagnostic.Severity.INFO && !options.verbose()) {
                continue;
            }
            buffer.append(diagnostic.format(name)).append('\n');
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
    private List<Path> discover(StyleResolver styles) throws IOException {
        // Relative to the working directory, with the leading "./" a root like "." contributes
        // normalized away, so "--include 'src/**' ." matches the paths a user would type.
        FileSelection cliSelection = new FileSelection(Path.of(""), options.includes(), options.excludes());
        List<Path> files = new ArrayList<>();
        for (Path path : options.paths()) {
            if (!Files.exists(path)) {
                throw new IOException("no such file or directory: " + path);
            }
            if (Files.isRegularFile(path)) {
                // A file named explicitly is formatted regardless of the default directory skips.
                if (selected(path, styles, cliSelection)) {
                    files.add(path);
                }
                continue;
            }
            List<Path> found = new ArrayList<>();
            Files.walkFileTree(path, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
                    return !directory.equals(path) && isSkippedDirectory(directory) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java") && selected(file, styles, cliSelection)) {
                        found.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

            });
            found.sort(null);
            files.addAll(found);
        }
        return List.copyOf(files);
    }

    /** One file's output, buffered rather than printed directly so {@code -j} can print it in order. */
    private record FileOutput(String out, String err) { }

}
