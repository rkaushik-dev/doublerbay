package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Material;
import java.util.Objects;

/**
 * A patch, its fasteners, and how they are laid out.
 *
 * <p>The fasteners go in rows at the pitch the standard practices set, the rows sit at the row
 * spacing, and the doubler is the hole plus enough skirt to hold all of them with edge distance left
 * over. {@link Doublers#sizeDoubler} works one out from the hole and the skin.
 */
public record DoublerPlan(
        Length gauge,
        Material material,
        Length along,
        Length around,
        FastenerSize size,
        int rowsEachSide,
        int perRow,
        Length pitch,
        Length rowSpacing,
        Length edgeDistance) {

    /** A plan with at least one row each side and at least two fasteners in a row. */
    public DoublerPlan {
        Objects.requireNonNull(gauge, "gauge");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(along, "along");
        Objects.requireNonNull(around, "around");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(pitch, "pitch");
        Objects.requireNonNull(rowSpacing, "rowSpacing");
        Objects.requireNonNull(edgeDistance, "edgeDistance");
        if (rowsEachSide < 1) {
            throw new BadReading("a doubler has at least one row of fasteners each side");
        }
        if (perRow < 2) {
            throw new BadReading("a row of one fastener is a hinge, not a joint");
        }
    }

    /**
     * A doubler over a hole of a size, with its rows at the spacings the fastener size sets: pitch and
     * row spacing at their least and edge distance at the preferred, the skirt on every side.
     */
    public static DoublerPlan laidOut(
            Length gauge,
            Material material,
            Length holeAlong,
            Length holeAround,
            FastenerSize size,
            int rowsEachSide,
            int perRow) {
        Length rowSpacing = size.minRowSpacing();
        Length edge = size.preferredEdgeDistance();
        Length skirt = skirtOf(edge, rowSpacing, rowsEachSide);
        return new DoublerPlan(
                gauge,
                material,
                holeAlong.plus(skirt.times(2)),
                holeAround.plus(skirt.times(2)),
                size,
                rowsEachSide,
                perRow,
                size.minPitch(),
                rowSpacing,
                edge);
    }

    private static Length skirtOf(Length edge, Length rowSpacing, int rows) {
        return edge.plus(rowSpacing.times(rows - 1));
    }

    /** Every hole in the patch, four sides of it. */
    public int fastenerCount() {
        return rowsEachSide * perRow * 4;
    }

    /** How far the doubler reaches past the hole on one side. */
    public Length skirt() {
        return skirtOf(edgeDistance, rowSpacing, rowsEachSide);
    }

    /** {@code 0.050 2024-T3 CLAD, 5.282 by 5.282, 48 off -5}, the way the parts line reads. */
    @Override
    public String toString() {
        return gauge.inches().toPlainString() + " " + material + ", "
                + along.inches().toPlainString() + " by " + around.inches().toPlainString() + ", " + fastenerCount()
                + " off " + size;
    }
}
