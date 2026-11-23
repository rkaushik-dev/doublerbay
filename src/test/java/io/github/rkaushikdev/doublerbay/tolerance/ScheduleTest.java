package io.github.rkaushikdev.doublerbay.tolerance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ScheduleTest {

    private static final Usage FLOWN = UtilisationLog.usage(13800, 24200);
    private static final LocalDate EMBODIED = LocalDate.of(2025, 4, 2);

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    /** The schedule a repair of a category carries, for a category that carries one. */
    private static InspectionSchedule made(RepairCategory category, PanelKind kind, InspectionMethod method) {
        return Schedules.scheduleFor(category, kind, method).orElseThrow();
    }

    private static int repeatCycles(InspectionSchedule schedule) {
        return schedule.repeat().orElseThrow().cycles().orElseThrow().count();
    }

    private static int thresholdCycles(InspectionSchedule schedule) {
        return schedule.threshold().cycles().orElseThrow().count();
    }

    @Test
    void aGrowthBasisReadsBackItsTwoNumbers() {
        GrowthBasis basis = Schedules.basisFor(PanelKind.PRESSURISED_SKIN);
        assertThat(basis).hasToString("critical 6.000, 0.100 per 1000 FC");
    }

    @Test
    void aBasisNeedsACriticalLengthAndAGrowthRate() {
        assertThatThrownBy(() -> new GrowthBasis(inch("0"), inch("0.100")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a critical crack length is a length");
        assertThatThrownBy(() -> new GrowthBasis(inch("6.000"), inch("0")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not grow needs no inspection");
    }

    @Test
    void theGrowthLifeIsTheRunDividedByTheRate() {
        GrowthBasis basis = new GrowthBasis(inch("6.000"), inch("0.100"));
        assertThat(basis.growthCycles(inch("1.000"))).isEqualTo(new Cycles(50000));
    }

    @Test
    void aCrackAlreadyAtCriticalHasNoGrowthLifeLeft() {
        GrowthBasis basis = new GrowthBasis(inch("6.000"), inch("0.100"));
        assertThatThrownBy(() -> basis.growthCycles(inch("6.000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("already at or past the critical");
    }

    @Test
    void theLowerWingGrowsCracksFasterThanTheBarrel() {
        GrowthBasis wing = Schedules.basisFor(PanelKind.WING_LOWER);
        GrowthBasis barrel = Schedules.basisFor(PanelKind.PRESSURISED_SKIN);
        assertThat(wing.growthPerThousand().isGreaterThan(barrel.growthPerThousand()))
                .isTrue();
        assertThat(wing.critical().isLessThan(barrel.critical())).isTrue();
    }

    @Test
    void aPanelKindWithNoGrowthDataIsALookupMiss() {
        assertThatThrownBy(() -> Schedules.basisFor(PanelKind.FAIRING))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("no crack growth data on file");
    }

    @Test
    void aPermanentRepairCarriesNoScheduleAtAll() {
        assertThat(Schedules.scheduleFor(RepairCategory.A, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI))
                .isEmpty();
    }

    @Test
    void anInspectedRepairGetsAThresholdAndARepeat() {
        InspectionSchedule schedule = made(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI);
        assertThat(schedule.isRepeating()).isTrue();
        assertThat(thresholdCycles(schedule)).isGreaterThan(repeatCycles(schedule));
    }

    @Test
    void theRepeatIsHalfTheGrowthLifeFromWhatTheMethodFinds() {
        InspectionSchedule schedule = made(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI);
        Cycles growth = Schedules.basisFor(PanelKind.PRESSURISED_SKIN).growthCycles(InspectionMethod.DVI.findsDownTo());
        assertThat(repeatCycles(schedule)).isEqualTo(Math.floorDiv(growth.count(), 2));
    }

    @Test
    void aMoreSensitiveMethodBuysALongerInterval() {
        InspectionSchedule visual = made(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI);
        InspectionSchedule eddy = made(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.HFEC);
        assertThat(repeatCycles(eddy)).isGreaterThan(repeatCycles(visual));
    }

    @Test
    void theFastGrowingSurfaceGetsTheShorterInterval() {
        InspectionSchedule wing = made(RepairCategory.B, PanelKind.WING_LOWER, InspectionMethod.HFEC);
        InspectionSchedule barrel = made(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.HFEC);
        assertThat(repeatCycles(wing)).isLessThan(repeatCycles(barrel));
    }

    @Test
    void aTemporaryRepairGetsAReplacementLimitAndNoRepeat() {
        InspectionSchedule schedule = made(RepairCategory.C, PanelKind.PRESSURISED_SKIN, InspectionMethod.GVI);
        assertThat(schedule.isRepeating()).isFalse();
        assertThat(schedule.threshold().months()).contains(12);
    }

    @Test
    void aMethodThatCannotFindItBeforeItIsCriticalIsRefused() {
        GrowthBasis tight = new GrowthBasis(inch("1.000"), inch("0.100"));
        assertThatThrownBy(() -> tight.growthCycles(InspectionMethod.GVI.findsDownTo()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("already at or past the critical");
    }

    @Test
    void aWalkRoundOnTheFastSurfaceBuysFarLessInterval() {
        InspectionSchedule visual = made(RepairCategory.B, PanelKind.WING_LOWER, InspectionMethod.GVI);
        InspectionSchedule eddy = made(RepairCategory.B, PanelKind.WING_LOWER, InspectionMethod.HFEC);
        assertThat(repeatCycles(visual)).isLessThan(repeatCycles(eddy));
        assertThat(repeatCycles(eddy) - repeatCycles(visual)).isGreaterThan(6000);
    }

    @Test
    void theFirstLookIsTheThresholdFromWhenItWentOn() {
        InspectionSchedule schedule = made(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI);
        DueAt due = schedule.firstDue(FLOWN, EMBODIED);
        assertThat(due.cycles().orElseThrow().count()).isEqualTo(13800 + thresholdCycles(schedule));
    }

    @Test
    void eachLookAfterThatIsTheRepeatFromTheLastOne() {
        InspectionSchedule schedule = made(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI);
        DueAt due = schedule.nextDue(FLOWN, LocalDate.of(2026, 1, 1));
        assertThat(due.cycles().orElseThrow().count()).isEqualTo(13800 + repeatCycles(schedule));
    }

    @Test
    void aReplacementLimitHasNoNextLook() {
        InspectionSchedule schedule = made(RepairCategory.C, PanelKind.PRESSURISED_SKIN, InspectionMethod.GVI);
        assertThatThrownBy(() -> schedule.nextDue(FLOWN, LocalDate.of(2026, 1, 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("it is a replacement limit");
    }

    @Test
    void aScheduleReadsBackAsAProgrammeLine() {
        InspectionSchedule repeating = made(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI);
        assertThat(repeating.toString()).startsWith("detailed visual at ");
        assertThat(repeating.toString()).contains("then every");
    }

    @Test
    void aReplacementLimitReadsBackAsOne() {
        InspectionSchedule once = InspectionSchedule.of(InspectionMethod.GVI, Interval.ofMonths(12), null);
        assertThat(once).hasToString("general visual once by 12 MO");
    }

    @Test
    void aTemporaryRepairComesOffOnTheCyclesOrTheMonthsWhicheverIsFirst() {
        assertThat(Schedules.temporaryLimitFor(PanelKind.PRESSURISED_SKIN)).hasToString("3000 FC / 12 MO");
        assertThat(Schedules.temporaryLimitFor(PanelKind.WING_LOWER)).hasToString("1500 FC / 12 MO");
        assertThat(Schedules.temporaryLimitFor(PanelKind.UNPRESSURISED_SKIN)).hasToString("6000 FC / 12 MO");
        assertThat(Schedules.temporaryLimitFor(PanelKind.FAIRING)).hasToString("12 MO");
    }

    @Test
    void aTemporaryRepairOnTheLowerWingIsReplacedOnItsCycles() {
        InspectionSchedule schedule = made(RepairCategory.C, PanelKind.WING_LOWER, InspectionMethod.DVI);
        assertThat(schedule.isRepeating()).isFalse();
        assertThat(schedule).hasToString("detailed visual once by 1500 FC / 12 MO");
        DueAt due = schedule.firstDue(FLOWN, EMBODIED);
        assertThat(due.cycles().orElseThrow().count()).isEqualTo(13800 + 1500);
        assertThat(due.isDue(UtilisationLog.usage(15300, 25000), EMBODIED.plusMonths(2)))
                .isTrue();
        assertThat(due.isDue(UtilisationLog.usage(15299, 25000), EMBODIED.plusMonths(2)))
                .isFalse();
    }

    @Test
    void aPermanentRepairStillCarriesNothing() {
        assertThat(Schedules.scheduleFor(RepairCategory.A, PanelKind.WING_LOWER, InspectionMethod.HFEC))
                .isEmpty();
    }

    @Test
    void theRepeatAMethodBuysComesOffTheBasisItIsRunAgainst() {
        GrowthBasis pressurised = Schedules.basisFor(PanelKind.PRESSURISED_SKIN);
        assertThat(Schedules.repeatFor(pressurised, InspectionMethod.HFEC)).contains(new Cycles(29750));
        assertThat(Schedules.repeatFor(pressurised.atMeasuredRate(new BigDecimal("0.5")), InspectionMethod.HFEC))
                .contains(new Cycles(5950));
        GrowthBasis tight = new GrowthBasis(inch("0.051"), inch("1.000"));
        assertThat(Schedules.repeatFor(tight, InspectionMethod.HFEC)).isEmpty();
    }

    @Test
    void aMeasuredRateIsNeverReadAsSlowerThanItWas() {
        GrowthBasis measured = Schedules.basisFor(PanelKind.WING_LOWER).atMeasuredRate(new BigDecimal("0.0754"));
        assertThat(measured.growthPerThousand()).isEqualTo(inch("0.076"));
        assertThat(measured.critical())
                .isEqualTo(Schedules.basisFor(PanelKind.WING_LOWER).critical());
        assertThatThrownBy(() -> Schedules.basisFor(PanelKind.WING_LOWER).atMeasuredRate(BigDecimal.ZERO))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not growth");
    }
}
