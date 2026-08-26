package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import zone.rong.formatj.api.Formatter;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** Published core bytecode must stay on the documented Java 21 floor. */
class RuntimeFloorTest {

    static final int JAVA_21 = 65;

    @Test
    void publicApiCompilesToJava21() throws IOException {
        assertEquals(JAVA_21, majorVersion(Formatter.class), "zone.rong.formatj.api must be Java 21 bytecode");
    }

    @Test
    void implementationCompilesToJava21() throws IOException {
        assertEquals(JAVA_21, majorVersion(FormatJ.class), "core implementation must be Java 21 bytecode");
    }

    static int majorVersion(Class<?> type) throws IOException {
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
