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

    private static final List<Class<?>> GROUPS =
            List.of(
                    FileRules.class,
                    IndentRules.class,
                    WrappingRules.class,
                    BraceRules.class,
                    SpacingRules.class,
                    BlankLineRules.class,
                    AlignmentRules.class,
                    AnnotationRules.class,
                    ImportRules.class,
                    CommentRules.class,
                    JavadocRules.class,
                    SwitchRules.class,
                    RecordRules.class,
                    PatternRules.class,
                    SealedRules.class,
                    LambdaRules.class,
                    TextBlockRules.class,
                    PreservationRules.class);

    static {
        for (Class<?> group : GROUPS) {
            try {
                Class.forName(group.getName(), true, group.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Rule group " + group.getName() + " could not be loaded", e);
            }
        }
    }

    private OptionRegistry() {}

    static void register(Option<?> option) {
        Option<?> previous = BY_KEY.putIfAbsent(option.key(), option);
        if (previous != null) {
            throw new IllegalStateException("Duplicate option key: " + option.key());
        }
    }

    /** Every registered option, in registration order (groups in catalogue order). */
    public static List<Option<?>> all() {
        return List.copyOf(BY_KEY.values());
    }

    /** Registered options whose key starts with {@code group + '.'}. */
    public static List<Option<?>> group(String group) {
        String prefix = group + ".";
        List<Option<?>> matches = new ArrayList<>();
        for (Option<?> option : BY_KEY.values()) {
            if (option.key().startsWith(prefix)) {
                matches.add(option);
            }
        }
        return List.copyOf(matches);
    }

    /** The distinct group prefixes present in the catalogue, in catalogue order. */
    public static List<String> groups() {
        List<String> names = new ArrayList<>();
        for (Option<?> option : BY_KEY.values()) {
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

    /** Read-only view of the whole catalogue keyed by dotted key. */
    public static Map<String, Option<?>> asMap() {
        return Collections.unmodifiableMap(BY_KEY);
    }

}
