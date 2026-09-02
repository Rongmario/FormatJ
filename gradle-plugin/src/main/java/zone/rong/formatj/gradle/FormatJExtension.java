package zone.rong.formatj.gradle;

import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.OptionRegistry;
import zone.rong.formatj.api.Preset;
import java.util.List;
import java.util.Map;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code formatJ { }} block.
 *
 * <pre>{@code
 * formatJ {
 *     preset = Preset.GOOGLE
 *     styleFile = file("formatj.toml")
 *     rule("indent.size", 4)
 *     sourceSets = listOf("main", "test")
 * }
 * }</pre>
 *
 * <p>Rules are addressed by the same dotted keys the style file and the CLI use, so a project can
 * move a rule between build script and style file without translating anything.
 */
public abstract class FormatJExtension {

    /** Starting point for the rules. Defaults to {@link Preset#FORMATJ}. */
    public abstract Property<Preset> getPreset();

    /** A style file whose rules are applied on top of the preset. */
    public abstract RegularFileProperty getStyleFile();

    /** Style document applied on top of the preset, for callers that hold the text rather than a file. */
    public abstract Property<String> getStyle();

    /** Individual rule overrides, applied last. Values are converted with the option's own parser. */
    public abstract MapProperty<String, String> getRules();

    /** Names of the source sets to format. Defaults to every source set in the project. */
    public abstract ListProperty<String> getSourceSets();

    /** Syntax level to parse. Defaults to the newest FormatJ knows. */
    public abstract Property<LanguageLevel> getLanguageLevel();

    /** Whether preview syntax is accepted. Defaults to false. */
    public abstract Property<Boolean> getPreviewFeatures();

    /** Whether {@code check} depends on {@code formatJavaCheck}. Defaults to true. */
    public abstract Property<Boolean> getEnforceOnCheck();

    /** Sets one rule, validating the key against the catalogue immediately. */
    public void rule(String key, Object value) {
        Option<?> option = OptionRegistry.require(key);
        String text = String.valueOf(value);
        // Parsing here means a typo fails while configuring, not halfway through formatting.
        option.parse(text);
        getRules().put(option.key(), text);
    }

    /** Sets several rules at once. */
    public void rules(Map<String, ?> values) {
        values.forEach(this::rule);
    }

    /** Convenience for {@code sourceSets = listOf(...)}. */
    public void sourceSets(String... names) {
        getSourceSets().set(List.of(names));
    }

}
