package io.github.rkaushikdev.doublerbay.assess;

import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Region;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The rules that follow from where on the panel a damage landed.
 *
 * <p>The limit tables already vary by region, so a dent on a chem mill step is measured against a
 * different depth cap than one in open field. What the tables do not carry is the handful of rules
 * that are about the feature itself rather than about the size of the damage.
 *
 * <p>Three of them. A chem mill step is where the skin changes thickness, and the residual left after
 * a blend has to be measured against the thin side rather than the nominal, because that is what is
 * actually there. A cutout corner is a stress concentration and a crack pointing into one is a
 * different problem from a crack pointing away. And a damage under an existing doubler cannot be
 * inspected without taking the doubler off, so the disposition has to say so rather than quietly
 * assume somebody looked.
 *
 * <p>Like the joint rules, these only ever make the answer worse.
 */
public final class Regions {

    /** How much thinner the thin side of a step is, as a share of the nominal. */
    public static final BigDecimal STEP_SHARE = new BigDecimal("0.75");

    /** A crack this long at a cutout corner goes straight to the manufacturer. */
    public static final Length CORNER_CRACK = new Length(200);

    /** The regions where the thickness changes, which the step rule speaks for. */
    public static final Set<Region> STEP_REGIONS =
            Collections.unmodifiableSet(EnumSet.of(Region.CHEM_MILL_STEP, Region.LAND));

    /** The regions round a cutout, which the corner rule speaks for. */
    public static final Set<Region> CORNER_REGIONS =
            Collections.unmodifiableSet(EnumSet.of(Region.CUTOUT_CORNER, Region.CUTOUT_EDGE));

    /** The region rules, in the order they run. */
    public static final List<Function<RegionCase, Optional<Check>>> REGION_CHECKS =
            List.of(Regions::stepResidualCheck, Regions::cornerCrackCheck, Regions::underDoublerCheck);

    private Regions() {}

    /** On a step, a blend is measured against the thin side, not the nominal. */
    public static Optional<Check> stepResidualCheck(RegionCase held) {
        if (!STEP_REGIONS.contains(held.region())) {
            return Optional.empty();
        }
        Optional<Length> cut = held.subject().cutDepth();
        if (cut.isEmpty()) {
            return Optional.empty();
        }
        Length thin = held.thinSide();
        Length left = new Length(thin.mils() - cut.get().mils());
        String written =
                left.inches().toPlainString() + " in left of " + thin.inches().toPlainString() + " in on the thin side";
        return Rules.flag(
                "step residual",
                left.mils() <= 0,
                written,
                "a blend here goes through, " + written,
                Disposition.REPAIRABLE);
    }

    /** A crack at a cutout corner is a different problem from one anywhere else. */
    public static Optional<Check> cornerCrackCheck(RegionCase held) {
        if (!CORNER_REGIONS.contains(held.region())
                || !(held.subject().finding().geometry() instanceof Crack crack)) {
            return Optional.empty();
        }
        String where = held.region() == Region.CUTOUT_CORNER ? "at the corner" : "in the keep out";
        String length = crack.length().inches().toPlainString();
        return Rules.flag(
                "cutout corner",
                crack.length().isAtLeast(CORNER_CRACK),
                length + " in crack " + where,
                length + " in crack " + where + ", past what the book covers",
                Disposition.OUT_OF_LIMITS);
    }

    /** Nothing under a fitted doubler was measured without taking it off. */
    public static Optional<Check> underDoublerCheck(RegionCase held) {
        if (held.region() != Region.UNDER_DOUBLER) {
            return Optional.empty();
        }
        return Rules.answer(
                false,
                "under a doubler",
                "the doubler comes off before this can be measured or blended",
                Disposition.REPAIRABLE);
    }

    /** Every region rule that has something to say about this damage. */
    public static List<Check> checksFor(Case held) {
        RegionCase onTheFeature = new RegionCase(held);
        return REGION_CHECKS.stream()
                .map(check -> check.apply(onTheFeature))
                .flatMap(Optional::stream)
                .toList();
    }
}
