package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Orientation;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import java.util.Objects;
import java.util.Optional;

/**
 * A crack, which is the one kind that has no allowable size.
 *
 * <p>Everything else on the form has a table with a number in it. A crack has a repair. What is
 * measured is therefore not whether it is inside a limit but how much of the structure it has already
 * crossed and how fast it is going to cross the rest, and that is length, direction, and whether it
 * has broken through the skin or is still buried in it.
 *
 * <p>A crack is reported by its two tips, because the tips are what get stop drilled and what get re
 * measured at the next inspection. Growth between two reports is the number the engineer actually
 * wants, and it only exists if the same two tips were found and written down both times.
 */
public record Crack(Length length, Angle bearing, boolean throughThickness, boolean stopDrilled, int tipsFound)
        implements Geometry {

    /** A crack with a length, nought to two tips found, and stop drilled only at a tip somebody found. */
    public Crack {
        Objects.requireNonNull(length, "length");
        Objects.requireNonNull(bearing, "bearing");
        if (length.mils() <= 0) {
            throw new BadReading("a crack has a length");
        }
        if (tipsFound < 0 || tipsFound > 2) {
            throw new BadReading("a crack has nought, one or two tips found");
        }
        if (stopDrilled && tipsFound == 0) {
            throw new BadReading("a crack cannot be stop drilled at a tip nobody found");
        }
    }

    /** A crack through the skin with both tips found and neither drilled. */
    public Crack(Length length, Angle bearing) {
        this(length, bearing, true, false, 2);
    }

    /** Which axis the crack lies along, or neither. */
    public Orientation orientation() {
        return Angle.orientationOf(bearing);
    }

    /** Lying along the body, which puts the larger of the two stresses across it. */
    public boolean isHoopLoaded() {
        return orientation() == Orientation.LONGITUDINAL;
    }

    /** Both tips found, so the length is a length and not a lower bound. */
    public boolean isBounded() {
        return tipsFound == 2;
    }

    /** Stop drilled at both tips, which holds it until the repair goes on. */
    public boolean isArrested() {
        return stopDrilled && isBounded();
    }

    /** How much longer it is than it was, which is what the interval is set on. */
    public Length grownFrom(Crack earlier) {
        if (!(isBounded() && earlier.isBounded())) {
            throw new BadReading("growth needs both tips found on both readings");
        }
        if (length.isLessThan(earlier.length)) {
            throw new BadReading("a crack does not get shorter");
        }
        return length.minus(earlier.length);
    }

    /**
     * The crack once both tips are stop drilled with a drill of this size. The holes are centred on
     * the tips, so the crack now runs half a diameter past each one and is a diameter longer.
     *
     * @throws BadReading when a tip was not found or the tips were drilled already
     */
    public Crack drilled(Length diameter) {
        if (!isBounded()) {
            throw new BadReading("a crack is drilled at two tips somebody found");
        }
        if (stopDrilled) {
            throw new BadReading("the tips of this crack were drilled before");
        }
        if (diameter.mils() <= 0) {
            throw new BadReading("a stop drill has a size");
        }
        return new Crack(length.plus(diameter), bearing, throughThickness, true, tipsFound);
    }

    /** A thin box along the crack, for working out what it is heading towards. */
    @Override
    public Footprint footprint(Offset centre) {
        Length thickness = new Length(10);
        if (orientation() == Orientation.CIRCUMFERENTIAL) {
            return Footprint.square(centre, thickness, length);
        }
        return Footprint.square(centre, length, thickness);
    }

    /** A crack has a length and no depth any table reads. */
    @Override
    public Optional<Length> limitDepth() {
        return Optional.empty();
    }

    /** {@code 0.750 long, 0.0 deg, part depth, 1 tips found, stop drilled}. */
    @Override
    public String toString() {
        String head = length.inches().toPlainString() + " long, " + bearing;
        if (!throughThickness) {
            head = head + ", part depth";
        }
        if (!isBounded()) {
            head = head + ", " + tipsFound + " tips found";
        }
        return stopDrilled ? head + ", stop drilled" : head;
    }
}
