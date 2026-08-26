package zone.rong.formatj.core.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StageTimerTest {

    @Test
    void reportLabelsEveryPipelineStage() {
        String source = """
                package sample;

                class Sample {

                    void run() {
                        int x = 1;
                    }

                }
                """;

        StageTimer.Times times = StageTimer.measure(List.of(source, source));
        String report = times.report();

        assertTrue(times.files() >= 1, report);
        assertTrue(report.contains("lex:"), report);
        assertTrue(report.contains("parse:"), report);
        assertTrue(report.contains("rewrite:"), report);
        assertTrue(report.contains("verify:"), report);
        assertTrue(report.contains("layout:"), report);
        assertTrue(report.contains("reparse:"), report);
        assertTrue(report.contains("second-layout:"), report);
        assertTrue(times.lexNanos() > 0, report);
        assertTrue(times.parseNanos() > 0, report);
        assertTrue(times.rewriteNanos() > 0, report);
        assertTrue(times.verifyNanos() > 0, report);
        assertTrue(times.layoutNanos() > 0, report);
        assertTrue(times.reparseNanos() > 0, report);
        assertTrue(times.secondLayoutNanos() > 0, report);
        assertTrue(times.files() == 2, report);
    }

}
