package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.ids.Serial;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One tail's work for one check: what the check can clear, and what is waiting on somebody else.
 *
 * @param serial the tail
 * @param opens the first day of the check
 * @param closes the last day of the check
 * @param deferrals the deferrals falling due
 * @param inspections the repeat inspections falling due
 * @param waiting the orders out to the manufacturer, which no check can clear
 * @param stripping the panels the paint comes off before the inspections on them can be done, one
 *     line a panel
 */
public record WorkPack(
        Serial serial,
        LocalDate opens,
        LocalDate closes,
        List<PackLine> deferrals,
        List<PackLine> inspections,
        List<PackLine> waiting,
        List<PackLine> stripping) {

    public WorkPack {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(opens, "opens");
        Objects.requireNonNull(closes, "closes");
        deferrals = List.copyOf(deferrals);
        inspections = List.copyOf(inspections);
        waiting = List.copyOf(waiting);
        stripping = List.copyOf(stripping);
    }

    /** A pack with no paint to come off. */
    public WorkPack(
            Serial serial,
            LocalDate opens,
            LocalDate closes,
            List<PackLine> deferrals,
            List<PackLine> inspections,
            List<PackLine> waiting) {
        this(serial, opens, closes, deferrals, inspections, waiting, List.of());
    }

    /**
     * Every block of the pack under the word its lines carry, in the order a planner reads them: the
     * deferrals, the inspections, what is waiting on engineering, then the paint that comes off first.
     */
    public List<Map.Entry<String, List<PackLine>>> blocks() {
        return List.of(
                Map.entry("deferral", deferrals),
                Map.entry("inspection", inspections),
                Map.entry("waiting", waiting),
                Map.entry("strip", stripping));
    }

    /** The ones the check can actually clear: the deferrals, then the inspections. */
    public List<PackLine> dueLines() {
        List<PackLine> lines = new ArrayList<>(deferrals);
        lines.addAll(inspections);
        return List.copyOf(lines);
    }

    /**
     * Every line on the pack. The stripping is not counted: it is the preparation for inspections
     * already on the pack, not more work owed.
     */
    public int total() {
        return dueLines().size() + waiting.size();
    }

    /** Whether the check owes nothing. */
    public boolean isEmpty() {
        return total() == 0;
    }

    /** The lines the check can clear that were already over. */
    public List<PackLine> overrun() {
        return dueLines().stream().filter(PackLine::overrun).toList();
    }

    /** How many days the check runs, both ends counted. */
    public int days() {
        return Math.toIntExact(ChronoUnit.DAYS.between(opens, closes) + 1);
    }

    /** {@code MSN 5127: 2 items over 8 days, 1 already over}, or that there is nothing for this check. */
    @Override
    public String toString() {
        if (isEmpty()) {
            return serial + ": nothing for this check";
        }
        String over = overrun().isEmpty() ? "" : ", " + overrun().size() + " already over";
        return serial + ": " + total() + " items over " + days() + " days" + over;
    }
}
