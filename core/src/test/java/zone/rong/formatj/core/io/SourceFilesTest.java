package zone.rong.formatj.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceFilesTest {

    @Test
    void decodesValidUtf8() throws IOException {
        assertEquals("hello", SourceFiles.decode("hello".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }

    @Test
    void failsWithTheByteOffsetInsteadOfReplacingTheBadByte() {
        byte[] bytes = { 'a', 'b', (byte) 0xFF };
        IOException failure = assertThrows(IOException.class, () -> SourceFiles.decode(bytes, StandardCharsets.UTF_8));
        assertTrue(failure.getMessage().contains("not valid UTF-8"), failure.getMessage());
        assertTrue(failure.getMessage().contains("byte 3"), failure.getMessage());
    }

    @Test
    void writeAtomicReplacesTheFileContentAndLeavesNoTempFileBehind(@TempDir Path root) throws IOException {
        Path file = root.resolve("A.java");
        Files.writeString(file, "old");

        SourceFiles.writeAtomic(file, "new", StandardCharsets.UTF_8);

        assertEquals("new", Files.readString(file));
        try (var listing = Files.list(root)) {
            assertEquals(1, listing.count(), "no leftover temp file");
        }
    }

}
