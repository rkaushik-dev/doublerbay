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
 * A lightning strike, which is a burn and a hundred small questions.
 *
 * <p>The current has to get on and off the aeroplane, and it does that at whatever is sharpest and
 * most conductive, which on a skin panel means a fastener head or a panel edge. What is left is a burn
 * mark and a cluster of pits, each of them a tiny local melt with a heat affected zone under it that is
 * harder and more brittle than the metal around it.
 *
 * <p>So a strike report is a pit count, the worst pit, and where it attached. A strike on the open skin
 * is usually a blend job. The same strike on a fastener means that fastener came out of its hole with
 * an arc across the joint, and the hole under it has to be opened up and looked at whatever the surface
 * says.
 */
public record LightningBurn(
        Length along, Length around, int pitCount, Length worstPit, boolean onAFastener, boolean isEntry)
        implements Geometry {

    /**
     * Strikes with at least this many attachment points are a swept stroke rather than a single
     * attachment, and the whole zone gets looked at rather than each pit.
     */
    public static final int SWEPT_STROKE_PITS = 12;

    /** One attachment area with an area, at least one pit, and a depth to the worst of them. */
    public LightningBurn {
        Objects.requireNonNull(along, "along");
        Objects.requireNonNull(around, "around");
        Objects.requireNonNull(worstPit, "worstPit");
        if (along.mils() <= 0 || around.mils() <= 0) {
            throw new BadReading("a burn covers an area");
        }
        if (pitCount < 1) {
            throw new BadReading("a strike with no pits is a scorch mark, not a strike");
        }
        if (worstPit.mils() <= 0) {
            throw new BadReading("a pit has a depth");
        }
    }

    /** An entry burn on the open skin. */
    public LightningBurn(Length along, Length around, int pitCount, Length worstPit) {
        this(along, around, pitCount, worstPit, false, true);
    }

    /** The area it covers, in square inches to two places. */
    public BigDecimal area() {
        return Corrosion.squareInches(along, around);
    }

    /** Enough attachment points to be a stroke dragged across the skin. */
    public boolean isSwept() {
        return pitCount >= SWEPT_STROKE_PITS;
    }

    /** A strike that attached on a fastener arced across the joint under it. */
    public boolean needsTheFastenerOut() {
        return onAFastener;
    }

    /**
     * Pits per square inch, which is how a swept stroke is told from a cluster.
     *
     * @throws ArithmeticException when the burn is too small to have an area to two places
     */
    public BigDecimal pitDensity() {
        BigDecimal area = area();
        if (area.signum() == 0) {
            throw new ArithmeticException("a burn with no area has no density");
        }
        return Decimals.quantize(Decimals.over(BigDecimal.valueOf(pitCount), area), 2);
    }

    @Override
    public Footprint footprint(Offset centre) {
        return Footprint.square(centre, along, around);
    }

    /** A patch, read by its area. */
    @Override
    public Optional<BigDecimal> patchArea() {
        return Optional.of(area());
    }

    /** The deepest pit in the attachment area. */
    @Override
    public Optional<Length> limitDepth() {
        return Optional.of(worstPit);
    }

    /** A strike on a fastener is worse than one that is not; otherwise the deeper worst pit is worse. */
    public boolean worseThan(LightningBurn other) {
        if (onAFastener != other.onAFastener) {
            return onAFastener;
        }
        return worstPit.isGreaterThan(other.worstPit);
    }

    /** {@code entry, 4 pits, worst 0.008, on a fastener}. */
    @Override
    public String toString() {
        String end = isEntry ? "entry" : "exit";
        String head =
                end + ", " + pitCount + " pits, worst " + worstPit.inches().toPlainString();
        return onAFastener ? head + ", on a fastener" : head;
    }
}
