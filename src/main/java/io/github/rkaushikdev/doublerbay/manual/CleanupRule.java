package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import io.github.rkaushikdev.doublerbay.structure.Region;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * How a damage of one kind may be ground out, if it may be at all.
 *
 * <p>Two rules are equal when every field is the same amount, however many places the share of the
 * panel was written to.
 */
public record CleanupRule(
        Taper taper,
        boolean blendable,
        Optional<Length> minEndRadius,
        Optional<BigDecimal> maxBlendFraction,
        boolean finishRequiredThroughClad,
        Length minCutBelow) {

    /** A rule whose radius, share and cut all make sense for whether it can be blended. */
    public CleanupRule {
        Objects.requireNonNull(taper, "taper");
        Objects.requireNonNull(minEndRadius, "minEndRadius");
        Objects.requireNonNull(maxBlendFraction, "maxBlendFraction");
        Objects.requireNonNull(minCutBelow, "minCutBelow");
        if (!blendable && minEndRadius.isPresent()) {
            throw new BadReading("a kind that cannot be blended carries no blend radius");
        }
        if (maxBlendFraction.isPresent()
                && !(maxBlendFraction.get().signum() > 0
                        && maxBlendFraction.get().compareTo(BigDecimal.ONE) <= 0)) {
            throw new BadReading("a blend fraction is a part of the panel");
        }
        if (minCutBelow.mils() < 0) {
            throw new BadReading("a blend does not cut upwards");
        }
    }

    /** A blendable rule with no radius or share stated, finished through the clad, cut the usual depth below. */
    public CleanupRule(Taper taper) {
        this(taper, true, Optional.empty(), Optional.empty(), true, Cleanups.DEFAULT_CUT_BELOW);
    }

    /**
     * A rule from what was to hand, finished through the clad and cut the usual depth below; a null
     * radius or share is one the book does not state.
     */
    public static CleanupRule of(Taper taper, boolean blendable, Length minEndRadius, BigDecimal maxBlendFraction) {
        return new CleanupRule(
                taper,
                blendable,
                Optional.ofNullable(minEndRadius),
                Optional.ofNullable(maxBlendFraction),
                true,
                Cleanups.DEFAULT_CUT_BELOW);
    }

    /** A kind that may be blended, to a radius at its ends and within a share of the panel. */
    public static CleanupRule blendable(Taper taper, Length minEndRadius, BigDecimal maxBlendFraction) {
        return of(
                taper,
                true,
                Objects.requireNonNull(minEndRadius, "minEndRadius"),
                Objects.requireNonNull(maxBlendFraction, "maxBlendFraction"));
    }

    /** A kind the book will not let anybody grind out. */
    public static CleanupRule notBlendable(Taper taper) {
        return of(taper, false, null, null);
    }

    /** The same rule, saying whether a blend through the clad needs a protective finish. */
    public CleanupRule withFinishRequiredThroughClad(boolean required) {
        return new CleanupRule(taper, blendable, minEndRadius, maxBlendFraction, required, minCutBelow);
    }

    /** The same rule, cutting this far below the bottom of the damage. */
    public CleanupRule withMinCutBelow(Length cut) {
        return new CleanupRule(taper, blendable, minEndRadius, maxBlendFraction, finishRequiredThroughClad, cut);
    }

    /** How deep the blend actually goes, which is past the bottom of the damage. */
    public Length cutDepth(Length damageDepth) {
        if (damageDepth.mils() < 0) {
            throw new BadReading("a blend depth does not run backwards");
        }
        if (!blendable) {
            return damageDepth;
        }
        return damageDepth.plus(minCutBelow);
    }

    /** How far out from the deepest point the blend has to feather. */
    public Length runout(Length depth) {
        if (depth.mils() < 0) {
            throw new BadReading("a blend depth does not run backwards");
        }
        return taper.runout(depth);
    }

    /**
     * How much skin the finished blend covers along one axis: the damage feathered out both ways, and
     * never narrower than the cutter its ends are formed with.
     *
     * <p>The end radius is what stops a blend leaving a notch of its own. A shallow scratch feathered
     * at twenty to one is narrower than a quarter inch cutter, and a cutter cannot cut narrower than
     * itself, so the finished blend is at least two end radii across whatever the feathering says.
     */
    public Length blendExtent(Length damageExtent, Length depth) {
        Length feathered = damageExtent.plus(runout(depth).times(2));
        return minEndRadius
                .map(radius -> Length.max(feathered, radius.times(2)))
                .orElse(feathered);
    }

    /** Whether a blend may happen at all where this damage is sitting. */
    public boolean allowedIn(Region region) {
        return blendable && !Cleanups.NO_BLEND_REGIONS.contains(region);
    }

    /** Whether the blend stays within the share of the panel the book allows one to take. */
    public boolean withinPanelShare(Length blendExtent, Length panelExtent) {
        if (maxBlendFraction.isEmpty()) {
            return true;
        }
        if (panelExtent.mils() <= 0) {
            throw new BadReading("a panel extent is a length");
        }
        BigDecimal share =
                Decimals.over(BigDecimal.valueOf(blendExtent.mils()), BigDecimal.valueOf(panelExtent.mils()));
        return share.compareTo(maxBlendFraction.get()) <= 0;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CleanupRule that)) {
            return false;
        }
        boolean sameShare = maxBlendFraction.isEmpty() || that.maxBlendFraction.isEmpty()
                ? maxBlendFraction.isEmpty() && that.maxBlendFraction.isEmpty()
                : maxBlendFraction.get().compareTo(that.maxBlendFraction.get()) == 0;
        return taper.equals(that.taper)
                && blendable == that.blendable
                && minEndRadius.equals(that.minEndRadius)
                && sameShare
                && finishRequiredThroughClad == that.finishRequiredThroughClad
                && minCutBelow.equals(that.minCutBelow);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                taper,
                blendable,
                minEndRadius,
                maxBlendFraction.map(BigDecimal::stripTrailingZeros),
                finishRequiredThroughClad,
                minCutBelow);
    }

    /** {@code blend at 20:1 with 0.250 ends, up to 25.00% of the panel}, or {@code not blendable}. */
    @Override
    public String toString() {
        if (!blendable) {
            return "not blendable";
        }
        String head = "blend at " + taper
                + minEndRadius
                        .map(radius -> " with " + radius.inches().toPlainString() + " ends")
                        .orElse("");
        if (maxBlendFraction.isPresent()) {
            head = head + ", up to " + Decimals.text(Decimals.times(maxBlendFraction.get(), 100)) + "% of the panel";
        }
        return head;
    }
}
