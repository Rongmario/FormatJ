package zone.rong.formatj.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** The packaged Maven plugin must stay on the documented Java 21 floor. */
class RuntimeFloorTest {

    @Test
    void pluginCompilesToJava21() throws IOException {
        assertEquals(
                65,
                majorVersion("zone/rong/formatj/maven/FormatMojo.class"),
                "Maven format mojo must be Java 21 bytecode");
        assertEquals(
                65,
                majorVersion("zone/rong/formatj/maven/CheckMojo.class"),
                "Maven check mojo must be Java 21 bytecode");
    }

    private static int majorVersion(String resource) throws IOException {
        try (InputStream in = RuntimeFloorTest.class.getClassLoader().getResourceAsStream(resource)) {
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
