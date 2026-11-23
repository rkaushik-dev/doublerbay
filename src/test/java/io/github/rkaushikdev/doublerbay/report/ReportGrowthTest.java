package io.github.rkaushikdev.doublerbay.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.GrowthBasis;
import io.github.rkaushikdev.doublerbay.tolerance.Schedules;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportGrowthTest {

    private static Crack crack(String length) {
        return new Crack(Sample.inch(length), new Angle(0));
    }

    private static Finding card(String length, int cycles, int revision) {
        Finding made = Sample.card(1, DamageKind.CRACK, crack(length), DamageOrigin.FATIGUE);
        return made.withRevision(revision).withAtUsage(Sample.flown(cycles, cycles * 2L));
    }

    private static Finding card(String length) {
        return card(length, 13800, 1);
    }

    private static Finding card() {
        return card("0.400");
    }

    private static Step step(int cycles) {
        return new Step(Sample.inch("0.400"), Sample.inch("0.500"), cycles);
    }

    @Test
    void aCrackIsMeasuredOnItsLength() {
        assertThat(Growth.measuredLength(card("0.400"))).isEqualTo(Sample.inch("0.400"));
    }

    @Test
    void aDentIsNotTrackedForGrowth() {
        assertThatThrownBy(() -> Growth.measuredLength(Sample.card(1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not tracked for growth");
    }

    @Test
    void aCardWithNoRevisionsHasNoGrowth() {
        assertThatThrownBy(() -> Growth.of(List.of()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no revisions");
    }

    @Test
    void oneReadingGivesNoSteps() {
        Growth built = Growth.of(List.of(card()));
        assertThat(built.steps()).isEmpty();
        assertThat(built.readings()).isEqualTo(1);
        assertThat(built.hasGrown()).isFalse();
    }

    @Test
    void oneReadingReadsBackAsNoGrowthMeasured() {
        assertThat(Growth.of(List.of(card()))).hasToString("DR-2025-000001: one reading, no growth measured");
    }

    @Test
    void twoReadingsGiveOneStep() {
        Growth built = Growth.of(List.of(card("0.400", 13800, 1), card("0.500", 14800, 2)));
        assertThat(built.steps()).hasSize(1);
        assertThat(built.hasGrown()).isTrue();
    }

    @Test
    void theTotalIsTheFirstToTheLast() {
        Growth built = Growth.of(List.of(card("0.400", 13800, 1), card("0.500", 14800, 2)));
        assertThat(built.total()).isEqualTo(Sample.inch("0.100"));
    }

    @Test
    void aCrackThatHasNotMovedHasNotGrown() {
        Growth built = Growth.of(List.of(card("0.400", 13800, 1), card("0.400", 14800, 2)));
        assertThat(built.hasGrown()).isFalse();
        assertThat(built.total()).isEqualTo(new Length(0));
    }

    @Test
    void theRevisionsAreOrderedBeforeTheyAreRead() {
        Growth built = Growth.of(List.of(card("0.500", 14800, 2), card("0.400", 13800, 1)));
        assertThat(built.total()).isEqualTo(Sample.inch("0.100"));
    }

    @Test
    void twoReadingsAreNotATrend() {
        assertThat(Growth.of(List.of(card("0.400", 13800, 1), card("0.500", 14800, 2)))
                        .isATrend())
                .isFalse();
    }

    @Test
    void threeReadingsAreATrend() {
        Growth built = Growth.of(List.of(card("0.400", 13800, 1), card("0.450", 14300, 2), card("0.500", 14800, 3)));
        assertThat(built.isATrend()).isTrue();
        assertThat(built.steps()).hasSize(2);
    }

    @Test
    void aStepReadsBackWithBothReadings() {
        assertThat(step(1000)).hasToString("0.400 to 0.500 over 1000 FC");
    }

    @Test
    void aStepOverAThousandCyclesGivesARate() {
        assertThat(step(1000).rate()).isEqualByComparingTo("0.1000");
    }

    @Test
    void aStepOverTwoThousandCyclesHalvesTheRate() {
        assertThat(step(2000).rate()).isEqualByComparingTo("0.0500");
    }

    @Test
    void theRateIsQuotedPerThousandCycles() {
        assertThat(Growth.PER_CYCLES).isEqualTo(1000);
    }

    @Test
    void aStepShorterThanTheFloorHasNoRate() {
        Step shorter = step(Growth.SHORTEST_SPAN - 1);
        assertThat(shorter.isMeasurable()).isFalse();
        assertThatThrownBy(shorter::rate).isInstanceOf(BadReading.class).hasMessageContaining("too short a span");
    }

    @Test
    void aStepAtTheFloorIsMeasurable() {
        assertThat(step(Growth.SHORTEST_SPAN).isMeasurable()).isTrue();
    }

    @Test
    void aStepThatRunsBackwardsInCyclesIsRefused() {
        assertThatThrownBy(() -> step(-1))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not run backwards");
    }

    @Test
    void aCrackThatShrankReadsAsNegativeGrowth() {
        Step shrank = new Step(Sample.inch("0.500"), Sample.inch("0.400"), 1000);
        assertThat(shrank.grewBy().mils()).isNegative();
        assertThat(shrank.rate()).isNegative();
        assertThat(shrank.rateText()).isEqualTo("-0.1000");
        // A mil shorter over thirty thousand cycles rounds to nothing at four places and still reads as shrinking.
        Step barely = new Step(Sample.inch("0.401"), Sample.inch("0.400"), 30000);
        assertThat(barely.rate()).isZero();
        assertThat(barely.rateText()).isEqualTo("-0.0000");
    }

    @Test
    void theWorstRateIsTheFastestStretch() {
        Growth built = Growth.of(List.of(card("0.400", 13800, 1), card("0.450", 14300, 2), card("0.600", 14800, 3)));
        assertThat(built.worstRate())
                .hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("0.3000"));
    }

    @Test
    void aCardWithNoMeasurableStepHasNoWorstRate() {
        Growth built = Growth.of(List.of(card("0.400", 13800, 1), card("0.500", 13820, 2)));
        assertThat(built.measurable()).isEmpty();
        assertThat(built.worstRate()).isEmpty();
    }

    @Test
    void aCardWithNoMeasurableRateSaysSo() {
        Growth built = Growth.of(List.of(card("0.400", 13800, 1), card("0.500", 13820, 2)));
        assertThat(built.toString()).contains("no measurable rate");
    }

    @Test
    void aCardWithARateNamesIt() {
        Growth built = Growth.of(List.of(card("0.400", 13800, 1), card("0.500", 14800, 2)));
        assertThat(built.toString()).contains("worst 0.1000 in per 1000 FC");
    }

    @Test
    void aGrowthWithNoStepsTotalsNothing() {
        assertThat(new Growth(Sample.card(1).reference(), 1, List.of()).total()).isEqualTo(new Length(0));
    }

    @Test
    void aCrackOutgrowsTheAnalysisOnlyWhenItsWorstStretchIsQuicker() {
        GrowthBasis pressurised = Schedules.basisFor(PanelKind.PRESSURISED_SKIN);
        assertThat(pressurised.inchesPerThousand()).isEqualByComparingTo("0.100");
        Growth quick = Growth.of(List.of(card("0.400", 13800, 1), card("0.550", 14800, 2)));
        Growth steady = Growth.of(List.of(card("0.400", 13800, 1), card("0.500", 14800, 2)));
        assertThat(quick.outgrows(pressurised)).isTrue();
        assertThat(steady.outgrows(pressurised)).isFalse();
        assertThat(steady.outgrows(Schedules.basisFor(PanelKind.UNPRESSURISED_SKIN)))
                .isTrue();
        Growth once = Growth.of(List.of(card("0.400", 13800, 1)));
        assertThat(once.outgrows(pressurised)).isFalse();
    }

    @Test
    void theDiameterAStopDrillAddsIsNotGrowth() {
        Finding raised = card("0.400", 13800, 1);
        Finding drilled = raised.stopDrilled(Sample.inch("0.098"), raised.foundOn(), Sample.flown(13900, 27800));
        Finding later = drilled.reMeasured(
                new Crack(Sample.inch("0.548"), new Angle(0), true, true, 2),
                raised.foundOn(),
                Sample.flown(14900, 29800));
        Growth growth = Growth.of(List.of(raised, drilled, later));
        assertThat(growth.steps()).hasSize(2);
        assertThat(growth.steps().get(0).grewBy()).isEqualTo(new Length(0));
        assertThat(growth.steps().get(1).grewBy()).isEqualTo(Sample.inch("0.050"));
        assertThat(growth.total()).isEqualTo(Sample.inch("0.050"));
    }
}
