package io.github.rkaushikdev.doublerbay.assess;

import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Disbond;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.damage.LightningBurn;
import io.github.rkaushikdev.doublerbay.damage.Scoring;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Ratio;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The individual checks, each one a question with an answer and a consequence.
 *
 * <p>A rule here does two things. It says whether the damage got past, and it says what the
 * disposition becomes if it did not, which is not the same for every rule. Failing a depth cap on a
 * scratch means grind it out, because the metal can be taken back and the limit met. Failing the same
 * cap on a dent means repair it, because there is nothing to grind. Failing an edge margin means
 * repair it whatever the kind, since moving the damage is not an option.
 *
 * <p>The one worth reading twice is the depth check. Whether it lands on rework or repair depends on
 * the cleanup rule for the kind and on the region the damage is sitting in, so the same 0.010 scratch
 * at the same depth on the same panel comes out as two different answers two inches apart.
 *
 * <p>Every check but the first stands aside, answering empty, when it has nothing to say about the
 * damage.
 */
public final class Rules {

    /** Walked in this order so the trail reads the way an engineer would work it. */
    public static final List<Function<Case, Optional<Check>>> CHECKS = List.of(
            held -> Optional.of(allowedCheck(held)),
            Rules::smoothnessCheck,
            Rules::sharpnessCheck,
            Rules::depthCheck,
            Rules::residualCheck,
            Rules::lengthCheck,
            Rules::crackTipsCheck,
            Rules::areaCheck,
            Rules::exfoliationCheck,
            Rules::strikeCheck,
            Rules::edgeMarginCheck,
            Rules::blendShareCheck);

    private Rules() {}

    /** A rule the damage got past. */
    static Reason pass(String rule, String detail) {
        return new Reason(rule, detail, true);
    }

    /** A rule the damage did not get past. */
    static Reason fail(String rule, String detail) {
        return new Reason(rule, detail, false);
    }

    private static String inches(Length length) {
        return length.inches().toPlainString();
    }

    private static Optional<Check> answer(boolean passed, String rule, String detail, Disposition onFailure) {
        return Optional.of(new Check(passed ? pass(rule, detail) : fail(rule, detail), onFailure));
    }

    /**
     * A rule that is a yes or no about the damage rather than a number against a limit, reading one
     * detail when the fault is there and another when it is not.
     */
    private static Optional<Check> flag(
            String rule, boolean fault, String clear, String faulted, Disposition onFailure) {
        return answer(!fault, rule, fault ? faulted : clear, onFailure);
    }

    /** Whether the page allows this damage at any size at all. */
    public static Check allowedCheck(Case held) {
        String what = held.finding().kind() + " " + held.placement().region() + " on "
                + held.placement().panelKind();
        return flag(
                        "allowable",
                        !held.limits().allowed(),
                        "the book allows a " + what,
                        "no allowable limit for a " + what,
                        Disposition.REPAIRABLE)
                .orElseThrow();
    }

    /**
     * Depth against the cap, with rework on the table only where grinding is allowed.
     *
     * <p>A score lying along the body on a page that states a hoop loaded share is held to that share of
     * the cap, and the trail says so, because the same depth running round the barrel would have passed.
     */
    public static Optional<Check> depthCheck(Case held) {
        Optional<Length> depth = held.depth();
        boolean hoopLoaded = isHoopLoaded(held);
        Optional<Length> cap = held.limits().depthCap(held.thickness(), hoopLoaded);
        if (depth.isEmpty() || cap.isEmpty()) {
            return Optional.empty();
        }
        String along = hoopLoaded && held.limits().hoopLoadedShare().isPresent() ? " along the body" : "";
        String detail = inches(depth.get()) + " deep against a cap of " + inches(cap.get()) + along + " at "
                + inches(held.thickness());
        if (depth.get().isAtMost(cap.get())) {
            return answer(true, "depth", detail, Disposition.REPAIRABLE);
        }
        Disposition landing = held.canBeBlendedHere() ? Disposition.REWORKABLE : Disposition.REPAIRABLE;
        return answer(false, "depth", detail, landing);
    }

    /** A score lying along the body, which hoop stress pulls open. */
    static boolean isHoopLoaded(Case held) {
        return held.finding().geometry() instanceof Scoring scoring && scoring.isHoopLoaded();
    }

    /**
     * What a cleanup would leave behind, against the floor the page sets.
     *
     * <p>This is the rework limit and not the as is limit, so it is deliberately looser than the depth
     * cap and it is measured on the cut rather than on the damage. A blend goes past the bottom of a
     * scratch to get the worked metal out, and that extra is what decides whether grinding is worth
     * starting.
     */
    public static Optional<Check> residualCheck(Case held) {
        Optional<Length> depth = held.cutDepth();
        Optional<Length> floor = held.limits().residualFloor(held.thickness());
        if (depth.isEmpty() || floor.isEmpty()) {
            return Optional.empty();
        }
        Length left = held.thickness().minus(depth.get());
        String detail = inches(left) + " left after a cleanup against a floor of " + inches(floor.get());
        return answer(left.isAtLeast(floor.get()), "residual", detail, Disposition.REPAIRABLE);
    }

    /** A dent is graded on how sharp it is as well as how deep. */
    public static Optional<Check> sharpnessCheck(Case held) {
        Optional<Ratio> cap = held.limits().maxSharpness();
        if (!(held.finding().geometry() instanceof Dent dent) || cap.isEmpty()) {
            return Optional.empty();
        }
        String detail = dent.sharpness() + " against a cap of " + cap.get();
        return answer(dent.sharpness().isAtMost(cap.get()), "sharpness", detail, Disposition.REPAIRABLE);
    }

    /** A dent with a sharp bottom or a crack in it is never left alone. */
    public static Optional<Check> smoothnessCheck(Case held) {
        if (!(held.finding().geometry() instanceof Dent dent)) {
            return Optional.empty();
        }
        String fault = dent.cracked() ? "cracked" : "sharp bottomed";
        return flag(
                "smoothness",
                !dent.isSmooth(),
                "smooth bottom and no crack",
                "the dent is " + fault,
                Disposition.REPAIRABLE);
    }

    /** Length against the cap, for the kinds that lie along a line. */
    public static Optional<Check> lengthCheck(Case held) {
        Optional<Length> cap = held.limits().maxLength();
        Optional<Length> length = lineLength(held.finding().geometry());
        if (cap.isEmpty() || length.isEmpty()) {
            return Optional.empty();
        }
        String detail = inches(length.get()) + " long against a cap of " + inches(cap.get());
        return answer(length.get().isAtMost(cap.get()), "length", detail, Disposition.REPAIRABLE);
    }

    private static Optional<Length> lineLength(Geometry geometry) {
        return switch (geometry) {
            case Scoring scoring -> Optional.of(scoring.length());
            case Crack crack -> Optional.of(crack.length());
            default -> Optional.empty();
        };
    }

    /**
     * Whether both ends of a crack were found, which is what a repair is drawn to.
     *
     * <p>A crack with a tip nobody found has a length that is only a lower bound. It cannot be stop
     * drilled at the end that was not found and no doubler can be sized to cover a crack of unknown
     * length, so it goes to the manufacturer rather than to the repair section.
     */
    public static Optional<Check> crackTipsCheck(Case held) {
        if (!(held.finding().geometry() instanceof Crack crack)) {
            return Optional.empty();
        }
        return flag(
                "crack tips",
                !crack.isBounded(),
                "both tips found",
                "only " + crack.tipsFound() + " of 2 tips found, so " + inches(crack.length()) + " is a lower bound",
                Disposition.OUT_OF_LIMITS);
    }

    /** Area against the cap, for the kinds measured as a patch. */
    public static Optional<Check> areaCheck(Case held) {
        Optional<BigDecimal> cap = held.limits().maxArea();
        if (cap.isEmpty()) {
            return Optional.empty();
        }
        Optional<BigDecimal> area = patchArea(held.finding().geometry());
        if (area.isEmpty()) {
            return Optional.empty();
        }
        String detail = Decimals.text(area.get()) + " sq in against a cap of " + Decimals.text(cap.get());
        return answer(area.get().compareTo(cap.get()) <= 0, "area", detail, Disposition.REPAIRABLE);
    }

    private static Optional<BigDecimal> patchArea(Geometry geometry) {
        return switch (geometry) {
            case Corrosion corrosion -> Optional.of(corrosion.area());
            case Disbond disbond -> Optional.of(disbond.area());
            case LightningBurn burn -> Optional.of(burn.area());
            default -> Optional.empty();
        };
    }

    /**
     * Distance to the nearest edge, which no amount of grinding improves.
     *
     * <p>An edge margin written in diameters is a distance to fasteners, so it says nothing at all in
     * the middle of a bay where there are none. The rule stands aside there rather than inventing a
     * fastener size to read it with.
     */
    public static Optional<Check> edgeMarginCheck(Case held) {
        Optional<Length> floor;
        boolean inDiameters = held.limits().minEdgeMarginDiameters().isPresent();
        if (inDiameters && held.placement().nearestFastener().isEmpty()) {
            floor = held.limits().minEdgeMargin();
        } else {
            floor = held.limits().edgeMarginFloor(held.placement().nearestFastener());
        }
        if (floor.isEmpty()) {
            return Optional.empty();
        }
        Length margin = held.placement().edgeMargin();
        String detail = inches(margin) + " to the nearest edge against a floor of " + inches(floor.get());
        return answer(margin.isAtLeast(floor.get()), "edge margin", detail, Disposition.REPAIRABLE);
    }

    /** Corrosion running along the grain, which blending does not reach. */
    public static Optional<Check> exfoliationCheck(Case held) {
        if (!(held.finding().geometry() instanceof Corrosion corrosion)) {
            return Optional.empty();
        }
        return flag(
                "exfoliation",
                corrosion.exfoliating(),
                "no exfoliation reported",
                "exfoliation is not reached by a blend",
                Disposition.REPAIRABLE);
    }

    /**
     * Where a lightning strike attached, which decides more than the size of the burn does.
     *
     * <p>A strike that attached on a fastener drove current through the joint, and the fastener comes
     * out so the hole can be looked at for arcing whatever the surface reads, which is a repair job. A
     * swept stroke dragged across the skin leaves pits nobody counted one by one, so the whole zone is
     * blended and looked at again rather than each pit being taken on its own.
     */
    public static Optional<Check> strikeCheck(Case held) {
        if (!(held.finding().geometry() instanceof LightningBurn burn)) {
            return Optional.empty();
        }
        if (burn.needsTheFastenerOut()) {
            return answer(
                    false,
                    "attachment",
                    "attached on a fastener, which comes out so the hole can be checked for arcing",
                    Disposition.REPAIRABLE);
        }
        return flag(
                "attachment",
                burn.isSwept(),
                burn.pitCount() + " pits on the open skin",
                "a swept stroke of " + burn.pitCount() + " pits, blended and looked at as one zone",
                Disposition.REWORKABLE);
    }

    /** How much of the panel the cleanup would take, if a cleanup is on the table. */
    public static Optional<Check> blendShareCheck(Case held) {
        Optional<Length> depth = held.depth();
        if (depth.isEmpty() || !held.canBeBlendedHere()) {
            return Optional.empty();
        }
        Length covered = held.finding().footprint(held.placement().centre()).longerSide();
        Length extent = held.cleanup().blendExtent(covered, depth.get());
        Length panel = held.placement().panelExtent();
        BigDecimal share = Decimals.over(BigDecimal.valueOf(extent.mils()), BigDecimal.valueOf(panel.mils()));
        String detail = "a blend covering " + inches(extent) + " of a " + inches(panel) + " panel";
        if (held.cleanup().withinPanelShare(extent, panel)) {
            return answer(true, "blend share", detail, Disposition.REPAIRABLE);
        }
        String said = detail + " is " + Decimals.quantize(share, 2).toPlainString() + " of it";
        return answer(false, "blend share", said, Disposition.REPAIRABLE);
    }
}
