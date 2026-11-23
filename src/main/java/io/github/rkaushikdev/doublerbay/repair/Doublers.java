package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.SheetGauge;
import io.github.rkaushikdev.doublerbay.structure.Material;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sizing a doubler, which comes down to putting the load back where the metal was.
 *
 * <p>The skin that has been cut out was carrying load. The doubler has to pick that load up on one
 * side of the hole, carry it across, and put it back down on the other side, and the only things
 * carrying it into and out of the doubler are the fasteners. So the count is not a matter of taste:
 * it is the load the removed strip carried, divided by what one fastener will take in shear, rounded
 * up, on each side.
 *
 * <p>Everything else follows from the count. The fasteners go in rows at the pitch the standard
 * practices set, the rows sit at the row spacing, and the doubler is the hole plus enough skirt to
 * hold all of them with edge distance left over. A doubler drawn to look right and then filled with
 * fasteners is how a repair ends up with three rows where it needed five.
 */
public final class Doublers {

    /**
     * Single shear allowable per fastener, in pounds, for the protruding head rivets the shop uses on
     * skin repairs, keyed on the dash number. Off the standard practices chapter.
     */
    public static final Map<Integer, Integer> SHEAR_ALLOWABLE = shearAllowables();

    /** Pounds per square inch in one ksi. */
    public static final int KSI = 1000;

    private Doublers() {}

    private static Map<Integer, Integer> shearAllowables() {
        Map<Integer, Integer> held = new LinkedHashMap<>();
        held.put(3, 217);
        held.put(4, 388);
        held.put(5, 596);
        held.put(6, 862);
        held.put(8, 1524);
        held.put(10, 2400);
        held.put(12, 3456);
        return Collections.unmodifiableMap(held);
    }

    /**
     * What one fastener of a size takes in single shear, in pounds.
     *
     * @throws BadReading when there is no allowable on file for the size
     */
    public static int shearAllowable(FastenerSize size) {
        return Optional.ofNullable(SHEAR_ALLOWABLE.get(size.dash()))
                .orElseThrow(() -> new BadReading("no shear allowable on file for a " + size));
    }

    /** What a stress in ksi comes to over a section of two lengths, in pounds. */
    private static int pounds(Length one, Length other, int ksi) {
        BigDecimal milsPerInch = BigDecimal.valueOf(Length.MILS_PER_INCH);
        BigDecimal area = Decimals.over(
                Decimals.times(BigDecimal.valueOf(one.mils()), BigDecimal.valueOf(other.mils())),
                Decimals.times(milsPerInch, milsPerInch));
        return Decimals.toInt(Decimals.times(Decimals.times(area, ksi), KSI));
    }

    /** The load the strip of skin being cut out was carrying, in pounds. */
    public static int carriedLoad(Length thickness, Length width, Material material) {
        if (thickness.mils() <= 0 || width.mils() <= 0) {
            throw new BadReading("a strip of skin has a thickness and a width");
        }
        return pounds(thickness, width, material.ultimateKsi());
    }

    /**
     * What one fastener of a size takes bearing on a sheet of a gauge, in pounds: the shank, the sheet
     * and the sheet's bearing strength.
     */
    public static int bearingAllowable(FastenerSize size, Length sheet, Material material) {
        if (sheet.mils() <= 0) {
            throw new BadReading("a fastener bears on a sheet with a thickness");
        }
        return pounds(size.diameter(), sheet, material.bearingKsi());
    }

    /**
     * What one fastener carries in a sheet, which is the lesser of shearing the fastener and bearing
     * on the sheet.
     *
     * <p>On thin skin the hole gives before the rivet does. A dash five in 0.040 clad shears first, the
     * same rivet in 0.032 elongates its hole first, and a pattern counted off shear alone in the thin
     * sheet is short of fasteners by the difference.
     */
    public static int fastenerAllowable(FastenerSize size, Length sheet, Material material) {
        return Math.min(shearAllowable(size), bearingAllowable(size, sheet, material));
    }

    /**
     * How many fasteners it takes to get that load into the doubler, each side. The skin is the
     * thinner of the two sheets, since the doubler goes on a gauge heavier, so the skin is what the
     * fasteners bear on.
     */
    public static int fastenersEachSide(Length thickness, Length width, Material material, FastenerSize size) {
        return Math.max(
                Math.ceilDiv(carriedLoad(thickness, width, material), fastenerAllowable(size, thickness, material)), 1);
    }

    /**
     * Work out a doubler for a hole of a given size in a skin of a given gauge.
     *
     * <p>A doubler goes on one gauge heavier than the skin it covers, so the repair is never the
     * weakest thing in the joint.
     */
    public static DoublerPlan sizeDoubler(
            Length along, Length around, Length skin, Material material, FastenerSize size) {
        if (along.mils() <= 0 || around.mils() <= 0) {
            throw new BadReading("a doubler covers a hole with a size");
        }

        Length gauge = SheetGauge.oneStepHeavier(skin).nominal();
        Length pitch = size.diameters(FastenerSize.MIN_PITCH_D);
        Length rowSpacing = size.diameters(FastenerSize.MIN_ROW_SPACING_D);
        Length edge = size.diameters(FastenerSize.PREFERRED_EDGE_DISTANCE_D);

        Length longer = Length.max(along, around);
        int needed = fastenersEachSide(skin, longer, material, size);
        int perRow = Math.max(Math.floorDiv(longer.mils(), pitch.mils()) + 1, 2);
        int rows = Math.max(Math.ceilDiv(needed, perRow), 1);

        Length skirt = edge.plus(rowSpacing.times(rows - 1));
        return new DoublerPlan(
                gauge,
                material,
                along.plus(skirt.times(2)),
                around.plus(skirt.times(2)),
                size,
                rows,
                perRow,
                pitch,
                rowSpacing,
                edge);
    }
}
