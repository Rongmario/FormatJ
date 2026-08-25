package demo;

import zone.rong.other.Thing;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import javax.annotation.Nullable;
import java.util.List;
import java.io.IOException;
import static java.util.Objects.requireNonNull;
import zone.rong.other.Unused;

class Imports {

    List<String> run(Map<String, ArrayList<String>> in, @Nullable Thing t) throws IOException {
        assertTrue(requireNonNull(in) != null);
        return List.of();
    }

}
