package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.Objects;

/**
 * A worked fastener hole reamed out to a bigger fastener rather than covered with a doubler.
 *
 * <p>A hole that has been elongated or worked oval is not repaired by putting the same fastener
 * back in it. It is reamed round to the next size the shop stocks and an oversize goes in, as long
 * as the bigger fastener still sits at its edge distance. {@link Oversizes#oversizeFor} works one out.
 */
public record Oversize(FastenerSize from, FastenerSize to, Length bore, Length edgeDistance) {

    /** An oversize bigger than what it replaces, that cleans the bore up and keeps its edge distance. */
    public Oversize {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(bore, "bore");
        Objects.requireNonNull(edgeDistance, "edgeDistance");
        if (to.compareTo(from) <= 0) {
            throw new BadReading("an oversize is bigger than the fastener it replaces");
        }
        if (to.diameter().isLessThan(bore)) {
            throw new BadReading("an oversize cleans the worked bore up");
        }
        if (edgeDistance.isLessThan(to.minEdgeDistance())) {
            throw new BadReading("an oversize keeps its edge distance");
        }
    }

    /** How many stocked sizes up the hole went. */
    public int steps() {
        return FastenerSize.STOCKED_DASHES.indexOf(to.dash()) - FastenerSize.STOCKED_DASHES.indexOf(from.dash());
    }

    /** {@code ream -5 to -6, 0.188 at 0.500 edge distance}. */
    @Override
    public String toString() {
        return "ream " + from + " to " + to + ", " + to.diameter().inches().toPlainString() + " at "
                + edgeDistance.inches().toPlainString() + " edge distance";
    }
}
