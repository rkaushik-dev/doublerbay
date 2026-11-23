package io.github.rkaushikdev.doublerbay.tolerance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CpcpTest {

    private static final PanelId PART = new PanelId(53, 10, 143, "L");
    private static final LocalDate FOUND = LocalDate.of(2025, 5, 6);

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Corrosion rot(String depth, boolean exfoliating) {
        return new Corrosion(inch("2.000"), inch("1.000"), inch(depth), false, exfoliating);
    }

    private static Corrosion rot() {
        return rot("0.004", false);
    }

    private static Corrosion stain() {
        return new Corrosion(inch("2.000"), inch("1.000"), inch("0"));
    }

    private static CorrosionContext context(boolean withinLimits, boolean before, boolean urgent) {
        return new CorrosionContext(PART, withinLimits, before, urgent);
    }

    private static CorrosionContext context() {
        return context(true, false, false);
    }

    @Test
    void theLevelsOrderAndReadBack() {
        assertThat(CorrosionLevel.THREE.worseThan(CorrosionLevel.TWO)).isTrue();
        assertThat(CorrosionLevel.TWO.worseThan(CorrosionLevel.ONE)).isTrue();
        assertThat(CorrosionLevel.TWO).hasToString("level 2");
    }

    @Test
    void onlyLevelThreeGoesToTheAuthority() {
        assertThat(CorrosionLevel.THREE.isReportable()).isTrue();
        assertThat(CorrosionLevel.TWO.isReportable()).isFalse();
        assertThat(CorrosionLevel.ONE.isReportable()).isFalse();
    }

    @Test
    void anythingAboveLevelOneSaysTheProgrammeIsNotCoping() {
        assertThat(CorrosionLevel.ONE.needsProgrammeReview()).isFalse();
        assertThat(CorrosionLevel.TWO.needsProgrammeReview()).isTrue();
        assertThat(CorrosionLevel.THREE.isWorseThanExpected()).isTrue();
    }

    @Test
    void corrosionInsideTheLimitsIsLevelOne() {
        assertThat(CorrosionLevel.levelFor(rot(), context(true, false, false))).isSameAs(CorrosionLevel.ONE);
    }

    @Test
    void theSameFindingIsLevelOneTheFirstTimeAndTwoTheSecond() {
        CorrosionContext outside = context(false, false, false);
        CorrosionContext again = context(false, true, false);
        assertThat(CorrosionLevel.levelFor(rot(), outside)).isSameAs(CorrosionLevel.ONE);
        assertThat(CorrosionLevel.levelFor(rot(), again)).isSameAs(CorrosionLevel.TWO);
    }

    @Test
    void corrosionInsideTheLimitsStaysLevelOneHoweverOftenItComesBack() {
        assertThat(CorrosionLevel.levelFor(rot(), context(true, true, false))).isSameAs(CorrosionLevel.ONE);
    }

    @Test
    void exfoliationIsNeverInsideTheLimitsWhateverTheContextSays() {
        Corrosion lifted = rot("0.001", true);
        assertThat(CorrosionLevel.levelFor(lifted, context(true, false, false))).isSameAs(CorrosionLevel.ONE);
        assertThat(CorrosionLevel.levelFor(lifted, context(true, true, false))).isSameAs(CorrosionLevel.TWO);
    }

    @Test
    void anUrgentAirworthinessConcernIsLevelThreeWhateverElseIsTrue() {
        assertThat(CorrosionLevel.levelFor(rot(), context(true, false, true))).isSameAs(CorrosionLevel.THREE);
        assertThat(CorrosionLevel.levelFor(rot(), context(true, false, true))).isSameAs(CorrosionLevel.THREE);
    }

    @Test
    void theContextReadsBackWhatTheProgrammeKnows() {
        assertThat(context()).hasToString("5310-143L, first time here");
        assertThat(context(true, true, false).toString()).endsWith("seen here before");
    }

    @Test
    void gradingProducesAnEntryWithTheLevelOnIt() {
        CorrosionEntry entry = CorrosionEntry.grade(rot(), context(), FOUND);
        assertThat(entry.level()).isSameAs(CorrosionLevel.ONE);
        assertThat(entry.part()).isEqualTo(PART);
        assertThat(entry).hasToString("5310-143L level 1 on 2025-05-06");
    }

    @Test
    void aLevelThreeEntryCarriesAClock() {
        CorrosionEntry entry = CorrosionEntry.grade(rot(), context(true, false, true), FOUND);
        assertThat(entry.mustBeReported()).isTrue();
        assertThat(entry.reportBy()).contains(LocalDate.of(2025, 5, 9));
    }

    @Test
    void aLowerLevelEntryCarriesNoClock() {
        CorrosionEntry entry = CorrosionEntry.grade(rot(), context(), FOUND);
        assertThat(entry.mustBeReported()).isFalse();
        assertThat(entry.reportBy()).isEmpty();
        assertThat(entry.isOverdue(LocalDate.of(2099, 1, 1))).isFalse();
    }

    @Test
    void aLevelThreeReportGoesOverdueAfterItsClockRunsOut() {
        CorrosionEntry entry = CorrosionEntry.grade(rot(), context(true, false, true), FOUND);
        assertThat(entry.isOverdue(LocalDate.of(2025, 5, 9))).isFalse();
        assertThat(entry.isOverdue(LocalDate.of(2025, 5, 10))).isTrue();
    }

    @Test
    void aSurfaceStainCannotBeOutsideTheAllowableLimits() {
        assertThatThrownBy(() -> CorrosionEntry.grade(stain(), context(false, false, false), FOUND))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("cannot be outside the allowable limits");
    }

    @Test
    void aSurfaceStainInsideTheLimitsGradesNormally() {
        assertThat(CorrosionEntry.grade(stain(), context(), FOUND).level()).isSameAs(CorrosionLevel.ONE);
    }

    @Test
    void aSurfaceStainCanStillBeAnUrgentConcern() {
        CorrosionEntry entry = CorrosionEntry.grade(stain(), context(false, false, true), FOUND);
        assertThat(entry.level()).isSameAs(CorrosionLevel.THREE);
    }

    @Test
    void anEntryIsFrozenAndHashesByValue() {
        Set<CorrosionEntry> both = new HashSet<>(Set.of(new CorrosionEntry(PART, CorrosionLevel.ONE, FOUND)));
        both.addAll(Set.of(new CorrosionEntry(PART, CorrosionLevel.ONE, FOUND)));
        assertThat(both).hasSize(1);
        // Reassigning an entry's level does not compile: every field is final and there is no setter.
        Types.isFrozen(CorrosionEntry.class);
    }

    @Test
    void widespreadCorrosionOutsideTheLimitsIsLevelTwoTheFirstTime() {
        CorrosionContext spread = new CorrosionContext(PART, false, false, false, true);
        assertThat(CorrosionLevel.levelFor(rot(), spread)).isSameAs(CorrosionLevel.TWO);
        assertThat(spread).hasToString("5310-143L, first time here, widespread");
    }

    @Test
    void widespreadCorrosionTheProgrammeIsCopingWithIsStillLevelOne() {
        CorrosionContext spread = new CorrosionContext(PART, true, false, false, true);
        assertThat(CorrosionLevel.levelFor(rot(), spread)).isSameAs(CorrosionLevel.ONE);
        assertThat(CorrosionLevel.levelFor(rot("0.004", true), spread)).isSameAs(CorrosionLevel.TWO);
    }

    @Test
    void corrosionOnOneElementIsNotWidespreadUnlessSaidSo() {
        assertThat(context().widespread()).isFalse();
        assertThat(context(false, false, true).widespread()).isFalse();
        assertThat(CorrosionLevel.levelFor(rot(), new CorrosionContext(PART, false, false, true, true)))
                .isSameAs(CorrosionLevel.THREE);
    }
}
