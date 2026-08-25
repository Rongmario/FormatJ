package zone.rong.formatj.core.imports;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.ImportRules;
import zone.rong.formatj.api.rules.SortOrder;
import zone.rong.formatj.api.rules.StaticImportPlacement;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The order the {@code imports.*} rules ask for, and the groups they divide imports into.
 *
 * <p>Shared by the rewrite that reorders the declarations and by the emitter that puts a blank line
 * between groups. Those are two different stages doing two different kinds of work — one changes the
 * program's tokens and must declare it, the other only adds whitespace — but they have to agree on
 * where a group ends, so the answer lives in one place.
 */
public final class ImportOrder {

    private ImportOrder() { }

    /** Whether any {@code imports.*} rule asks for the declarations to be put in an order. */
    public static boolean sorts(Style style) {
        return style.get(ImportRules.ORDER) != SortOrder.PRESERVE;
    }

    /**
     * The contiguous run of import declarations at the top of a file, or an empty list.
     *
     * <p>Empty when anything other than an import sits between the first import and the last. That
     * does not happen in a file the parser understood, and treating it as "leave well alone" is
     * cheaper than deciding what reordering would even mean.
     */
    public static List<ImportEntry> run(GreenNode compilationUnit) {
        List<GreenNode> children = compilationUnit.children();
        int first = -1;
        int last = -1;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).kind() == SyntaxKind.IMPORT_DECLARATION) {
                first = first < 0 ? i : first;
                last = i;
            }
        }
        if (first < 0) {
            return List.of();
        }

        List<ImportEntry> entries = new ArrayList<>(last - first + 1);
        for (int i = first; i <= last; i++) {
            ImportEntry entry = ImportEntry.of(children.get(i));
            if (entry == null) {
                return List.of();
            }
            entries.add(entry);
        }
        return List.copyOf(entries);
    }

    /** The index in the compilation unit of the first declaration of the import run, or -1. */
    public static int runStart(GreenNode compilationUnit) {
        List<GreenNode> children = compilationUnit.children();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).kind() == SyntaxKind.IMPORT_DECLARATION) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The entries in the order the style asks for.
     *
     * <p>The sort is stable, so {@code imports.order = preserve} still lets the grouping rules
     * partition the list without disturbing the order the author chose inside each part.
     */
    public static List<ImportEntry> sorted(List<ImportEntry> entries, Style style) {
        List<String> groups = style.get(ImportRules.GROUPS);
        SortOrder order = style.get(ImportRules.ORDER);
        StaticImportPlacement placement = style.get(ImportRules.STATIC_PLACEMENT);
        boolean modulesFirst = style.get(ImportRules.MODULE_IMPORTS_FIRST);

        Comparator<ImportEntry> byBucket = Comparator
                .comparingInt((ImportEntry entry) -> moduleBucket(entry, modulesFirst))
                .thenComparingInt(entry -> staticBucket(entry, placement))
                .thenComparingInt(entry -> groupWithin(entry, groups, placement, modulesFirst));
        Comparator<ImportEntry> comparator =
                switch (order) {
                    case PRESERVE -> byBucket;
                    case ASCENDING -> byBucket.thenComparing(ImportEntry::name);
                    case DESCENDING -> byBucket.thenComparing(Comparator.comparing(ImportEntry::name).reversed());
                };

        List<ImportEntry> sorted = new ArrayList<>(entries);
        sorted.sort(comparator);
        return List.copyOf(sorted);
    }

    /** Whether a blank line belongs between two neighbouring imports under this style. */
    public static boolean separates(ImportEntry previous, ImportEntry next, Style style) {
        if (!style.get(ImportRules.BLANK_LINE_BETWEEN_GROUPS) || !sorts(style)) {
            return false;
        }
        List<String> groups = style.get(ImportRules.GROUPS);
        StaticImportPlacement placement = style.get(ImportRules.STATIC_PLACEMENT);
        boolean modulesFirst = style.get(ImportRules.MODULE_IMPORTS_FIRST);
        return moduleBucket(previous, modulesFirst) != moduleBucket(next, modulesFirst)
                || staticBucket(previous, placement) != staticBucket(next, placement)
                || groupWithin(previous, groups, placement, modulesFirst)
                        != groupWithin(next, groups, placement, modulesFirst);
    }

    /**
     * The group an import falls in, once the buckets have had their say.
     *
     * <p>Static imports and module imports that sit in a bucket of their own are one group each. The
     * package prefixes exist to keep the JDK away from everybody else's code in the main block; using
     * them again inside a block of four static imports would scatter it across four paragraphs for no
     * reader's benefit. Where static imports are interleaved instead, they are part of the main block
     * and the prefixes apply to them like anything else.
     */
    private static int groupWithin(
            ImportEntry entry,
            List<String> groups,
            StaticImportPlacement placement,
            boolean modulesFirst) {
        if (modulesFirst && entry.isModule()) {
            return 0;
        }
        if (entry.isStatic() && placement != StaticImportPlacement.INLINE) {
            return 0;
        }
        return group(entry, groups);
    }

    /**
     * Which group a name falls in: the longest declared prefix that matches it.
     *
     * <p>{@code *} is the catch-all and matches nothing on its own, so a name that no prefix claims
     * lands there. A file whose groups leave out the catch-all puts the strays last.
     */
    static int group(ImportEntry entry, List<String> groups) {
        int best = -1;
        int bestLength = -1;
        int catchAll = groups.size();
        for (int i = 0; i < groups.size(); i++) {
            String prefix = groups.get(i);
            if (prefix.equals("*")) {
                catchAll = i;
                continue;
            }
            if (matches(entry.name(), prefix) && prefix.length() > bestLength) {
                best = i;
                bestLength = prefix.length();
            }
        }
        return best < 0 ? catchAll : best;
    }

    private static boolean matches(String name, String prefix) {
        return name.equals(prefix) || name.startsWith(prefix + ".");
    }

    private static int moduleBucket(ImportEntry entry, boolean modulesFirst) {
        return modulesFirst && entry.isModule() ? 0 : 1;
    }

    private static int staticBucket(ImportEntry entry, StaticImportPlacement placement) {
        return switch (placement) {
            case FIRST -> entry.isStatic() ? 0 : 1;
            case LAST -> entry.isStatic() ? 1 : 0;
            case INLINE -> 0;
        };
    }

}
