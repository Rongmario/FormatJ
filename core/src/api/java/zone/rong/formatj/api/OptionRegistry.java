package zone.rong.formatj.api;

import zone.rong.formatj.api.rules.AlignmentRules;
import zone.rong.formatj.api.rules.AnnotationRules;
import zone.rong.formatj.api.rules.BlankLineRules;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.CommentRules;
import zone.rong.formatj.api.rules.FileRules;
import zone.rong.formatj.api.rules.ImportRules;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.JavadocRules;
import zone.rong.formatj.api.rules.LambdaRules;
import zone.rong.formatj.api.rules.PatternRules;
import zone.rong.formatj.api.rules.PreservationRules;
import zone.rong.formatj.api.rules.RecordRules;
import zone.rong.formatj.api.rules.SealedRules;
import zone.rong.formatj.api.rules.SpacingRules;
import zone.rong.formatj.api.rules.SwitchRules;
import zone.rong.formatj.api.rules.TextBlockRules;
import zone.rong.formatj.api.rules.WrappingRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The catalogue of every formatting rule FormatJ understands.
 *
 * <p>Options register themselves when their declaring rule group is initialised; this class forces
 * every group to initialise so the catalogue is always complete, whichever entry point is used.
 */
public final class OptionRegistry {

    private static final Map<String, Option<?>> BY_KEY = new LinkedHashMap<>();

    /** A rule group: the dotted prefix its keys use, and the class that declares them. */
    private record Group(String prefix, Class<?> type) { }

    private static final List<Group> GROUPS =
            List.of(
                    new Group("file", FileRules.class),
                    new Group("indent", IndentRules.class),
                    new Group("wrapping", WrappingRules.class),
                    new Group("braces", BraceRules.class),
                    new Group("spacing", SpacingRules.class),
                    new Group("blank-lines", BlankLineRules.class),
                    new Group("alignment", AlignmentRules.class),
                    new Group("annotations", AnnotationRules.class),
                    new Group("imports", ImportRules.class),
                    new Group("comments", CommentRules.class),
                    new Group("javadoc", JavadocRules.class),
                    new Group("switch", SwitchRules.class),
                    new Group("records", RecordRules.class),
                    new Group("patterns", PatternRules.class),
                    new Group("sealed", SealedRules.class),
                    new Group("lambdas", LambdaRules.class),
                    new Group("text-blocks", TextBlockRules.class),
                    new Group("preservation", PreservationRules.class));

    static {
        for (Group group : GROUPS) {
            Class<?> type = group.type();
            try {
                Class.forName(type.getName(), true, type.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Rule group " + type.getName() + " could not be loaded", e);
            }
        }
    }

    private OptionRegistry() { }

    static void register(Option<?> option) {
        Option<?> previous = BY_KEY.putIfAbsent(option.key(), option);
        if (previous != null) {
            throw new IllegalStateException("Duplicate option key: " + option.key());
        }
    }

    /**
     * Every registered option, in catalogue order: groups in the order declared by {@link #GROUPS},
     * and options in declaration order within each group.
     *
     * <p>The order deliberately does not depend on registration order. A caller touching a rule class
     * before this one — {@code IndentRules.SIZE}, say — starts that class's initialisation first, so
     * its options register after every group this class loads (JLS 12.4.2: the recursive
     * {@code Class.forName} for a class already being initialised on the same thread returns at once).
     * Ordering off {@code GROUPS} keeps the catalogue stable whichever entry point is used.
     */
    public static List<Option<?>> all() {
        return List.copyOf(ordered().values());
    }

    /** Registered options whose key starts with {@code group + '.'}, in catalogue order. */
    public static List<Option<?>> group(String group) {
        String prefix = group + ".";
        List<Option<?>> matches = new ArrayList<>();
        for (Option<?> option : ordered().values()) {
            if (option.key().startsWith(prefix)) {
                matches.add(option);
            }
        }
        return List.copyOf(matches);
    }

    /** The distinct group prefixes present in the catalogue, in catalogue order. */
    public static List<String> groups() {
        List<String> names = new ArrayList<>();
        for (Option<?> option : ordered().values()) {
            String name = option.key().substring(0, option.key().indexOf('.'));
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    public static Optional<Option<?>> find(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    /** Looks up an option, failing with a readable message when the key is unknown. */
    public static Option<?> require(String key) {
        Option<?> option = BY_KEY.get(key);
        if (option == null) {
            throw new IllegalArgumentException("Unknown option '" + key + "'. Run --dump-config to list every option.");
        }
        return option;
    }

    /** Read-only view of the whole catalogue keyed by dotted key, in catalogue order. */
    public static Map<String, Option<?>> asMap() {
        return Collections.unmodifiableMap(ordered());
    }

    /** The catalogue re-ordered by group, with any option from an unlisted group kept at the end. */
    private static Map<String, Option<?>> ordered() {
        Map<String, Option<?>> remaining = new LinkedHashMap<>(BY_KEY);
        Map<String, Option<?>> result = new LinkedHashMap<>(remaining.size());
        for (Group group : GROUPS) {
            String prefix = group.prefix() + ".";
            remaining.values().removeIf(option -> {
                if (!option.key().startsWith(prefix)) {
                    return false;
                }
                result.put(option.key(), option);
                return true;
            });
        }
        result.putAll(remaining);
        return result;
    }
}
