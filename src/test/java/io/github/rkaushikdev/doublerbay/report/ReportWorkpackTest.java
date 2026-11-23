package io.github.rkaushikdev.doublerbay.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.service.Orders;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Databases;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionSchedule;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionTask;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportWorkpackTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final LocalDate OPENS = LocalDate.of(2025, 4, 1);
    private static final LocalDate CLOSES = LocalDate.of(2025, 4, 8);
    private static final Serial MSN = new Serial("5127");
    private static final String QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do";

    private Database database;

    private static UtilisationLog logWithTwoReadings() {
        return UtilisationLog.opened(MSN, LocalDate.of(2016, 4, 12))
                .posted(new Snapshot(LocalDate.of(2024, 3, 14), Sample.flown(12000, 21000)))
                .posted(new Snapshot(LocalDate.of(2025, 3, 1), Sample.flown(13800, 24200)));
    }

    @BeforeEach
    void oneTailWithARate() {
        database = Databases.fresh();
        UnitOfWork.run(database, unit -> {
            unit.aircraft().add(Sample.aircraft());
            unit.aircraft().openLog(logWithTwoReadings());
        });
    }

    private FindingId assessed(int sequence) {
        FindingId reference = new FindingId(2025, sequence);
        UnitOfWork.run(database, unit -> {
            unit.findings().raiseFinding(Sample.card(sequence, Sample.cabinPointOn(10)));
            unit.dispositions()
                    .save(DispositionRecord.raised(reference).movedTo(DispositionState.ASSESSED, DAY, "a.okonkwo"));
        });
        return reference;
    }

    private FindingId assessed() {
        return assessed(1);
    }

    private void defer(FindingId reference, String limit, LocalDate on, int cycles, long hours) {
        UnitOfWork.run(database, unit -> {
            unit.deferrals()
                    .grant(Deferral.grant(
                            reference,
                            on,
                            Sample.flown(cycles, hours),
                            "j.mercer",
                            InspectionMethod.DVI,
                            Interval.parse(limit),
                            null,
                            ""));
            DispositionRecord held = unit.dispositions().get(reference);
            unit.dispositions().save(held.movedTo(DispositionState.DEFERRED, on, "j.mercer"));
        });
    }

    private void defer(FindingId reference, String limit) {
        defer(reference, limit, DAY, 13800, 24200);
    }

    /** Granted at a hundred cycles, so a one cycle limit is long gone. */
    private void deferredLongAgo(FindingId reference) {
        defer(reference, "1 FC", DAY, 100, 200);
    }

    private void order(FindingId reference) {
        UnitOfWork.run(database, unit -> Orders.raiseOrder(unit, reference, QUESTION, DAY, "a.okonkwo"));
    }

    private WorkPack pack(LocalDate opens, LocalDate closes) {
        return UnitOfWork.apply(database, unit -> WorkPacks.build(unit, MSN, opens, closes));
    }

    private WorkPack pack() {
        return pack(OPENS, CLOSES);
    }

    private WorkPack pack(int horizon) {
        return UnitOfWork.apply(database, unit -> WorkPacks.build(unit, MSN, OPENS, CLOSES, horizon));
    }

    @Test
    void aTailWithNothingOwedHasAnEmptyPack() {
        WorkPack built = pack();
        assertThat(built.isEmpty()).isTrue();
        assertThat(built).hasToString("MSN 5127: nothing for this check");
    }

    @Test
    void aCheckThatClosesBeforeItOpensIsRefused() {
        assertThatThrownBy(() -> pack(CLOSES, OPENS))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("closes after it opens");
    }

    @Test
    void thePackCountsItsOwnDays() {
        assertThat(pack().days()).isEqualTo(8);
    }

    @Test
    void aOneDayCheckIsOneDay() {
        assertThat(pack(OPENS, OPENS).days()).isEqualTo(1);
    }

    @Test
    void aDeferralFallingDueInsideTheCheckIsOnThePack() {
        defer(assessed(), "1 FC");
        WorkPack built = pack();
        assertThat(built.deferrals()).hasSize(1);
        assertThat(built.deferrals().get(0).what()).isEqualTo("deferral");
    }

    @Test
    void aDeferralFarOutIsNotOnThePack() {
        defer(assessed(), "5000 FC");
        assertThat(pack().deferrals()).isEmpty();
    }

    @Test
    void aHorizonCanWidenThePackPastTheCheck() {
        defer(assessed(), "500 FC");
        assertThat(pack().deferrals()).isEmpty();
        assertThat(pack(400).deferrals()).hasSize(1);
    }

    @Test
    void aDeferralAlreadyOverIsMarkedOver() {
        deferredLongAgo(assessed());
        WorkPack built = pack();
        assertThat(built.deferrals().get(0).overrun()).isTrue();
        assertThat(built.overrun()).hasSize(1);
    }

    @Test
    void anOrderOutLandsUnderWaiting() {
        order(assessed());
        WorkPack built = pack();
        assertThat(built.waiting()).hasSize(1);
        assertThat(built.waiting().get(0).what()).isEqualTo("waiting");
        assertThat(built.waiting().get(0).detail()).contains("no answer yet");
    }

    @Test
    void anOrderIsNotSomethingTheCheckCanClear() {
        order(assessed());
        WorkPack built = pack();
        assertThat(built.dueLines()).isEmpty();
        assertThat(built.total()).isEqualTo(1);
    }

    @Test
    void anotherTailsOrderIsNotOnThisPack() {
        UnitOfWork.run(database, unit -> {
            unit.aircraft().add(Sample.aircraft("5240"));
            unit.aircraft().openLog(Sample.flyingLog("5240"));
            unit.findings().raiseFinding(Sample.card(2, "5240"));
            unit.dispositions()
                    .save(DispositionRecord.raised(new FindingId(2025, 2))
                            .movedTo(DispositionState.ASSESSED, DAY, "a.okonkwo"));
        });
        order(new FindingId(2025, 2));
        assertThat(pack().waiting()).isEmpty();
    }

    @Test
    void thePackReadsBackWithACountAndASpan() {
        defer(assessed(), "1 FC");
        assertThat(pack().toString()).contains("1 items over 8 days");
    }

    @Test
    void aPackWithAnOverrunSaysSo() {
        deferredLongAgo(assessed());
        assertThat(pack().toString()).contains("already over");
    }

    @Test
    void everyReferenceOnThePackCanBeListed() {
        defer(assessed(1), "1 FC");
        order(assessed(2));
        Set<String> named = new HashSet<>(WorkPacks.referencesOn(pack()));
        assertThat(named).containsExactlyInAnyOrder("DR-2025-000001", "DR-2025-000002");
    }

    @Test
    void aPackWithNoInspectionsTouchesNoRepairs() {
        defer(assessed(), "1 FC");
        assertThat(WorkPacks.repairsTouched(pack())).isEmpty();
    }

    @Test
    void aPackLineReadsBackAsOneLine() {
        PackLine line = new PackLine("deferral", "DR-2025-000001", "detailed visual, 3 days on calendar");
        assertThat(line).hasToString("deferral DR-2025-000001: detailed visual, 3 days on calendar");
    }

    @Test
    void anOverrunLineIsMarkedAtTheFront() {
        PackLine line = new PackLine("deferral", "DR-2025-000001", "over", true);
        assertThat(line.toString()).startsWith("OVER ");
    }

    @Test
    void aRepairIdOnAnInspectionLineParsesBack() {
        assertThat(RepairId.parse("RP-53-0001")).isEqualTo(new RepairId(53, 1));
    }

    private static InspectionTask task(int sequence, int panel, InspectionMethod method) {
        return new InspectionTask(
                new RepairId(53, sequence),
                new PanelId(53, 10, panel, "L"),
                InspectionSchedule.of(method, Interval.parse("8000 FC"), Interval.parse("4000 FC")),
                DAY,
                Sample.flown(13800, 24200));
    }

    @Test
    void theRepairsOnOnePanelThatNeedThePaintOffAreOneStrippingJob() {
        List<PackLine> strip = WorkPacks.strippingLines(List.of(
                task(2, 152, InspectionMethod.HFEC),
                task(1, 143, InspectionMethod.LFEC),
                task(3, 143, InspectionMethod.HFEC),
                task(4, 143, InspectionMethod.DVI)));
        assertThat(strip).hasSize(2);
        assertThat(strip.get(0).what()).isEqualTo("strip");
        assertThat(strip.get(0).reference()).isEqualTo("5310-143L");
        assertThat(strip.get(0).detail())
                .isEqualTo("paint off for low frequency eddy current on RP-53-0001,"
                        + " high frequency eddy current on RP-53-0003");
        assertThat(strip.get(1).reference()).isEqualTo("5310-152L");
        assertThat(strip.get(0).overrun()).isFalse();
    }

    @Test
    void inspectionsThatLookThroughThePaintNeedNoStripping() {
        assertThat(WorkPacks.strippingLines(
                        List.of(task(1, 143, InspectionMethod.DVI), task(2, 143, InspectionMethod.UT))))
                .isEmpty();
        assertThat(pack().stripping()).isEmpty();
    }

    @Test
    void strippingIsPreparationAndIsNotCountedAsWorkOwed() {
        PackLine strip = new PackLine("strip", "5310-143L", "paint off for high frequency eddy current on RP-53-0001");
        WorkPack built = new WorkPack(MSN, OPENS, CLOSES, List.of(), List.of(), List.of(), List.of(strip));
        assertThat(built.stripping()).containsExactly(strip);
        assertThat(built.total()).isZero();
        assertThat(built.isEmpty()).isTrue();
    }
}
