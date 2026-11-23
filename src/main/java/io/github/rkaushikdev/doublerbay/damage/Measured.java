package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * A set of damage measurements, whatever kind it belongs to.
 *
 * <p>Two things get asked of every measurement set: what skin it covers, so its neighbours can be
 * found, and what depth the limit tables read off it, if any. Everything else about a dent and a
 * crack is different and stays on the type that measures it.
 */
public interface Measured {

    /** The skin it covers, placed at a point. */
    Footprint footprint(Offset centre);

    /** The depth the limit tables read, or nothing for the kinds that have none. */
    Optional<Length> limitDepth();

    /**
     * The area an area cap reads, in square inches to two places, for the kinds measured as a patch,
     * and nothing for the kinds measured as a line or a point.
     */
    default Optional<BigDecimal> patchArea() {
        return Optional.empty();
    }
}
