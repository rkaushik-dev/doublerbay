package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Corrosion, which is measured as a patch and judged on what is under it.
 *
 * <p>A dent is a shape. Corrosion is a stain with metal missing under it, and the depth is not known
 * until somebody has taken the product off and put a depth gauge in the worst of it. So a corrosion
 * report carries the area it covers, the worst depth found once it was cleaned up, and two flags that
 * change everything about the answer.
 *
 * <p>Pitting means the loss is not spread evenly and the average depth is meaningless, so the worst
 * pit governs. Exfoliation means it is running along the grain and lifting the metal in layers, which
 * no amount of surface blending reaches, and it goes to a repair whatever the depth says. Those two
 * are why corrosion cannot be graded off a depth alone.
 */
public record Corrosion(Length along, Length around, Length worstDepth, boolean pitted, boolean exfoliating)
        implements Geometry {

    private static final BigDecimal SQUARE_MILS_PER_SQUARE_INCH =
            BigDecimal.valueOf((long) Length.MILS_PER_INCH * Length.MILS_PER_INCH);

    /** A corroded patch with an area, a worst depth that does not run backwards, and real exfoliation. */
    public Corrosion {
        Objects.requireNonNull(along, "along");
        Objects.requireNonNull(around, "around");
        Objects.requireNonNull(worstDepth, "worstDepth");
        if (along.mils() <= 0 || around.mils() <= 0) {
            throw new BadReading("corrosion covers an area");
        }
        if (worstDepth.mils() < 0) {
            throw new BadReading("a corrosion depth does not run backwards");
        }
        if (exfoliating && worstDepth.mils() == 0) {
            throw new BadReading("exfoliation is not a surface stain");
        }
    }

    /** A corroded patch that is neither pitted nor exfoliating. */
    public Corrosion(Length along, Length around, Length worstDepth) {
        this(along, around, worstDepth, false, false);
    }

    /** Area in square inches, to two places, from two lengths in mils. */
    public static BigDecimal squareInches(Length along, Length around) {
        BigDecimal product = Decimals.times(BigDecimal.valueOf(along.mils()), around.mils());
        return Decimals.quantize(Decimals.over(product, SQUARE_MILS_PER_SQUARE_INCH), 2);
    }

    /** The area the patch covers, in square inches to two places. */
    public BigDecimal area() {
        return squareInches(along, around);
    }

    /** Product taken off and no metal loss under it, which is the easy answer. */
    public boolean isSurfaceOnly() {
        return worstDepth.mils() == 0 && !exfoliating;
    }

    /** Whether any metal has gone from under the stain. */
    public boolean reachesBelowTheSurface() {
        return !isSurfaceOnly();
    }

    /** Exfoliation runs along the grain and blending does not reach it. */
    public boolean needsARepairWhateverTheDepth() {
        return exfoliating;
    }

    /** The number the tables read, which is the worst pit and never an average. */
    public Length governingDepth() {
        return worstDepth;
    }

    /** How much skin the cleanup covers once it is feathered out all round. */
    public BigDecimal blendArea(Taper taper) {
        Length runout = taper.runout(worstDepth);
        return squareInches(along.plus(runout.times(2)), around.plus(runout.times(2)));
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

    /** The worst pit, which is the only depth corrosion is judged on. */
    @Override
    public Optional<Length> limitDepth() {
        return Optional.of(worstDepth);
    }

    /** Exfoliation is worse than anything that is not; otherwise the deeper patch is worse. */
    public boolean worseThan(Corrosion other) {
        if (exfoliating != other.exfoliating) {
            return exfoliating;
        }
        return worstDepth.isGreaterThan(other.worstDepth);
    }

    /** {@code 12.00 sq in, 0.006 deep, pitted and exfoliating}. */
    @Override
    public String toString() {
        String head = Decimals.text(area()) + " sq in, " + worstDepth.inches().toPlainString() + " deep";
        List<String> marks = new ArrayList<>();
        if (pitted) {
            marks.add("pitted");
        }
        if (exfoliating) {
            marks.add("exfoliating");
        }
        return marks.isEmpty() ? head : head + ", " + String.join(" and ", marks);
    }
}
