package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.disposition.EngineeringOrder;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionTask;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Everything one aeroplane owes the desk at one check, on one page.
 *
 * <p>Planning does not want three lists. It wants to know what has to happen while the aeroplane is
 * in the hangar, and that comes off three different registers with three different clocks: deferrals
 * counting down against flying, repeat inspections counting down against the repair they hang on,
 * and engineering orders that have no clock at all and are waiting on somebody else.
 *
 * <p>The orders are the awkward one. Nothing about an order falls due, so a check cannot clear it,
 * but leaving it off the pack means the aeroplane comes out of the hangar with a card the desk still
 * cannot close. So it goes on the pack under its own heading, marked as waiting rather than due,
 * which is what it is.
 *
 * <p>The pack is built for a window rather than a day. A check is a week, and something falling due
 * on the Thursday is something to do while the panel is already off, not something to defer to the
 * next one.
 */
public final class WorkPacks {

    private WorkPacks() {}

    /** A deferral on the due list, as a pack line. */
    public static PackLine deferralLine(DueItem item) {
        return new PackLine(
                "deferral", item.against().toString(), item.what() + ", " + item.margin(), item.isOverrun());
    }

    /** A repeat inspection that has come due, as a pack line. */
    public static PackLine inspectionLine(InspectionTask task, boolean over) {
        return new PackLine(
                "inspection",
                task.against().toString(),
                task.schedule().method() + " on " + task.part() + ", due " + task.due(),
                over);
    }

    /**
     * The panels the paint has to come off before the inspections on them can be done, one line a
     * panel in part number order.
     *
     * <p>Eddy current reads the metal, and paint between the probe and the skin is lift off the
     * instrument reads as a defect or as nothing. The hangar strips a panel once, so two repairs on
     * one panel that both need it are one stripping job and one line, which is what lets the paint
     * shop be booked before the check opens rather than when the inspector reaches the panel.
     */
    public static List<PackLine> strippingLines(List<InspectionTask> tasks) {
        Map<PanelId, List<InspectionTask>> byPanel = new TreeMap<>(Comparator.comparing(PanelId::toString));
        for (InspectionTask task : tasks) {
            if (task.schedule().method().needsPaintOff()) {
                byPanel.computeIfAbsent(task.part(), ignored -> new ArrayList<>())
                        .add(task);
            }
        }
        List<PackLine> lines = new ArrayList<>();
        byPanel.forEach((part, onIt) -> lines.add(new PackLine(
                "strip",
                part.toString(),
                "paint off for "
                        + onIt.stream()
                                .map(one -> one.schedule().method() + " on " + one.against())
                                .collect(Collectors.joining(", ")))));
        return List.copyOf(lines);
    }

    /** An order out to the manufacturer, as a pack line that is never over. */
    public static PackLine waitingLine(FindingId reference, String order, int days) {
        return new PackLine("waiting", reference.toString(), order + " out " + days + " days, no answer yet");
    }

    /** Everything due on one tail between two days, plus what is waiting. */
    public static WorkPack build(UnitOfWork unit, Serial serial, LocalDate opens, LocalDate closes) {
        return build(unit, serial, opens, closes, OptionalInt.empty());
    }

    /** The same, with the deferrals reaching a number of days past the day the check opens. */
    public static WorkPack build(UnitOfWork unit, Serial serial, LocalDate opens, LocalDate closes, int horizonDays) {
        return build(unit, serial, opens, closes, OptionalInt.of(horizonDays));
    }

    /**
     * Everything due on one tail between two days, plus what is waiting.
     *
     * <p>The deferrals are the ones due within the horizon of the day the check opens; with no horizon
     * given it is the length of the check itself.
     */
    public static WorkPack build(
            UnitOfWork unit, Serial serial, LocalDate opens, LocalDate closes, OptionalInt horizonDays) {
        if (closes.isBefore(opens)) {
            throw new BadReading("a check closes after it opens");
        }
        int span = Math.toIntExact(ChronoUnit.DAYS.between(opens, closes));
        int reach = horizonDays.orElse(span);
        DueReport due = DeskReports.dueOnTail(unit, serial, opens, reach);

        Usage flown = unit.aircraft().logFor(serial).at(closes);
        List<InspectionTask> tasks = unit.inspections().dueOn(serial, flown, closes);
        Set<RepairId> already = unit.inspections().dueOn(serial, flown, opens).stream()
                .map(InspectionTask::against)
                .collect(Collectors.toSet());

        return new WorkPack(
                serial,
                opens,
                closes,
                due.items().stream().map(WorkPacks::deferralLine).toList(),
                tasks.stream()
                        .map(one -> inspectionLine(one, already.contains(one.against())))
                        .toList(),
                waiting(unit, serial, closes),
                strippingLines(tasks));
    }

    /** The orders out against this tail, which no check can clear. */
    private static List<PackLine> waiting(UnitOfWork unit, Serial serial, LocalDate on) {
        List<PackLine> lines = new ArrayList<>();
        for (EngineeringOrder order : unit.orders().openOrders()) {
            if (!unit.findings().latest(order.against()).serial().equals(serial)) {
                continue;
            }
            lines.add(waitingLine(order.against(), order.reference().toString(), order.daysWaiting(on)));
        }
        return lines;
    }

    /** Every reference the pack names, for a planner cross checking a printout. */
    public static List<String> referencesOn(WorkPack pack) {
        List<String> references = new ArrayList<>();
        pack.dueLines().forEach(one -> references.add(one.reference()));
        pack.waiting().forEach(one -> references.add(one.reference()));
        return List.copyOf(references);
    }

    /** The repairs whose inspections fall in this pack. */
    public static List<RepairId> repairsTouched(WorkPack pack) {
        return pack.inspections().stream()
                .map(one -> RepairId.parse(one.reference()))
                .toList();
    }
}
