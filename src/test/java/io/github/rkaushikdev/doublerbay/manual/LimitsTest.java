package io.github.rkaushikdev.doublerbay.manual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Ratio;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class LimitsTest {

    private static final Length SKIN = Length.ofInches("0.063");

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static LimitSet limits() {
        return LimitSet.allowable();
    }

    @Test
    void aLimitSetReadsBackTheNumbersItCarries() {
        String printed = limits().withMaxDepth(inch("0.005"))
                .withMaxSharpness(new Ratio("0.02"))
                .toString();
        assertThat(printed).contains("depth 0.005");
        assertThat(printed).contains("sharpness 2.00%");
    }

    @Test
    void aPageThatAllowsNothingSaysSo() {
        assertThat(LimitSet.nothingAllowable()).hasToString("nothing allowable");
        assertThat(LimitSet.nothingAllowable().allowed()).isFalse();
    }

    @Test
    void aPageThatAllowsNothingCannotAlsoCarryALimit() {
        assertThatThrownBy(() -> LimitSet.nothingAllowable().withMaxDepth(inch("0.005")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not carry limits");
    }

    @Test
    void aPageThatAllowsSomethingAndCapsNothingIsAnOpenPage() {
        assertThat(limits().isOpen()).isTrue();
        assertThat(limits().withMaxDepth(inch("0.005")).isOpen()).isFalse();
        assertThat(LimitSet.nothingAllowable().isOpen()).isFalse();
        assertThat(limits()).hasToString("no limits stated");
    }

    @Test
    void anAbsoluteDepthCapReadsStraightOff() {
        assertThat(limits().withMaxDepth(inch("0.005")).depthCap(SKIN)).contains(inch("0.005"));
    }

    @Test
    void aFractionalDepthCapIsReadAgainstTheThicknessAtTheDamage() {
        LimitSet tenPercent = limits().withMaxDepthFraction(new Ratio("0.10"));
        assertThat(tenPercent.depthCap(SKIN)).contains(new Length(6));
        assertThat(tenPercent.depthCap(inch("0.100"))).contains(inch("0.010"));
    }

    @Test
    void theTighterOfTheTwoDepthFormsGoverns() {
        LimitSet both = limits().withMaxDepth(inch("0.005")).withMaxDepthFraction(new Ratio("0.10"));
        assertThat(both.depthCap(SKIN)).contains(new Length(5));
        assertThat(both.depthCap(inch("0.032"))).contains(new Length(3));
    }

    @Test
    void aScoreAcrossTheLoadGetsItsShareOfTheTighterCap() {
        LimitSet page = limits().withMaxDepth(inch("0.005"))
                .withMaxDepthFraction(new Ratio("0.10"))
                .withAcrossLoadShare(new Ratio("0.50"));
        assertThat(page.depthCap(inch("0.032"), true)).contains(new Length(2));
        assertThat(page.depthCap(inch("0.032"), false)).contains(new Length(3));
        assertThat(page.depthCap(SKIN, true)).contains(new Length(2));
        assertThat(page.depthCap(SKIN, false)).contains(new Length(5));
    }

    @Test
    void aPageWithNoShareAcrossTheLoadTreatsEveryDirectionAlike() {
        LimitSet page = limits().withMaxDepth(inch("0.005"));
        assertThat(page.depthCap(SKIN, true)).isEqualTo(page.depthCap(SKIN, false));
    }

    @Test
    void aShareAcrossTheLoadIsAPartOfACapThatExists() {
        assertThatThrownBy(() -> limits().withMaxDepth(inch("0.005")).withAcrossLoadShare(new Ratio("1.20")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("part of the depth cap");
        assertThatThrownBy(() -> limits().withMaxDepth(inch("0.005")).withAcrossLoadShare(new Ratio("0")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("part of the depth cap");
        assertThatThrownBy(() -> limits().withAcrossLoadShare(new Ratio("0.50")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("needs a depth cap");
    }

    @Test
    void aShareAcrossTheLoadIsPartOfWhatMakesTwoPagesTheSame() {
        LimitSet plain = limits().withMaxDepthFraction(new Ratio("0.05"));
        LimitSet shared = plain.withAcrossLoadShare(new Ratio("0.50"));
        assertThat(shared).isNotEqualTo(plain);
        assertThat(shared).isEqualTo(plain.withAcrossLoadShare(new Ratio("0.5000")));
        assertThat(shared).hasSameHashCodeAs(plain.withAcrossLoadShare(new Ratio("0.5000")));
        assertThat(shared).hasToString("depth 5.00%, across the load 50.00% of that");
    }

    @Test
    void aPageWithNoDepthFormCapsNoDepth() {
        assertThat(limits().withMaxLength(inch("2.000")).depthCap(SKIN)).isEmpty();
    }

    @Test
    void anAbsoluteResidualFloorReadsStraightOff() {
        assertThat(limits().withMinResidual(inch("0.030")).residualFloor(SKIN)).contains(inch("0.030"));
    }

    @Test
    void aFractionalResidualFloorIsReadAgainstTheThickness() {
        assertThat(limits().withMinResidualFraction(new Ratio("0.80")).residualFloor(SKIN))
                .contains(new Length(50));
    }

    @Test
    void theHigherOfTheTwoResidualFormsGoverns() {
        LimitSet both = limits().withMinResidual(inch("0.055")).withMinResidualFraction(new Ratio("0.80"));
        assertThat(both.residualFloor(SKIN)).contains(inch("0.055"));
        assertThat(both.residualFloor(inch("0.100"))).contains(inch("0.080"));
    }

    @Test
    void aPageWithNoResidualFormFloorsNothing() {
        assertThat(limits().withMaxDepth(inch("0.005")).residualFloor(SKIN)).isEmpty();
    }

    @Test
    void anEdgeMarginInInchesReadsWithoutAFastener() {
        assertThat(limits().withMinEdgeMargin(inch("0.500")).edgeMarginFloor()).contains(inch("0.500"));
    }

    @Test
    void anEdgeMarginInDiametersNeedsTheFastenerItIsWrittenAgainst() {
        LimitSet inD = limits().withMinEdgeMarginDiameters(new BigDecimal("2.0"));
        assertThat(inD.edgeMarginFloor(new FastenerSize(8))).contains(inch("0.500"));
        assertThat(inD.edgeMarginFloor(new FastenerSize(4))).contains(inch("0.250"));
        assertThatThrownBy(inD::edgeMarginFloor)
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("needs a fastener size");
    }

    @Test
    void theLargerEdgeMarginFormGoverns() {
        LimitSet both = limits().withMinEdgeMargin(inch("0.400")).withMinEdgeMarginDiameters(new BigDecimal("2.0"));
        assertThat(both.edgeMarginFloor(new FastenerSize(8))).contains(inch("0.500"));
        assertThat(both.edgeMarginFloor(new FastenerSize(4))).contains(inch("0.400"));
    }

    @Test
    void aPageWithNoEdgeMarginFloorsNothing() {
        assertThat(limits().withMaxDepth(inch("0.005")).edgeMarginFloor(new FastenerSize(5)))
                .isEmpty();
    }

    @Test
    void whatADepthCapLeavesBehindCanBeComparedWithTheResidualFloor() {
        LimitSet page = limits().withMaxDepth(inch("0.005")).withMinResidualFraction(new Ratio("0.80"));
        assertThat(page.impliedResidual(SKIN)).contains(inch("0.058"));
        assertThat(page.residualFloor(SKIN)).contains(new Length(50));
        assertThat(page.impliedResidual(SKIN)
                        .orElseThrow()
                        .isGreaterThan(page.residualFloor(SKIN).orElseThrow()))
                .isTrue();
    }

    @Test
    void aPageWithNoDepthCapImpliesNoResidual() {
        assertThat(limits().withMaxLength(inch("2.000")).impliedResidual(SKIN)).isEmpty();
    }

    @Test
    void anAreaLimitHasToBeAnArea() {
        assertThatThrownBy(() -> limits().withMaxArea(new BigDecimal("0")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("an area limit is an area");
    }

    @Test
    void anEdgeMarginInDiametersHasToBeADistance() {
        assertThatThrownBy(() -> limits().withMinEdgeMarginDiameters(new BigDecimal("0")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is a distance");
    }

    @Test
    void aBlendTaperRidesAlongWithTheLimits() {
        assertThat(limits().withBlendTaper(new Taper(20)).blendTaper()).contains(new Taper(20));
        assertThat(limits().blendTaper()).isEmpty();
    }

    @Test
    void aLimitSetIsFrozenAndHashesByValue() {
        assertThat(new HashSet<>(List.of(limits().withMaxDepth(inch("0.005")), limits().withMaxDepth(inch("0.005")))))
                .hasSize(1);
        // Assigning to a limit once the set is built does not compile: every component is final.
        Types.isFrozen(LimitSet.class);
    }
}
