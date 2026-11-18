package zone.rong.formatj.cli;

import java.io.UncheckedIOException;

import zone.rong.formatj.core.config.TomlReader;

/** Command line entry point. */
public final class Main {

    private Main() { }

    public static void main(String[] arguments) {
        // Core logs through System.Logger, whose default JDK format spends a line on a timestamp.
        System.setProperty("java.util.logging.SimpleFormatter.format", "formatj: warning: %5$s%n");
        System.exit(run(arguments));
    }

    /** Runs the CLI and returns the exit code, without terminating the JVM. */
    static int run(String[] arguments) {
        try {
            CliOptions options = CliOptions.parse(arguments);
            return new CliRunner(options, System.out, System.err, System.in).run();
        } catch (CliOptions.CliException | IllegalArgumentException | TomlReader.TomlException | UncheckedIOException e) {
            System.err.println("formatj: " + e.getMessage());
            return CliRunner.ERROR;
        } catch (RuntimeException e) {
            System.err.println("formatj: unexpected failure: " + e);
            return CliRunner.ERROR;
        }
    }

}
