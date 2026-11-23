package io.github.rkaushikdev.doublerbay.repair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Scoring;
import io.github.rkaushikdev.doublerbay.grid.Point;
import io.github.rkaushikdev.doublerbay.manual.CleanupRules;
import io.github.rkaushikdev.doublerbay.manual.Cleanups;
import io.github.rkaushikdev.doublerbay.manual.LimitSet;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Ratio;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BlendTest {

    private static final CleanupRules RULES = Cleanups.standardRules();

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Finding scratch(String depth, String length, Point at) {
        return Sample.cardWith()
                .kind(DamageKind.SCRATCH)
                .geometry(new Scoring(inch(depth), inch(length), inch("0.060"), Angle.ofDegrees(0)))
                .at(at)
                .build();
    }

    private static Finding scratch(String depth, String length) {
        return scratch(depth, length, Sample.cabinPointOn(10, "0.000"));
    }

    private static Finding scratch(String depth) {
        return scratch(depth, "2.000");
    }

    private static Finding scratch(Point at) {
        return scratch("0.003", "2.000", at);
    }

    private static Finding scratch() {
        return scratch("0.003");
    }

    private static BlendPlan planned(Finding finding, LimitSet limits) {
        Placement placement = Placement.place(Sample.tailContext(), finding);
        return Blends.planBlend(finding, placement, RULES.forKind(finding.kind()), limits);
    }

    private static BlendPlan planned(Finding finding) {
        return planned(finding, LimitSet.allowable());
    }

    private static BlendPlan planned() {
        return planned(scratch());
    }

    private static boolean clears(Finding finding, LimitSet limits) {
        Placement placement = Placement.place(Sample.tailContext(), finding);
        return Blends.blendWouldClear(finding, placement, RULES.forKind(finding.kind()), limits);
    }

    @Test
    void theCutGoesPastTheBottomOfTheDamage() {
        BlendPlan plan = planned(scratch("0.003"));
        assertThat(plan.cutDepth()).isEqualTo(inch("0.005"));
    }

    @Test
    void whatIsLeftIsTheThicknessLessTheCut() {
        BlendPlan plan = planned(scratch("0.003"));
        assertThat(plan.residual()).isEqualTo(inch("0.035"));
    }

    @Test
    void theBlendRunsOutPastBothEndsOfTheDamage() {
        BlendPlan plan = planned(scratch("0.003", "2.000"));
        assertThat(plan.along()).isEqualTo(inch("2.200"));
        assertThat(plan.around().isGreaterThan(inch("0.060"))).isTrue();
    }

    @Test
    void thePlanReadsBackAsAWorkCardLine() {
        String printed = planned(scratch("0.003")).toString();
        assertThat(printed).startsWith("cut 0.005 at 20:1");
        assertThat(printed).contains("0.035 left");
    }

    @Test
    void evenTheShallowestBlendGoesThroughTheCladdingOnThinSkin() {
        Finding shallow = Sample.cardWith()
                .kind(DamageKind.SCRATCH)
                .geometry(new Scoring(inch("0.001"), inch("1.000"), inch("0.060"), Angle.ofDegrees(0)))
                .at(Sample.cabinPointOn(10, "0.000"))
                .build();
        BlendPlan plan = planned(shallow);
        assertThat(plan.cutDepth()).isEqualTo(inch("0.003"));
        assertThat(plan.breachesClad()).isTrue();
        assertThat(plan.finishRequired()).isTrue();
    }

    @Test
    void aCutThroughTheCladdingAsksForTheFinishBack() {
        BlendPlan plan = planned(scratch("0.005"));
        assertThat(plan.breachesClad()).isTrue();
        assertThat(plan.finishRequired()).isTrue();
        assertThat(plan.toString()).endsWith("refinish");
    }

    @Test
    void aKindWithNoDepthHasNothingToBlend() {
        Finding dented = Sample.cardWith()
                .geometry(new Dent(inch("0.010"), inch("3.000"), inch("2.000")))
                .build();
        assertThatThrownBy(() -> planned(dented)).isInstanceOf(Refused.class).hasMessageContaining("no blend here");
    }

    @Test
    void aBlendIsRefusedWhereTheBookWillNotHaveOne() {
        Finding onARow = scratch(Sample.cabinPointOn(8, "0.500"));
        assertThatThrownBy(() -> planned(onARow)).isInstanceOf(Refused.class).hasMessageContaining("no blend here");
    }

    @Test
    void aCutThatWouldGoThroughTheSkinIsRefused() {
        Finding deep = scratch("0.060");
        assertThatThrownBy(() -> planned(deep)).isInstanceOf(Refused.class).hasMessageContaining("nothing left");
    }

    @Test
    void thePlanAreaIsTheFinishedBlendAndNotTheDamage() {
        BlendPlan plan = planned(scratch("0.003", "2.000"));
        assertThat(plan.area()).isGreaterThan(new BigDecimal("0.10"));
        assertThat(plan.longerSide()).isEqualTo(plan.along());
    }

    @Test
    void aPlanThatLeavesEnoughClearsTheResidualFloor() {
        BlendPlan plan = planned(scratch("0.003"));
        assertThat(plan.leavesEnough(LimitSet.allowable().withMinResidualFraction(new Ratio("0.85")), inch("0.040")))
                .isTrue();
        assertThat(plan.leavesEnough(LimitSet.allowable().withMinResidualFraction(new Ratio("0.95")), inch("0.040")))
                .isFalse();
    }

    @Test
    void aPlanWithNoResidualFloorAlwaysLeavesEnough() {
        assertThat(planned().leavesEnough(LimitSet.allowable(), inch("0.040"))).isTrue();
    }

    @Test
    void aPlanInsideTheAreaCapClearsIt() {
        BlendPlan plan = planned(scratch("0.003", "1.000"));
        assertThat(plan.withinArea(LimitSet.allowable().withMaxArea(new BigDecimal("4.00"))))
                .isTrue();
        assertThat(plan.withinArea(LimitSet.allowable().withMaxArea(new BigDecimal("0.01"))))
                .isFalse();
        assertThat(plan.withinArea(LimitSet.allowable())).isTrue();
    }

    @Test
    void aBlendThatGetsTheDamageInsideTheBookSaysSo() {
        LimitSet limits = LimitSet.allowable().withMinResidualFraction(new Ratio("0.85"));
        assertThat(clears(scratch("0.003"), limits)).isTrue();
    }

    @Test
    void aBlendThatWouldNotLeaveEnoughDoesNotClearIt() {
        LimitSet limits = LimitSet.allowable().withMinResidualFraction(new Ratio("0.85"));
        assertThat(clears(scratch("0.010"), limits)).isFalse();
    }

    @Test
    void aBlendThatCannotHappenAtAllDoesNotClearIt() {
        assertThat(clears(scratch(Sample.cabinPointOn(8, "0.500")), LimitSet.allowable()))
                .isFalse();
    }

    @Test
    void aBlendTakingTooMuchOfThePanelDoesNotClearIt() {
        assertThat(clears(scratch("0.003", "30.000"), LimitSet.allowable())).isFalse();
    }

    @Test
    void aNarrowScratchIsBlendedTwoEndRadiiAcross() {
        BlendPlan plan = planned(scratch("0.003", "2.000"));
        assertThat(plan.endRadius()).contains(inch("0.250"));
        assertThat(plan.around()).isEqualTo(inch("0.500"));
        assertThat(plan.along()).isEqualTo(inch("2.200"));
        assertThat(plan.toString()).contains("2.200 by 0.500, 0.250 ends");
    }

    @Test
    void aPlanNarrowerThanItsOwnEndsIsRefused() {
        assertThatThrownBy(() -> new BlendPlan(
                        inch("0.005"),
                        inch("2.000"),
                        inch("0.300"),
                        new Taper(20),
                        inch("0.035"),
                        false,
                        false,
                        Optional.of(inch("0.250"))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("two end radii across");
        BlendPlan open = new BlendPlan(
                inch("0.005"),
                inch("2.000"),
                inch("0.300"),
                new Taper(20),
                inch("0.035"),
                false,
                false,
                Optional.empty());
        assertThat(open.toString()).doesNotContain("ends");
    }
}
