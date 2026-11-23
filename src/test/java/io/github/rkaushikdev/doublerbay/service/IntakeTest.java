package io.github.rkaushikdev.doublerbay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.grid.Point;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionEntry;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionLevel;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntakeTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final LocalDate LATER = LocalDate.of(2026, 3, 14);
    private static final Serial MSN = new Serial("5127");

    private Database database;

    @BeforeEach
    void stocked() {
        database = Desk.stocked();
    }

    private static Corrosion corrosion(String along, String around, String depth, boolean exfoliating) {
        return new Corrosion(Sample.inch(along), Sample.inch(around), Sample.inch(depth), false, exfoliating);
    }

    private static Corrosion corrosion(String depth) {
        return corrosion("2.000", "1.000", depth, false);
    }

    private static Corrosion corrosion() {
        return corrosion("0.002");
    }

    private static Finding rustCard(int sequence, LocalDate found, Geometry geometry, Point at) {
        return Sample.cardWith()
                .sequence(sequence)
                .kind(DamageKind.CORROSION)
                .geometry(geometry != null ? geometry : corrosion())
                .at(at)
                .found(found)
                .origin(DamageOrigin.ENVIRONMENT)
                .build();
    }

    private static Finding rustCard(int sequence, LocalDate found, Geometry geometry) {
        return rustCard(sequence, found, geometry, null);
    }

    private static Finding rustCard() {
        return rustCard(1, DAY, null);
    }

    private Intake taken(Finding finding, boolean urgent) {
        return UnitOfWork.apply(
                database, unit -> Intake.takeIn(unit, Book.v320(), new TypeCatalogue(), finding, urgent));
    }

    private Intake taken(Finding finding) {
        return taken(finding, false);
    }

    @Test
    void aDentWritesTheCardAndItsDisposition() {
        Intake answer = taken(Sample.card(1));
        assertThat(answer.reference()).isEqualTo(new FindingId(2025, 1));
        assertThat(answer.wentOnTheProgramme()).isFalse();
        UnitOfWork.run(database, unit -> {
            assertThat(unit.findings().latest(new FindingId(2025, 1)).kind()).isSameAs(DamageKind.DENT);
            assertThat(unit.dispositions().get(new FindingId(2025, 1)).isOpen()).isTrue();
        });
    }

    @Test
    void aDentReadsBackAsJustRaised() {
        assertThat(taken(Sample.card(1))).hasToString("raised DR-2025-000001");
    }

    @Test
    void aDentNeverGoesOnTheCorrosionRegister() {
        taken(Sample.card(1));
        List<CorrosionEntry> graded =
                UnitOfWork.apply(database, unit -> unit.corrosion().onTail(MSN));
        assertThat(graded).isEmpty();
    }

    @Test
    void aDentCannotBeCalledAnAirworthinessConcern() {
        assertThatThrownBy(() -> taken(Sample.card(1), true))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("only a corrosion finding");
    }

    @Test
    void aCorrosionCardGoesOnTheProgramme() {
        Intake answer = taken(rustCard());
        assertThat(answer.wentOnTheProgramme()).isTrue();
        List<CorrosionEntry> graded =
                UnitOfWork.apply(database, unit -> unit.corrosion().onTail(MSN));
        assertThat(graded).hasSize(1);
    }

    @Test
    void aCorrosionCardReadsBackWithItsLevel() {
        assertThat(taken(rustCard()).toString()).contains("level");
    }

    @Test
    void theFirstCorrosionInAPlaceIsLevelOne() {
        assertThat(taken(rustCard()).graded().orElseThrow().level()).isSameAs(CorrosionLevel.ONE);
    }

    @Test
    void corrosionBackInTheSamePlaceIsLevelTwo() {
        Corrosion deep = corrosion("0.030");
        taken(rustCard(1, DAY, deep));
        Intake second = taken(rustCard(2, LATER, deep));
        assertThat(second.graded().orElseThrow().level()).isSameAs(CorrosionLevel.TWO);
    }

    @Test
    void corrosionInADifferentPlaceStaysLevelOne() {
        Corrosion deep = corrosion("0.030");
        taken(rustCard(1, DAY, deep));
        Intake elsewhere = taken(rustCard(2, LATER, deep, Sample.cabinPoint(40, "10.000", 6, "4.000")));
        assertThat(elsewhere.graded().orElseThrow().level()).isSameAs(CorrosionLevel.ONE);
    }

    @Test
    void anUrgentFindingIsLevelThree() {
        Intake answer = taken(rustCard(), true);
        assertThat(answer.graded().orElseThrow().level()).isSameAs(CorrosionLevel.THREE);
        assertThat(answer.mustBeReported()).isTrue();
    }

    @Test
    void aReportableFindingCarriesTheDayItIsDueOut() {
        Intake answer = taken(rustCard(), true);
        assertThat(answer.reportBy()).contains(LocalDate.of(2025, 3, 17));
    }

    @Test
    void aFindingThatIsNotReportableHasNoReportDate() {
        assertThat(taken(rustCard()).reportBy()).isEmpty();
    }

    @Test
    void aDentHasNoReportDate() {
        assertThat(taken(Sample.card(1)).reportBy()).isEmpty();
    }

    @Test
    void anUrgentFindingLandsOnTheUnreportedList() {
        taken(rustCard(), true);
        List<CorrosionEntry> unreported =
                UnitOfWork.apply(database, unit -> unit.corrosion().unreported());
        assertThat(unreported).hasSize(1);
    }

    @Test
    void theProgrammeEntryIsFiledAgainstTheCard() {
        taken(rustCard(), true);
        UnitOfWork.run(
                database, unit -> unit.corrosion().markReported(new FindingId(2025, 1), LocalDate.of(2025, 3, 15)));
        List<CorrosionEntry> unreported =
                UnitOfWork.apply(database, unit -> unit.corrosion().unreported());
        assertThat(unreported).isEmpty();
    }

    @Test
    void shallowCorrosionInsideTheLimitsReadsAsWithinLimits() {
        Finding finding = rustCard();
        UnitOfWork.run(database, unit -> {
            TailContext tail = Intake.tailContext(unit, new TypeCatalogue(), finding);
            assertThat(Intake.withinLimits(Book.v320(), tail, finding)).isTrue();
        });
    }

    @Test
    void deepCorrosionReadsAsOutsideTheLimits() {
        Finding finding = rustCard(1, DAY, corrosion("0.060"));
        UnitOfWork.run(database, unit -> {
            TailContext tail = Intake.tailContext(unit, new TypeCatalogue(), finding);
            assertThat(Intake.withinLimits(Book.v320(), tail, finding)).isFalse();
        });
    }

    @Test
    void theNextReferenceFollowsTheYearTheCardWasFoundIn() {
        UnitOfWork.run(database, unit -> {
            assertThat(Intake.nextReference(unit, DAY).year()).isEqualTo(2025);
            assertThat(Intake.nextReference(unit, LocalDate.of(2026, 1, 1)).year())
                    .isEqualTo(2026);
        });
    }

    @Test
    void theNextReferenceMovesOnAfterACardIsRaised() {
        taken(Sample.card(1));
        FindingId next = UnitOfWork.apply(database, unit -> Intake.nextReference(unit, DAY));
        assertThat(next).isEqualTo(new FindingId(2025, 2));
    }

    @Test
    void anIntakeWithNoGradingSaysItWentNowhere() {
        assertThat(new Intake(new FindingId(2025, 1)).wentOnTheProgramme()).isFalse();
    }

    @Test
    void deepCorrosionAcrossAStringerLineIsLevelTwoTheFirstTimeItIsFound() {
        Corrosion deep = corrosion("0.030");
        Intake across = taken(rustCard(1, DAY, deep, Sample.cabinPointAtFrame(40)));
        assertThat(across.graded().orElseThrow().level()).isSameAs(CorrosionLevel.TWO);
    }

    @Test
    void theSameCorrosionInsideOneBayIsLevelOneTheFirstTime() {
        Corrosion deep = corrosion("0.030");
        Intake inside = taken(rustCard(1, DAY, deep, Sample.cabinPoint(40, "10.000", 6, "4.000")));
        assertThat(inside.graded().orElseThrow().level()).isSameAs(CorrosionLevel.ONE);
    }
}
