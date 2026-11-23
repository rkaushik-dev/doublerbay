package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.manual.LimitSet;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * What the shop is being asked to grind, and what has to be left.
 *
 * <p>A rework disposition is not an answer on its own. What goes on the work card is how deep to cut,
 * how far to feather it out, what has to be left when you are done, and what to do about the finish.
 * The last one is the part that gets missed. Clad sheet has a layer of nearly pure aluminium on it
 * that is there to corrode instead of the alloy, and a blend that goes through it has taken that
 * protection off. The metal is fine and the corrosion protection is gone, and nothing about the depth
 * reading says so.
 *
 * <p>So the plan says whether the cut breaks the cladding, and if it does it carries the finish that
 * has to go back on. That is a line on the work card and it is the difference between a repair that
 * lasts and a corrosion finding in four years at the same spot. {@link Blends#planBlend} works one
 * out.
 */
public record BlendPlan(
        Length cutDepth,
        Length along,
        Length around,
        Taper taper,
        Length residual,
        boolean breachesClad,
        boolean finishRequired,
        Optional<Length> endRadius) {

    /** A plan with every measurement on it, whose ends fit inside it. */
    public BlendPlan {
        Objects.requireNonNull(cutDepth, "cutDepth");
        Objects.requireNonNull(along, "along");
        Objects.requireNonNull(around, "around");
        Objects.requireNonNull(taper, "taper");
        Objects.requireNonNull(residual, "residual");
        Objects.requireNonNull(endRadius, "endRadius");
        if (endRadius.isPresent()
                && Length.min(along, around).isLessThan(endRadius.get().times(2))) {
            throw new BadReading("a blend is at least two end radii across");
        }
    }

    /** The skin the finished blend covers, in square inches to two places. */
    public BigDecimal area() {
        return Corrosion.squareInches(along, around);
    }

    /** The longer of the two sides of the finished blend. */
    public Length longerSide() {
        return Length.max(along, around);
    }

    /** Whether the metal left clears the floor the page sets at this thickness, if it sets one. */
    public boolean leavesEnough(LimitSet limits, Length thickness) {
        Optional<Length> floor = limits.residualFloor(thickness);
        return floor.isEmpty() || residual.isAtLeast(floor.get());
    }

    /** Whether the finished blend stays inside the area cap the page sets, if it sets one. */
    public boolean withinArea(LimitSet limits) {
        return limits.maxArea().isEmpty() || area().compareTo(limits.maxArea().get()) <= 0;
    }

    /**
     * {@code cut 0.005 at 20:1, 2.200 by 0.500, 0.250 ends, 0.035 left, refinish}, the ends only where
     * the book states a radius and the refinish only when it is needed.
     */
    @Override
    public String toString() {
        String size = along.inches().toPlainString() + " by " + around.inches().toPlainString();
        String ends = endRadius
                .map(radius -> ", " + radius.inches().toPlainString() + " ends")
                .orElse("");
        String head = "cut " + cutDepth.inches().toPlainString() + " at " + taper + ", " + size + ends + ", "
                + residual.inches().toPlainString() + " left";
        return finishRequired ? head + ", refinish" : head;
    }
}
