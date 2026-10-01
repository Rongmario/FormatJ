package zone.rong.formatj.cli;

import zone.rong.formatj.core.config.TomlReader;

/** Command line entry point. */
public final class Main {

    private Main() { }

    public static void main(String[] arguments) {
        System.exit(run(arguments));
    }

    /** Runs the CLI and returns the exit code, without terminating the JVM. */
    static int run(String[] arguments) {
        try {
            CliOptions options = CliOptions.parse(arguments);
            return new CliRunner(options, System.out, System.err, System.in).run();
        } catch (CliOptions.CliException | IllegalArgumentException | TomlReader.TomlException e) {
            System.err.println("formatj: " + e.getMessage());
            return CliRunner.ERROR;
        } catch (RuntimeException e) {
            System.err.println("formatj: unexpected failure: " + e);
            return CliRunner.ERROR;
        }
    }

}
