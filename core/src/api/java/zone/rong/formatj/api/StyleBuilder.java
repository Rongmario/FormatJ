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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Mutable builder for a {@link Style}.
 *
 * <p>Rules are reachable two ways: through the typed group methods below, which is what most callers
 * want, or through {@link #set(Option, Object)} and {@link #setRaw(String, String)} for tools that
 * carry rules as untyped key-value pairs, such as the CLI's {@code --set} flag.
 */
public final class StyleBuilder {

    private final Map<String, Object> values;

    StyleBuilder() {
        this.values = new LinkedHashMap<>();
    }

    StyleBuilder(Map<String, Object> seed) {
        this.values = new LinkedHashMap<>(seed);
    }

    /** Sets one option. */
    public <T> StyleBuilder set(Option<T> option, T value) {
        values.put(option.key(), option.cast(value));
        return this;
    }

    /** Clears one option, restoring its default. */
    public StyleBuilder unset(Option<?> option) {
        values.remove(option.key());
        return this;
    }

    /** Sets one option from its dotted key and textual value, as used by config files and CLIs. */
    public StyleBuilder setRaw(String key, String value) {
        Option<?> option = OptionRegistry.require(key);
        values.put(option.key(), option.parse(value));
        return this;
    }

    /** Applies every explicit setting of {@code other} on top of this builder. */
    public StyleBuilder apply(Style other) {
        values.putAll(other.explicitValues());
        return this;
    }

    public StyleBuilder file(Consumer<FileRules.Builder> rules) {
        rules.accept(new FileRules.Builder(this));
        return this;
    }

    public StyleBuilder indent(Consumer<IndentRules.Builder> rules) {
        rules.accept(new IndentRules.Builder(this));
        return this;
    }

    public StyleBuilder wrapping(Consumer<WrappingRules.Builder> rules) {
        rules.accept(new WrappingRules.Builder(this));
        return this;
    }

    public StyleBuilder braces(Consumer<BraceRules.Builder> rules) {
        rules.accept(new BraceRules.Builder(this));
        return this;
    }

    public StyleBuilder spacing(Consumer<SpacingRules.Builder> rules) {
        rules.accept(new SpacingRules.Builder(this));
        return this;
    }

    public StyleBuilder blankLines(Consumer<BlankLineRules.Builder> rules) {
        rules.accept(new BlankLineRules.Builder(this));
        return this;
    }

    public StyleBuilder alignment(Consumer<AlignmentRules.Builder> rules) {
        rules.accept(new AlignmentRules.Builder(this));
        return this;
    }

    public StyleBuilder annotations(Consumer<AnnotationRules.Builder> rules) {
        rules.accept(new AnnotationRules.Builder(this));
        return this;
    }

    public StyleBuilder imports(Consumer<ImportRules.Builder> rules) {
        rules.accept(new ImportRules.Builder(this));
        return this;
    }

    public StyleBuilder comments(Consumer<CommentRules.Builder> rules) {
        rules.accept(new CommentRules.Builder(this));
        return this;
    }

    public StyleBuilder javadoc(Consumer<JavadocRules.Builder> rules) {
        rules.accept(new JavadocRules.Builder(this));
        return this;
    }

    public StyleBuilder switches(Consumer<SwitchRules.Builder> rules) {
        rules.accept(new SwitchRules.Builder(this));
        return this;
    }

    public StyleBuilder records(Consumer<RecordRules.Builder> rules) {
        rules.accept(new RecordRules.Builder(this));
        return this;
    }

    public StyleBuilder patterns(Consumer<PatternRules.Builder> rules) {
        rules.accept(new PatternRules.Builder(this));
        return this;
    }

    public StyleBuilder sealedTypes(Consumer<SealedRules.Builder> rules) {
        rules.accept(new SealedRules.Builder(this));
        return this;
    }

    public StyleBuilder lambdas(Consumer<LambdaRules.Builder> rules) {
        rules.accept(new LambdaRules.Builder(this));
        return this;
    }

    public StyleBuilder textBlocks(Consumer<TextBlockRules.Builder> rules) {
        rules.accept(new TextBlockRules.Builder(this));
        return this;
    }

    public StyleBuilder preservation(Consumer<PreservationRules.Builder> rules) {
        rules.accept(new PreservationRules.Builder(this));
        return this;
    }

    /** Snapshots the current settings into an immutable style. */
    public Style build() {
        return new Style(values);
    }

}
