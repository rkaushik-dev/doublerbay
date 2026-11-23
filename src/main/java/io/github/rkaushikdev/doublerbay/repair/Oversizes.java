package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.damage.Opening;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.Optional;

/**
 * Whether a worked fastener hole can be put right with a bigger fastener.
 *
 * <p>The shop goes up at most two stocked sizes on one hole. Past that the hole has eaten into the
 * ligament between it and its neighbours and the joint is no longer the joint the row was sized for,
 * so it gets a doubler. A torn hole is never oversized: the tears are crack starters and are cut back
 * to clean metal, which makes it a cut out and not a hole any more.
 */
public final class Oversizes {

    /** The most stocked sizes a hole is taken up by before it is a doubler instead. */
    public static final int MOST_STEPS = 2;

    private Oversizes() {}

    /**
     * The smallest stocked oversize that cleans up a worked hole and keeps its edge distance, within two
     * sizes of what was fitted, or nothing when no oversize will do.
     *
     * @throws Refused for an opening that was never a fastener hole
     */
    public static Optional<Oversize> oversizeFor(Opening worked, FastenerSize fitted, Length edgeDistance) {
        if (!worked.wasAFastenerHole()) {
            throw new Refused("not a fastener hole", "only a worked fastener hole is reamed to an oversize");
        }
        if (worked.needsTrimmingBeforeRepair()) {
            return Optional.empty();
        }
        Length bore = worked.wide();
        return FastenerSize.sizesUp(fitted).stream()
                .limit(MOST_STEPS)
                .filter(size -> size.diameter().isAtLeast(bore))
                .filter(size -> edgeDistance.isAtLeast(size.minEdgeDistance()))
                .findFirst()
                .map(size -> new Oversize(fitted, size, bore, edgeDistance));
    }
}
