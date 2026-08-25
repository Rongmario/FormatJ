package demo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import zone.rong.other.Thing;
import zone.rong.other.Unused;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Imports {

    List<String> run(Map<String, ArrayList<String>> in, @Nullable Thing t) throws IOException {
        assertTrue(requireNonNull(in) != null);
        return List.of();
    }

}
