package zone.rong.formatj.core.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;

/**
 * Reading and writing source files: strict charset decoding, and atomic writes.
 *
 * <p>Shared by the CLI, Gradle and Maven plugins so a decode or write failure reads the same way
 * everywhere.
 */
public final class SourceFiles {

    private SourceFiles() { }

    /** Reads a whole file, decoding strictly: a byte the charset cannot read fails the read. */
    public static String readString(Path file, Charset charset) throws IOException {
        return decode(Files.readAllBytes(file), charset);
    }

    /**
     * Decodes bytes strictly rather than replacing a bad byte with U+FFFD, so invalid input fails
     * the same way whether it came from a file or from standard input.
     */
    public static String decode(byte[] bytes, Charset charset) throws IOException {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer input = ByteBuffer.wrap(bytes);
        CharBuffer output = CharBuffer.allocate((int) (bytes.length * decoder.maxCharsPerByte()) + 1);
        CoderResult result = decoder.decode(input, output, true);
        if (!result.isError()) {
            result = decoder.flush(output);
        }
        if (result.isError()) {
            // input.position() sits right before the bad byte; +1 to report it 1-based.
            throw new IOException(
                    "not valid " + charset.name() + " (malformed input at byte " + (input.position() + 1) + ")");
        }
        output.flip();
        return output.toString();
    }

    /**
     * Writes {@code content} atomically: a temp file next to {@code file} is written in full, then
     * moved into place, so a reader never observes a half-written file.
     */
    public static void writeAtomic(Path file, String content, Charset charset) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path temp = Files.createTempFile(absolute.getParent(), absolute.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content, charset);
            copyPermissionsIfPossible(absolute, temp);
            try {
                Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Best effort: a filesystem that cannot report POSIX permissions leaves the temp file's own. */
    private static void copyPermissionsIfPossible(Path file, Path temp) throws IOException {
        if (Files.exists(file) && Files.getFileAttributeView(file, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(file));
        }
    }

}
