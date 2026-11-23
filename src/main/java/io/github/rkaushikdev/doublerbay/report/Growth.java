package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.tolerance.GrowthBasis;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How fast a damage is actually growing, measured rather than predicted: one card's measured growth
 * across every revision on file.
 *
 * <p>The damage tolerance analysis says how fast a crack in this material at this stress ought to
 * grow. This says how fast this one did. The two are different numbers and the second one is the one
 * that decides whether the aeroplane keeps flying, because it is the only one that knows about the
 * particular corrosion pit the crack started from.
 *
 * <p>Rate is per thousand flight cycles rather than per day. Growth is driven by pressurisation, and
 * an aeroplane doing four short sectors a day and one doing a single long one are not accumulating
 * damage at the same rate per day even though the calendar says they are.
 *
 * <p>Two readings is the minimum and it is a weak answer. Three is a trend. The report says how many
 * it is working from rather than presenting a two point line as though it were a curve.
 */
public record Growth(FindingId reference, int readings, List<Step> steps) {

    /** What a rate is quoted against, because growth follows pressurisation. */
    public static final int PER_CYCLES = 1000;

    /** Below this many cycles between readings the rate is noise, not a measurement. */
    public static final int SHORTEST_SPAN = 50;

    /** A card, how many readings it has, and the steps between consecutive ones. */
    public Growth {
        Objects.requireNonNull(reference, "reference");
        steps = List.copyOf(steps);
    }

    /**
     * The number that matters for growth, which depends on the kind.
     *
     * <p>A crack grows along its length. Anything else is reported by whichever of its measurements
     * the limit table is written against, and for now that is only the crack, because nothing else on
     * this fleet is tracked for growth.
     *
     * @throws BadReading for any card that is not a crack
     */
    public static Length measuredLength(Finding finding) {
        if (!(finding.geometry() instanceof Crack crack)) {
            throw new BadReading(finding.reference() + " is a " + finding.kind() + ", which is not tracked for growth");
        }
        return crack.length();
    }

    /**
     * Work out one card's growth from its revisions, put in revision order first.
     *
     * @throws BadReading when there are no revisions, or two or more and one is not a crack
     */
    public static Growth of(List<Finding> revisions) {
        if (revisions.isEmpty()) {
            throw new BadReading("a card with no revisions has no growth");
        }
        List<Finding> ordered = revisions.stream()
                .sorted(Comparator.comparingInt(Finding::revision))
                .toList();
        List<Step> steps = new ArrayList<>();
        for (int index = 1; index < ordered.size(); index++) {
            Finding before = ordered.get(index - 1);
            Finding after = ordered.get(index);
            Length now = measuredLength(after);
            Length was = drilledBetween(before, after) ? now : measuredLength(before);
            int cycles =
                    after.atUsage().cycles().count() - before.atUsage().cycles().count();
            steps.add(new Step(was, now, cycles));
        }
        return new Growth(ordered.get(0).reference(), ordered.size(), steps);
    }

    /**
     * Whether the tips were stop drilled between two readings. The later one is a diameter longer
     * because of the drill and not because the crack grew, so that stretch counts no growth.
     */
    private static boolean drilledBetween(Finding before, Finding after) {
        return after.geometry() instanceof Crack later
                && later.stopDrilled()
                && before.geometry() instanceof Crack earlier
                && !earlier.stopDrilled();
    }

    /** Three readings is a trend. Two is a line through two points. */
    public boolean isATrend() {
        return readings >= 3;
    }

    /** Whether any step got longer. */
    public boolean hasGrown() {
        return steps.stream().anyMatch(one -> one.grewBy().mils() > 0);
    }

    /** From the first reading to the last, nothing when there is only one. */
    public Length total() {
        if (steps.isEmpty()) {
            return new Length(0);
        }
        return new Length(
                steps.get(steps.size() - 1).now().mils() - steps.get(0).was().mils());
    }

    /** The steps long enough to take a rate from. */
    public List<Step> measurable() {
        return steps.stream().filter(Step::isMeasurable).toList();
    }

    /**
     * Whether the fastest measured stretch is quicker than the analysis says a crack grows in this
     * kind of structure, which is the point at which the repeat interval was set on the wrong number.
     * A crack with no measurable stretch has not shown anything yet.
     */
    public boolean outgrows(GrowthBasis basis) {
        return worstRate()
                .filter(rate -> rate.compareTo(basis.inchesPerThousand()) > 0)
                .isPresent();
    }

    /** The fastest measured stretch, which is the one to plan against, or nothing with no measurable step. */
    public Optional<BigDecimal> worstRate() {
        return worstStep().map(Step::rate);
    }

    /** The worst rate as the desk prints it, sign kept on a rate too small to show: see {@link Step#rateText}. */
    public Optional<String> worstRateText() {
        return worstStep().map(Step::rateText);
    }

    /** The measurable step with the fastest rate, the first of those that are equal at four places. */
    private Optional<Step> worstStep() {
        Step worst = null;
        for (Step one : measurable()) {
            if (worst == null || one.rate().compareTo(worst.rate()) > 0) {
                worst = one;
            }
        }
        return Optional.ofNullable(worst);
    }

    /**
     * {@code DR-2025-000001: one reading, no growth measured}, or the total over the readings with the
     * worst rate: {@code DR-2025-000001: 0.100 over 2 readings, worst 0.1000 in per 1000 FC}.
     */
    @Override
    public String toString() {
        if (steps.isEmpty()) {
            return reference + ": one reading, no growth measured";
        }
        String pace = worstRateText()
                .map(text -> ", worst " + text + " in per 1000 FC")
                .orElse(", no measurable rate");
        return reference + ": " + total().inches().toPlainString() + " over " + readings + " readings" + pace;
    }
}
