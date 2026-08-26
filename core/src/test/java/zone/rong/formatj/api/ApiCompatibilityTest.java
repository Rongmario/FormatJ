package zone.rong.formatj.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The public, dependency-free API against a committed source and binary surface baseline.
 *
 * <p>An undeclared addition, removal, or signature change fails the test. The dump includes
 * protected members, generic supertypes, constants, and compiler bridges because all can matter to
 * existing consumers. Intentional 0.x changes update {@code src/test/resources/api-baseline.txt} in
 * the same change.
 *
 * <p>This is a drift sentinel, not a compatibility verdict. Before a stable release, the built jar
 * must also be compared with the previous released jar by a classfile-aware compatibility tool.
 */
class ApiCompatibilityTest {

    private static final Path BASELINE = Path.of("src/test/resources/api-baseline.txt");

    @Test
    void publicApiMatchesTheCommittedBaseline() throws IOException {
        assertTrue(Files.isRegularFile(BASELINE), () -> "missing API baseline: " + BASELINE.toAbsolutePath());
        String expected = Files.readString(BASELINE, StandardCharsets.UTF_8);
        assertEquals(
                expected,
                dump(),
                "public API drifted from " + BASELINE + "; update the baseline if this is intentional");
    }

    @Test
    void dumpIncludesProtectedGenericAndBridgeSignatures() {
        List<String> lines = new ArrayList<>();
        dumpType(GenericValue.class, lines);
        dumpType(StringValue.class, lines);
        String dump = String.join("\n", lines);

        assertTrue(dump.contains("SUPER " + GenericValue.class.getName() + "<java.lang.String>"), dump);
        assertTrue(dump.contains("protected T " + GenericValue.class.getName() + ".value()"), dump);
        assertTrue(dump.contains("[bridge]"), dump);
    }

    /** Regenerates the committed baseline. Run from the {@code core} project directory. */
    public static void main(String[] arguments) throws IOException {
        Path output = arguments.length == 0 ? BASELINE : Path.of(arguments[0]);
        Files.createDirectories(output.getParent());
        Files.writeString(output, dump(), StandardCharsets.UTF_8);
    }

    static String dump() {
        TreeSet<Class<?>> types = new TreeSet<>(Comparator.comparing(Class::getName));
        for (Class<?> type : loadApiTypes()) {
            collect(type, types);
        }
        List<String> lines = new ArrayList<>();
        for (Class<?> type : types) {
            dumpType(type, lines);
        }
        return String.join("\n", lines) + "\n";
    }

    private static void collect(Class<?> type, TreeSet<Class<?>> types) {
        if (!isApiVisible(type.getModifiers()) || !types.add(type)) {
            return;
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            collect(nested, types);
        }
    }

    private static Path apiSources() {
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve("src/api/java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            Path nested = directory.resolve("core/src/api/java");
            if (Files.isDirectory(nested)) {
                return nested;
            }
        }
        throw new IllegalStateException("src/api/java not found from " + Path.of("").toAbsolutePath());
    }

    private static List<Class<?>> loadApiTypes() {
        Path root = apiSources();
        try (var files = Files.walk(root)) {
            List<Class<?>> types = new ArrayList<>();
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String relative = root.relativize(path).toString().replace('/', '.').replace('\\', '.');
                String name = relative.substring(0, relative.length() - 5);
                try {
                    types.add(Class.forName(name));
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(name, e);
                }
            });
            if (types.size() <= 10) {
                throw new IllegalStateException("API source walk should find public types, found " + types.size());
            }
            return types;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void dumpType(Class<?> type, List<String> lines) {
        lines.add("TYPE " + typeSignature(type));
        Type superclass = type.getGenericSuperclass();
        if (superclass != null && superclass != Object.class) {
            lines.add("  SUPER " + superclass.getTypeName());
        }
        for (Type implemented : type.getGenericInterfaces()) {
            lines.add("  IMPLEMENTS " + implemented.getTypeName());
        }
        Class<?>[] permitted = type.getPermittedSubclasses();
        if (permitted != null) {
            for (Class<?> subtype : permitted) {
                lines.add("  PERMITS " + subtype.getName());
            }
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            if (constants != null) {
                for (Object constant : constants) {
                    lines.add("  ENUM " + ((Enum<?>) constant).name());
                }
            }
        }
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                lines.add("  RECORD " + component.getGenericType().getTypeName() + " " + component.getName());
            }
        }
        List<String> members = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (!isApiVisible(field.getModifiers())) {
                continue;
            }
            String constant = constantValue(field);
            members.add("  FIELD " + field.toGenericString() + (constant == null ? "" : " = " + constant));
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!isApiVisible(constructor.getModifiers())) {
                continue;
            }
            members.add("  CTOR " + constructor.toGenericString() + (constructor.isSynthetic() ? " [synthetic]" : ""));
        }
        for (Method method : type.getDeclaredMethods()) {
            if (!isApiVisible(method.getModifiers())) {
                continue;
            }
            StringBuilder signature = new StringBuilder("  METHOD ").append(method.toGenericString());
            if (method.isBridge()) {
                signature.append(" [bridge]");
            }
            if (method.isSynthetic()) {
                signature.append(" [synthetic]");
            }
            if (method.getDefaultValue() != null) {
                signature.append(" DEFAULT ").append(method.getDefaultValue());
            }
            members.add(signature.toString());
        }
        members.sort(Comparator.naturalOrder());
        lines.addAll(members);
    }

    private static String typeSignature(Class<?> type) {
        StringBuilder signature = new StringBuilder();
        String modifiers = Modifier.toString(type.getModifiers() & Modifier.classModifiers());
        if (!modifiers.isEmpty()) {
            signature.append(modifiers).append(' ');
        }
        if (type.isSealed()) {
            signature.append("sealed ");
        }
        if (type.isAnnotation()) {
            signature.append("@interface ");
        } else if (type.isEnum()) {
            signature.append("enum ");
        } else if (type.isRecord()) {
            signature.append("record ");
        } else if (type.isInterface()) {
            signature.append("interface ");
        } else {
            signature.append("class ");
        }
        signature.append(type.getName());
        TypeVariable<?>[] parameters = type.getTypeParameters();
        if (parameters.length > 0) {
            signature.append('<');
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) {
                    signature.append(',');
                }
                TypeVariable<?> parameter = parameters[i];
                signature.append(parameter.getName());
                Type[] bounds = parameter.getBounds();
                if (bounds.length != 1 || bounds[0] != Object.class) {
                    signature.append(" extends ");
                    for (int j = 0; j < bounds.length; j++) {
                        if (j > 0) {
                            signature.append(" & ");
                        }
                        signature.append(bounds[j].getTypeName());
                    }
                }
            }
            signature.append('>');
        }
        return signature.toString();
    }

    private static boolean isApiVisible(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static String constantValue(Field field) {
        int modifiers = field.getModifiers();
        Class<?> type = field.getType();
        if (!Modifier.isStatic(modifiers)
                || !Modifier.isFinal(modifiers)
                || (!type.isPrimitive() && type != String.class)) {
            return null;
        }
        try {
            if (!field.canAccess(null)) {
                field.trySetAccessible();
            }
            return String.valueOf(field.get(null));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read API constant " + field, e);
        }
    }

    public static class GenericValue<T> {

        protected T value() {
            return null;
        }

    }

    public static final class StringValue extends GenericValue<String> {

        @Override
        public String value() {
            return "value";
        }

    }

}
