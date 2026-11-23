package io.github.rkaushikdev.doublerbay.tolerance;

import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.util.Literal;

/**
 * Corrosion levels, which are a programme classification and not a measurement.
 *
 * <p>The corrosion prevention and control programme grades every finding into one of three levels,
 * and the grading is not about how bad it looks. Level one is corrosion the programme is coping with.
 * Level two is corrosion the programme is not coping with, either because it is past the allowable
 * limits or because it has come back in the same place between two inspections. Level three is
 * corrosion the operator judges to be an airworthiness concern now.
 *
 * <p>The distinction that matters is between one and two, and the trap is that the same finding is
 * level one the first time and level two the second, with nothing about the metal having changed. So
 * the history of that location is an input to the classification, and a desk that grades each finding
 * on its own can never produce a level two.
 *
 * <p>Corrosion that is widespread, running across more than one structural element, and outside
 * the limits is level two the first time it is found. One blend on one element is the programme
 * having missed a spot. The same loss across a frame line is the programme not reaching that area at
 * all, and waiting for it to come back before calling it that is waiting a whole interval for nothing.
 *
 * <p>Level three is reportable. The authority hears about it and so does the type certificate holder,
 * and there is a clock on it.
 *
 * @see CorrosionEntry#grade
 */
public enum CorrosionLevel {
    ONE(1),
    TWO(2),
    THREE(3);

    private final int value;

    CorrosionLevel(int value) {
        this.value = value;
    }

    /** The level's number. */
    public int value() {
        return value;
    }

    /**
     * The level with this number.
     *
     * @throws IllegalArgumentException when there is no such level
     */
    public static CorrosionLevel fromValue(int value) {
        for (CorrosionLevel level : values()) {
            if (level.value == value) {
                return level;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid CorrosionLevel");
    }

    /** Grade one corrosion finding against the programme rules. */
    public static CorrosionLevel levelFor(Corrosion finding, CorrosionContext context) {
        if (context.urgentAirworthinessConcern()) {
            return THREE;
        }
        boolean coping = context.withinLimits() && !finding.exfoliating();
        boolean notReached = context.foundHereBefore() || context.widespread();
        return coping || !notReached ? ONE : TWO;
    }

    /** Level three goes to the authority and to the manufacturer. */
    public boolean isReportable() {
        return this == THREE;
    }

    /** Anything above level one says the programme is not coping. */
    public boolean needsProgrammeReview() {
        return this != ONE;
    }

    /** The same as needing a programme review: worse than the programme expects. */
    public boolean isWorseThanExpected() {
        return needsProgrammeReview();
    }

    /** Whether this level is above another. */
    public boolean worseThan(CorrosionLevel other) {
        return value > other.value;
    }

    /** {@code level 2}. */
    @Override
    public String toString() {
        return "level " + value;
    }
}
