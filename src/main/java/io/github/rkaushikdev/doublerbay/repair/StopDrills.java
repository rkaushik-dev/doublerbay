package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import java.math.BigDecimal;
import java.util.List;

/**
 * Sizing the stop drill for a crack, which comes off the gauge it is in.
 *
 * <p>The standard practices give one drill for thin skin and step it up as the skin gets heavier, so
 * the radius left at the tip stays in proportion to the metal carrying the load round it. A hole too
 * small for the gauge leaves a radius the crack runs straight through.
 *
 * <p>A hole is also a hole, and it has to keep its edge distance. A crack running up to a panel edge
 * cannot be stop drilled at that end, and the card goes to a repair that cuts the crack out instead.
 */
public final class StopDrills {

    /** The holes stay this many of their own diameters clear of the nearest edge. */
    public static final BigDecimal EDGE_DIAMETERS = new BigDecimal("2.0");

    /** What every drilled tip is looked at with. */
    public static final InspectionMethod CHECKED_WITH = InspectionMethod.HFEC;

    /** One step of the drill table: skin up to this gauge takes this drill. */
    private record Step(Length upTo, Length drill) {}

    /** Number 40 to 0.050, number 30 to 0.100, and 3/16 on anything heavier. */
    private static final List<Step> TABLE = List.of(
            new Step(Length.ofInches("0.050"), Length.ofInches("0.098")),
            new Step(Length.ofInches("0.100"), Length.ofInches("0.128")));

    /** The drill for skin heavier than the last step. */
    public static final Length HEAVY_DRILL = Length.ofInches("0.188");

    private StopDrills() {}

    /** The drill for a crack in skin of this gauge. */
    public static Length drillFor(Length skin) {
        if (skin.mils() <= 0) {
            throw new Refused("no skin", "a crack is drilled in skin with a thickness");
        }
        return TABLE.stream()
                .filter(step -> skin.isAtMost(step.upTo()))
                .map(Step::drill)
                .findFirst()
                .orElse(HEAVY_DRILL);
    }

    /**
     * Stop drill a crack in skin of a gauge, a distance off the nearest edge.
     *
     * @throws Refused when a tip was not found, when it has been drilled already, or when the holes
     *     would come inside their edge distance
     */
    public static StopDrill drill(Crack crack, Length skin, Length edgeMargin) {
        if (!crack.isBounded()) {
            throw new Refused("tips not found", "a crack is drilled at two tips somebody found");
        }
        if (crack.stopDrilled()) {
            throw new Refused("already drilled", "the tips of this crack were drilled before");
        }
        Length diameter = drillFor(skin);
        Length clear = diameter.times(EDGE_DIAMETERS);
        if (edgeMargin.isLessThan(clear)) {
            throw new Refused(
                    "too near the edge",
                    "a " + diameter.inches().toPlainString() + " hole wants "
                            + clear.inches().toPlainString() + " to the edge and has "
                            + edgeMargin.inches().toPlainString());
        }
        return new StopDrill(diameter, crack.length(), crack.length().plus(diameter), CHECKED_WITH);
    }
}
