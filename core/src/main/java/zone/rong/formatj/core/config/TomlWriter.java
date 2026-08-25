package zone.rong.formatj.core.config;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.OptionRegistry;
import zone.rong.formatj.api.Style;
import java.util.List;

/**
 * Renders a {@link Style} as a commented TOML document.
 *
 * <p>Used by {@code --dump-config}, which is how a user discovers the rule catalogue without reading
 * a website: every option, its documentation, its legal values and its current setting.
 */
public final class TomlWriter {

    private TomlWriter() { }

    /** The whole catalogue with this style's effective values, grouped and commented. */
    public static String write(Style style) {
        return write(style, true);
    }

    /**
     * @param includeDocumentation whether to emit the per-option comments
     */
    public static String write(Style style, boolean includeDocumentation) {
        StringBuilder out = new StringBuilder();
        out.append("# FormatJ style file. Every option is listed with its effective value.\n");
        out.append("# Delete anything you are happy to leave at its default.\n");
        for (String group : OptionRegistry.groups()) {
            out.append('\n').append('[').append(group).append("]\n");
            for (Option<?> option : OptionRegistry.group(group)) {
                if (includeDocumentation) {
                    out.append("# ").append(option.description()).append('.');
                    List<String> allowed = option.allowedValues();
                    if (!allowed.isEmpty()) {
                        out.append(" One of: ").append(String.join(", ", allowed)).append('.');
                    }
                    out.append('\n');
                }
                out.append(shortKey(option)).append(" = ").append(render(style, option)).append('\n');
            }
        }
        return out.toString();
    }

    private static String shortKey(Option<?> option) {
        return option.key().substring(option.key().indexOf('.') + 1);
    }

    private static <T> String render(Style style, Option<T> option) {
        return option.render(style.get(option));
    }

}
