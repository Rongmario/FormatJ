package zone.rong.formatj.core.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * The intermediate representation the emitter produces and the layout engine prints.
 *
 * <p>This is the Wadler/Oppen pretty-printing algebra with the extensions Prettier popularised: a
 * document describes what to print and where breaking is <em>allowed</em>, and the layout engine
 * decides which of those breaks are actually taken so that lines fit. Keeping that decision in one
 * place is what makes output stable, and what makes a rule such as "break every link of the chain if
 * any link breaks" expressible instead of ad hoc.
 */
public sealed interface Doc {

    /** How a {@link Break} renders when its group fits on one line. */
    enum BreakKind {

        /** Renders as a single space when flat, a line break when broken. */
        LINE,

        /** Renders as nothing when flat, a line break when broken. */
        SOFT,

        /** Always a line break; forces every enclosing group to break. */
        HARD

    }

    /** Literal text; must not contain a line terminator. */
    record Text(String value) implements Doc {}

    /** A sequence of documents. */
    record Concat(List<Doc> parts) implements Doc {}

    /** A place a line break may be taken. */
    record Break(BreakKind kind) implements Doc {}

    /** A unit that is printed flat if it fits, or broken as a whole if it does not. */
    record Group(Doc content, boolean shouldBreak) implements Doc {}

    /** Adds {@code columns} of indentation to line breaks inside {@code content}. */
    record Indent(int columns, Doc content) implements Doc {}

    /** Indents {@code content} to the current column rather than by a fixed amount. */
    record Align(Doc content) implements Doc {}

    /** Fills as many parts onto each line as fit, breaking between them as needed. */
    record Fill(List<Doc> parts) implements Doc {}

    /** Prints {@code broken} when the enclosing group breaks, {@code flat} when it does not. */
    record IfBreak(Doc broken, Doc flat) implements Doc {}

    /** Defers {@code content} to the end of the current line; how trailing comments are placed. */
    record LineSuffix(Doc content) implements Doc {}

    /** Forces every enclosing group to break without printing anything itself. */
    record BreakParent() implements Doc {}

    Doc EMPTY = new Text("");

    static Doc text(String value) {
        return value.isEmpty() ? EMPTY : new Text(value);
    }

    static Doc concat(Doc... parts) {
        return new Concat(List.of(parts));
    }

    static Doc concat(List<Doc> parts) {
        return new Concat(List.copyOf(parts));
    }

    static Doc line() {
        return new Break(BreakKind.LINE);
    }

    static Doc softLine() {
        return new Break(BreakKind.SOFT);
    }

    static Doc hardLine() {
        return new Concat(List.of(new Break(BreakKind.HARD), new BreakParent()));
    }

    static Doc group(Doc content) {
        return new Group(content, false);
    }

    static Doc breakingGroup(Doc content) {
        return new Group(content, true);
    }

    static Doc indent(int columns, Doc content) {
        return new Indent(columns, content);
    }

    static Doc align(Doc content) {
        return new Align(content);
    }

    static Doc fill(List<Doc> parts) {
        return new Fill(List.copyOf(parts));
    }

    static Doc ifBreak(Doc broken, Doc flat) {
        return new IfBreak(broken, flat);
    }

    static Doc lineSuffix(Doc content) {
        return new LineSuffix(content);
    }

    /** Forces every enclosing group to break, printing nothing itself. */
    static Doc breakParent() {
        return new BreakParent();
    }

    /** {@code separator} between each part, as one document. */
    static Doc join(Doc separator, List<Doc> parts) {
        if (parts.isEmpty()) {
            return EMPTY;
        }
        List<Doc> joined = new ArrayList<>(parts.size() * 2 - 1);
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                joined.add(separator);
            }
            joined.add(parts.get(i));
        }
        return new Concat(List.copyOf(joined));
    }

}
