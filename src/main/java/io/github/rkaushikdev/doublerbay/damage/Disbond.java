package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Two bonded parts that are no longer acting as one.
 *
 * <p>A disbond means two things that were meant to act as one are not, and the area of it is the
 * whole finding because depth does not apply. Like a hole and a buckle it is not graded on a limit
 * table; what is measured is what the repair has to cover.
 */
public record Disbond(Length along, Length around, boolean tapTested, boolean reachesAnEdge) implements Geometry {

    /** A disbond covering an area. */
    public Disbond {
        Objects.requireNonNull(along, "along");
        Objects.requireNonNull(around, "around");
        if (along.mils() <= 0 || around.mils() <= 0) {
            throw new BadReading("a disbond covers an area");
        }
    }

    /** A disbond nobody has tapped out, clear of any edge. */
    public Disbond(Length along, Length around) {
        this(along, around, false, false);
    }

    /** The area it covers, in square inches to two places. */
    public BigDecimal area() {
        return Corrosion.squareInches(along, around);
    }

    /** A patch, read by its area. */
    @Override
    public Optional<BigDecimal> patchArea() {
        return Optional.of(area());
    }

    /** No depth reads off this, a disbond is an area and nothing else. */
    @Override
    public Optional<Length> limitDepth() {
        return Optional.empty();
    }

    /** A disbond that has been tapped out has a known edge to it. */
    public boolean isBounded() {
        return tapTested;
    }

    /** One that runs to a free edge has nothing holding it from growing. */
    public boolean canSpread() {
        return reachesAnEdge;
    }

    @Override
    public Footprint footprint(Offset centre) {
        return Footprint.square(centre, along, around);
    }

    /** {@code 6.00 sq in, runs to an edge, extent not tapped out}. */
    @Override
    public String toString() {
        String head = Decimals.text(area()) + " sq in";
        if (reachesAnEdge) {
            head = head + ", runs to an edge";
        }
        return tapTested ? head : head + ", extent not tapped out";
    }
}
