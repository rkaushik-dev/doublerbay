package io.github.rkaushikdev.doublerbay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.assess.Disposition;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.repair.StopDrills;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceMeasuringTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final LocalDate AGAIN = LocalDate.of(2025, 9, 1);
    private static final FindingId AGAINST = new FindingId(2025, 1);

    private Database database;

    @BeforeEach
    void stocked() {
        database = Desk.stocked();
    }

    private static Dent dent(String depth) {
        return new Dent(Sample.inch(depth), Sample.inch("3.000"), Sample.inch("2.000"));
    }

    private FindingId raised(int sequence) {
        FindingId reference = new FindingId(2025, sequence);
        UnitOfWork.run(database, unit -> {
            unit.findings()
                    .raiseFinding(Sample.cardWith()
                            .sequence(sequence)
                            .geometry(dent("0.020"))
                            .at(Sample.cabinPointOn(10, "4.000"))
                            .build());
            unit.dispositions()
                    .save(DispositionRecord.raised(reference).movedTo(DispositionState.ASSESSED, DAY, "a.okonkwo"));
        });
        return reference;
    }

    private FindingId raised() {
        return raised(1);
    }

    private void defer(FindingId reference) {
        UnitOfWork.run(database, unit -> {
            unit.deferrals()
                    .grant(Deferral.grant(
                            reference,
                            DAY,
                            Sample.flown(13800, 24200),
                            "j.mercer",
                            InspectionMethod.DVI,
                            Interval.parse("500 FC"),
                            null,
                            ""));
            DispositionRecord held = unit.dispositions().get(reference);
            unit.dispositions().save(held.movedTo(DispositionState.DEFERRED, DAY, "j.mercer"));
        });
    }

    private ReMeasured measured(Geometry geometry, FindingId reference, LocalDate on) {
        return UnitOfWork.apply(
                database, unit -> Measuring.reMeasure(unit, Book.v320(), new TypeCatalogue(), reference, geometry, on));
    }

    private ReMeasured measured(Geometry geometry) {
        return measured(geometry, AGAINST, AGAIN);
    }

    private List<Finding> history() {
        return UnitOfWork.apply(database, unit -> Measuring.historyOf(unit, AGAINST));
    }

    private Finding latest() {
        return UnitOfWork.apply(database, unit -> unit.findings().latest(AGAINST));
    }

    private boolean grew() {
        return UnitOfWork.apply(database, unit -> Measuring.grewBetween(unit, AGAINST));
    }

    @Test
    void aReMeasurementAddsARevision() {
        raised();
        ReMeasured answer = measured(dent("0.025"));
        assertThat(answer.revision()).isEqualTo(2);
        assertThat(history()).hasSize(2);
    }

    @Test
    void theFirstReadingIsKept() {
        raised();
        measured(dent("0.025"));
        Finding first = history().get(0);
        assertThat(((Dent) first.geometry()).depth()).isEqualTo(Sample.inch("0.020"));
    }

    @Test
    void theLatestReadingIsTheNewOne() {
        raised();
        measured(dent("0.025"));
        assertThat(((Dent) latest().geometry()).depth()).isEqualTo(Sample.inch("0.025"));
    }

    @Test
    void theDayItWasLookedAtIsRecorded() {
        raised();
        measured(dent("0.025"));
        assertThat(latest().foundOn()).isEqualTo(AGAIN);
    }

    @Test
    void aDamageThatHasNotMovedDoesNotGetWorse() {
        raised();
        ReMeasured answer = measured(dent("0.020"));
        assertThat(answer.gotWorse()).isFalse();
        assertThat(answer.was()).isSameAs(answer.now());
    }

    @Test
    void aDamageThatHasGrownPastTheLimitGetsWorse() {
        raised();
        ReMeasured answer = measured(dent("0.400"));
        assertThat(answer.gotWorse()).isTrue();
        assertThat(answer.now().rank()).isGreaterThan(answer.was().rank());
    }

    @Test
    void aCardMeasuredBeforeItWasFoundIsRefused() {
        raised();
        assertThatThrownBy(() -> measured(dent("0.025"), AGAINST, LocalDate.of(2024, 1, 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("before it was found");
    }

    @Test
    void aCardThatIsNotOnFileCannotBeMeasured() {
        assertThatThrownBy(() -> measured(dent("0.025"), new FindingId(2025, 9), AGAIN))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void aSupersededCardPointsAtTheOneThatCarriesIt() {
        raised(1);
        raised(2);
        UnitOfWork.run(database, unit -> unit.findings().supersede(AGAINST, new FindingId(2025, 2)));
        assertThatThrownBy(() -> measured(dent("0.025")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("measure DR-2025-000002 instead");
    }

    @Test
    void aCardWithNoDeferralIsNotStillDeferred() {
        raised();
        assertThat(measured(dent("0.025")).stillDeferred()).isFalse();
    }

    @Test
    void aDeferredCardSaysItIsStillDeferred() {
        FindingId reference = raised();
        defer(reference);
        assertThat(measured(dent("0.025")).stillDeferred()).isTrue();
    }

    @Test
    void aDeferredCardThatGrewHasOutgrownItsDeferral() {
        FindingId reference = raised();
        defer(reference);
        ReMeasured answer = measured(dent("0.400"));
        assertThat(answer.outgrewItsDeferral()).isTrue();
    }

    @Test
    void aDeferredCardThatDidNotGrowHasNotOutgrownIt() {
        FindingId reference = raised();
        defer(reference);
        assertThat(measured(dent("0.020")).outgrewItsDeferral()).isFalse();
    }

    @Test
    void aCardThatGrewButWasNeverDeferredHasOutgrownNothing() {
        raised();
        assertThat(measured(dent("0.400")).outgrewItsDeferral()).isFalse();
    }

    @Test
    void aReMeasurementReadsBackWithItsRevision() {
        raised();
        ReMeasured answer = measured(dent("0.020"));
        assertThat(answer.toString()).startsWith("DR-2025-000001 revision 2, ");
    }

    @Test
    void aWorseAnswerNamesBothDispositions() {
        raised();
        ReMeasured answer = measured(dent("0.400"));
        assertThat(answer.toString()).contains(" to ");
    }

    @Test
    void aCardNeverMeasuredAgainHasNotGrown() {
        raised();
        assertThat(grew()).isFalse();
    }

    @Test
    void aCardMeasuredTheSameHasNotGrown() {
        raised();
        measured(dent("0.020"));
        assertThat(grew()).isFalse();
    }

    @Test
    void aCardMeasuredDifferentlyHasGrown() {
        raised();
        measured(dent("0.025"));
        assertThat(grew()).isTrue();
    }

    @Test
    void theCountersComeOffTheLogWhenNoneAreGiven() {
        raised();
        measured(dent("0.025"));
        assertThat(latest().atUsage().cycles().count()).isPositive();
    }

    @Test
    void theCountersCanBeHandedIn() {
        raised();
        UnitOfWork.run(
                database,
                unit -> Measuring.reMeasure(
                        unit,
                        Book.v320(),
                        new TypeCatalogue(),
                        AGAINST,
                        dent("0.025"),
                        AGAIN,
                        Sample.flown(14500, 25500)));
        assertThat(latest().atUsage().cycles().count()).isEqualTo(14500);
    }

    @Test
    void aReMeasuredRecordCarriesItsTwoAnswers() {
        ReMeasured made = new ReMeasured(Sample.card(1), Disposition.ALLOWABLE, Disposition.REPAIRABLE, false);
        assertThat(made.gotWorse()).isTrue();
        assertThat(made.outgrewItsDeferral()).isFalse();
    }

    private FindingId raisedCrack() {
        FindingId reference = new FindingId(2025, 4);
        UnitOfWork.run(database, unit -> unit.findings()
                .raiseFinding(Sample.cardWith()
                        .sequence(4)
                        .kind(DamageKind.CRACK)
                        .geometry(new Crack(Sample.inch("0.500"), Angle.ofDegrees(90)))
                        .at(Sample.cabinPointOn(10, "4.000"))
                        .origin(DamageOrigin.FATIGUE)
                        .build()));
        return reference;
    }

    private ReMeasured drilled(FindingId reference) {
        return UnitOfWork.apply(
                database, unit -> Measuring.stopDrill(unit, Book.v320(), new TypeCatalogue(), reference, AGAIN, null));
    }

    @Test
    void stopDrillingACrackRecordsTheDrilledCrackAsTheNextRevision() {
        FindingId reference = raisedCrack();
        ReMeasured answer = drilled(reference);
        assertThat(answer.revision()).isEqualTo(2);
        Finding latest = UnitOfWork.apply(database, unit -> unit.findings().latest(reference));
        assertThat(latest.geometry()).isInstanceOfSatisfying(Crack.class, crack -> {
            assertThat(crack.isArrested()).isTrue();
            assertThat(crack.length()).isEqualTo(Sample.inch("0.500").plus(StopDrills.drillFor(Sample.inch("0.040"))));
        });
        assertThat(latest.note()).startsWith("stop drilled 0.098");
        assertThat(latest.foundOn()).isEqualTo(AGAIN);
    }

    @Test
    void aCrackIsDrilledOnceAndADentNotAtAll() {
        FindingId reference = raisedCrack();
        drilled(reference);
        assertThatThrownBy(() -> drilled(reference))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("already drilled");
        raised();
        assertThatThrownBy(() -> drilled(AGAINST)).isInstanceOf(Refused.class).hasMessageContaining("not a crack");
    }
}
