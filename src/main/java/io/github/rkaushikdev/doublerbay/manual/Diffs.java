package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Ratio;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * What changed between two revisions, and which direction it moved.
 *
 * <p>When a revision lands somebody has to work out what it means for the damage already on the
 * aeroplanes. A limit that got looser is a nuisance at worst, and half the deferrals against it could
 * probably be closed. A limit that got tighter is the other thing entirely: every open finding
 * assessed under the old number has to be looked at again, and some of them are now out of limits
 * on an aeroplane that is flying.
 *
 * <p>So a diff is not a list of differences, it is a list of differences with a direction on each
 * one. Tighter and looser are decided per field, because a smaller number is tighter on a depth cap
 * and looser on a residual floor, and mixing those up produces exactly the wrong work list.
 */
public final class Diffs {

    /** The caps first, where a smaller value is a tighter limit, then the floors, where it is looser. */
    private static final List<Field<?>> FIELDS = List.of(
            new Field<Length>("max_depth", LimitSet::maxDepth, true),
            new Field<Ratio>("max_depth_fraction", LimitSet::maxDepthFraction, true),
            new Field<Ratio>("hoop_loaded_share", LimitSet::hoopLoadedShare, true),
            new Field<Length>("max_length", LimitSet::maxLength, true),
            new Field<BigDecimal>("max_area", LimitSet::maxArea, true),
            new Field<Ratio>("max_sharpness", LimitSet::maxSharpness, true),
            new Field<Length>("min_residual", LimitSet::minResidual, false),
            new Field<Ratio>("min_residual_fraction", LimitSet::minResidualFraction, false),
            new Field<Length>("min_edge_margin", LimitSet::minEdgeMargin, false),
            new Field<BigDecimal>("min_edge_margin_diameters", LimitSet::minEdgeMarginDiameters, false));

    private Diffs() {}

    /**
     * Work out what moved between two issues of the same table.
     *
     * <p>A row is matched across the two issues by its key. A row that stopped or started allowing
     * anything reports only that; otherwise each limit that changed is reported, in the order caps
     * then floors.
     */
    public static RevisionDiff diffTables(LimitTable before, LimitTable after) {
        Map<LimitKey, LimitSet> was = byKey(before);
        Map<LimitKey, LimitSet> now = byKey(after);
        Comparator<LimitKey> printed = Comparator.comparing(LimitKey::toString);

        List<LimitKey> added = now.keySet().stream()
                .filter(key -> !was.containsKey(key))
                .sorted(printed)
                .toList();
        List<LimitKey> removed = was.keySet().stream()
                .filter(key -> !now.containsKey(key))
                .sorted(printed)
                .toList();

        List<LimitChange> changes = new ArrayList<>();
        List<LimitKey> onBoth =
                was.keySet().stream().filter(now::containsKey).sorted(printed).toList();
        for (LimitKey key : onBoth) {
            LimitSet old = was.get(key);
            LimitSet updated = now.get(key);
            if (old.allowed() != updated.allowed()) {
                changes.add(new LimitChange(
                        key,
                        "allowed",
                        Optional.of(old.allowed()),
                        Optional.of(updated.allowed()),
                        old.allowed() ? Direction.TIGHTER : Direction.LOOSER));
                continue;
            }
            for (Field<?> field : FIELDS) {
                field.change(key, old, updated).ifPresent(changes::add);
            }
        }
        return new RevisionDiff(
                before.reference().toString(), before.revision(), after.revision(), added, removed, changes);
    }

    private static Map<LimitKey, LimitSet> byKey(LimitTable table) {
        Map<LimitKey, LimitSet> held = new LinkedHashMap<>();
        for (LimitRow row : table.rows()) {
            held.put(row.key(), row.limits());
        }
        return held;
    }

    /** One limit on a row, read the same way off both issues. */
    private record Field<T extends Comparable<T>>(
            String name, Function<LimitSet, Optional<T>> read, boolean smallerIsTighter) {

        Optional<LimitChange> change(LimitKey key, LimitSet old, LimitSet updated) {
            Optional<T> was = read.apply(old);
            Optional<T> now = read.apply(updated);
            if (same(was, now)) {
                return Optional.empty();
            }
            return Optional.of(
                    new LimitChange(key, name, was.map(value -> value), now.map(value -> value), direction(was, now)));
        }

        private boolean same(Optional<T> was, Optional<T> now) {
            if (was.isEmpty() || now.isEmpty()) {
                return was.isEmpty() && now.isEmpty();
            }
            return was.get().compareTo(now.get()) == 0;
        }

        /**
         * A limit appearing where there was none is tighter. One disappearing is looser, because
         * nothing now caps what that field used to cap.
         */
        private Direction direction(Optional<T> was, Optional<T> now) {
            if (was.isEmpty() || now.isEmpty()) {
                return was.isEmpty() ? Direction.TIGHTER : Direction.LOOSER;
            }
            int moved = now.get().compareTo(was.get());
            if (smallerIsTighter) {
                return moved < 0 ? Direction.TIGHTER : Direction.LOOSER;
            }
            return moved > 0 ? Direction.TIGHTER : Direction.LOOSER;
        }
    }
}
