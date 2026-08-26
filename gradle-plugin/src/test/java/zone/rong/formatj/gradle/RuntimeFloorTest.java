package zone.rong.formatj.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** The packaged Gradle plugin must stay on the documented Java 21 floor. */
class RuntimeFloorTest {

    @Test
    void pluginCompilesToJava21() throws IOException {
        assertEquals(65, majorVersion(FormatJPlugin.class), "Gradle plugin must be Java 21 bytecode");
    }

    @Test
    void pluginTaskCompilesToJava21() throws IOException {
        assertEquals(65, majorVersion(FormatJTask.class), "Gradle plugin task must be Java 21 bytecode");
    }

    private static int majorVersion(Class<?> type) throws IOException {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing class file: " + resource);
            }
            byte[] header = in.readNBytes(8);
            if (header.length < 8
                    || header[0] != (byte) 0xCA
                    || header[1] != (byte) 0xFE
                    || header[2] != (byte) 0xBA
                    || header[3] != (byte) 0xBE) {
                throw new IOException("not a class file: " + resource);
            }
            return ((header[6] & 0xff) << 8) | (header[7] & 0xff);
        }
    }

}
