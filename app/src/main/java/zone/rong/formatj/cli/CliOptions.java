package zone.rong.formatj.cli;

import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Preset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The parsed command line.
 *
 * <p>Argument parsing is hand-written rather than delegated to a library: the CLI is meant to start
 * fast and to be one self-contained jar, and a formatter that drags a dependency tree into every
 * build is the kind of friction this project exists to remove.
 */
public record CliOptions(
        Mode mode,
        List<Path> paths,
        Optional<Path> styleFile,
        Optional<Preset> preset,
        Map<String, String> overrides,
        List<String> includes,
        List<String> excludes,
        LanguageLevel languageLevel,
        boolean previewFeatures,
        boolean verify,
        boolean readStdin,
        String stdinName,
        int parallelism,
        boolean verbose) {

    /** What the CLI was asked to do. */
    public enum Mode {

        /** Rewrite files in place. */
        WRITE,

        /** Report files that would change, and exit non-zero if any would. */
        CHECK,

        /** Print a unified diff of what would change. */
        DIFF,

        /** Print the whole rule catalogue as TOML and exit. */
        DUMP_CONFIG,

        /** Print usage and exit. */
        HELP,

        /** Print the version and exit. */
        VERSION

    }

    /** A malformed command line. */
    public static final class CliException extends RuntimeException {

        public CliException(String message) {
            super(message);
        }

    }

    public static CliOptions parse(String[] arguments) {
        Mode mode = Mode.CHECK;
        boolean modeGiven = false;
        List<Path> paths = new ArrayList<>();
        Path styleFile = null;
        Preset preset = null;
        Map<String, String> overrides = new LinkedHashMap<>();
        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        LanguageLevel languageLevel = LanguageLevel.LATEST;
        boolean previewFeatures = false;
        boolean verify = true;
        boolean readStdin = false;
        String stdinName = "<stdin>";
        int parallelism = Runtime.getRuntime().availableProcessors();
        boolean verbose = false;

        for (int i = 0; i < arguments.length; i++) {
            String argument = arguments[i];
            switch (argument) {
                case "--write", "-w" -> {
                    mode = Mode.WRITE;
                    modeGiven = true;
                }
                case "--check", "-c" -> {
                    mode = Mode.CHECK;
                    modeGiven = true;
                }
                case "--diff", "-d" -> {
                    mode = Mode.DIFF;
                    modeGiven = true;
                }
                case "--dump-config" -> {
                    mode = Mode.DUMP_CONFIG;
                    modeGiven = true;
                }
                case "--help", "-h" -> {
                    return helpOptions(Mode.HELP);
                }
                case "--version" -> {
                    return helpOptions(Mode.VERSION);
                }
                case "--style", "-s" -> styleFile = Path.of(value(arguments, ++i, "--style"));
                case "--preset", "-p" -> preset = Preset.of(value(arguments, ++i, "--preset"));
                case "--set" -> {
                    String assignment = value(arguments, ++i, "--set");
                    int equals = assignment.indexOf('=');
                    if (equals <= 0) {
                        throw new CliException("--set expects key=value, got '" + assignment + "'");
                    }
                    overrides.put(assignment.substring(0, equals).trim(), assignment.substring(equals + 1).trim());
                }
                case "--include" -> includes.add(value(arguments, ++i, "--include"));
                case "--exclude" -> excludes.add(value(arguments, ++i, "--exclude"));
                case "--language-level" ->
                        languageLevel = LanguageLevel.ofRelease(intValue(arguments, ++i, "--language-level"));
                case "--preview" -> previewFeatures = true;
                case "--no-verify" -> verify = false;
                case "--stdin" -> readStdin = true;
                case "--stdin-name" -> {
                    stdinName = value(arguments, ++i, "--stdin-name");
                    readStdin = true;
                }
                case "-j", "--jobs" -> parallelism = Math.max(1, intValue(arguments, ++i, "--jobs"));
                case "--verbose", "-v" -> verbose = true;
                default -> {
                    if (argument.startsWith("-") && argument.length() > 1) {
                        throw new CliException("Unknown option '" + argument + "'. Try --help.");
                    }
                    paths.add(Path.of(argument));
                }
            }
        }

        if (mode == Mode.DUMP_CONFIG) {
            return new CliOptions(
                    mode,
                    List.of(),
                    Optional.ofNullable(styleFile),
                    Optional.ofNullable(preset),
                    overrides,
                    includes,
                    excludes,
                    languageLevel,
                    previewFeatures,
                    verify,
                    false,
                    stdinName,
                    parallelism,
                    verbose);
        }
        if (paths.isEmpty() && !readStdin) {
            throw new CliException("Nothing to format. Pass one or more paths, or --stdin. Try --help.");
        }
        if (readStdin && !modeGiven) {
            // Formatting a stream and printing the result is the only sensible default for a pipe.
            mode = Mode.WRITE;
        }
        return new CliOptions(
                mode,
                List.copyOf(paths),
                Optional.ofNullable(styleFile),
                Optional.ofNullable(preset),
                Map.copyOf(overrides),
                List.copyOf(includes),
                List.copyOf(excludes),
                languageLevel,
                previewFeatures,
                verify,
                readStdin,
                stdinName,
                parallelism,
                verbose);
    }

    private static CliOptions helpOptions(Mode mode) {
        return new CliOptions(
                mode,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                List.of(),
                List.of(),
                LanguageLevel.LATEST,
                false,
                true,
                false,
                "<stdin>",
                1,
                false);
    }

    private static String value(String[] arguments, int index, String option) {
        if (index >= arguments.length) {
            throw new CliException(option + " expects a value");
        }
        return arguments[index];
    }

    private static int intValue(String[] arguments, int index, String option) {
        String raw = value(arguments, index, option);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new CliException(option + " expects a number, got '" + raw + "'");
        }
    }

    /** The usage text printed by {@code --help}. */
    public static String usage() {
        return """
                formatj - a configurable Java formatter

                USAGE
                  formatj [options] <path>...
                  formatj --stdin [--stdin-name Foo.java] [options]

                MODES
                  -c, --check           report files that would change, exit 1 if any would (default)
                  -w, --write           rewrite files in place
                  -d, --diff            print a unified diff of what would change
                      --dump-config     print every rule with its effective value as TOML
                  -h, --help            print this help
                      --version         print the version

                STYLE
                  -s, --style FILE      read rules from a TOML style file
                  -p, --preset NAME     start from a preset: formatj (default) or google
                      --set KEY=VALUE   override one rule, repeatable
                      --language-level N  syntax level to parse, e.g. 21 (default: latest)
                      --preview         accept preview syntax for that level
                      --no-verify       skip the safety checks; for debugging the formatter only

                FILES
                      --include GLOB    only format paths matching this glob, repeatable
                      --exclude GLOB    skip paths matching this glob, repeatable
                      --stdin           read source from standard input, write to standard output
                      --stdin-name NAME name used for diagnostics when reading standard input

                OTHER
                  -j, --jobs N          files to format in parallel (default: available processors)
                  -v, --verbose         report every file and every diagnostic

                EXIT CODES
                  0  success: nothing to do, or the requested rewrite succeeded
                  1  files would be reformatted (--check and --diff only)
                  2  an error occurred

                Style resolution: --set overrides --preset and --style, which override the nearest
                formatj.toml found by walking up from each file, which overrides the built-in defaults.
                """;
    }

}
