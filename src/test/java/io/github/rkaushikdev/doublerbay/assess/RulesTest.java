package io.github.rkaushikdev.doublerbay.assess;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.LightningBurn;
import io.github.rkaushikdev.doublerbay.damage.Scoring;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.JointMember;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.manual.Cleanups;
import io.github.rkaushikdev.doublerbay.manual.LimitSet;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Ratio;
import io.github.rkaushikdev.doublerbay.structure.Margin;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class RulesTest {

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Case caseOf(Finding finding, LimitSet limits) {
        return new Case(
                finding,
                Placement.place(Sample.tailContext(), finding),
                limits,
                Cleanups.standardRules().forKind(finding.kind()));
    }

    private static Case caseOf(Finding finding) {
        return caseOf(finding, LimitSet.allowable());
    }

    private static Case caseOf(LimitSet limits) {
        return caseOf(Sample.card(Sample.cabinPoint()), limits);
    }

    private static Case caseOf() {
        return caseOf(LimitSet.allowable());
    }

    private static Finding dent(String depth, String along, String around, boolean sharp, boolean cracked) {
        return Sample.cardWith()
                .geometry(new Dent(inch(depth), inch(along), inch(around), sharp, cracked))
                .at(Sample.cabinPointOn(10, "4.000"))
                .build();
    }

    private static Finding dent(String depth, String along, String around) {
        return dent(depth, along, around, false, false);
    }

    private static Finding dent(String depth) {
        return dent(depth, "3.000", "2.000");
    }

    private static Finding dent() {
        return dent("0.020");
    }

    private static Finding scratch(String depth, String length) {
        return Sample.cardWith()
                .kind(DamageKind.SCRATCH)
                .geometry(new Scoring(inch(depth), inch(length), inch("0.060"), Angle.ofDegrees(0)))
                .at(Sample.cabinPointOn(10, "4.000"))
                .build();
    }

    private static Finding scratch(String depth) {
        return scratch(depth, "2.000");
    }

    private static Finding scratch() {
        return scratch("0.002");
    }

    private static Finding rot(String along, String around, String depth, boolean exfoliating) {
        return Sample.cardWith()
                .kind(DamageKind.CORROSION)
                .geometry(new Corrosion(inch(along), inch(around), inch(depth), false, exfoliating))
                .at(Sample.cabinPointOn(10, "4.000"))
                .origin(DamageOrigin.ENVIRONMENT)
                .build();
    }

    private static Finding rot() {
        return rot("2.000", "1.000", "0.001", false);
    }

    @Test
    void aPageThatAllowsNothingFailsTheFirstCheck() {
        Case blocked = caseOf(LimitSet.nothingAllowable());
        assertThat(Rules.allowedCheck(blocked).passed()).isFalse();
        assertThat(Rules.allowedCheck(blocked).outcome()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void aPageThatAllowsSomethingPassesTheFirstCheck() {
        assertThat(Rules.allowedCheck(caseOf()).passed()).isTrue();
        assertThat(Rules.allowedCheck(caseOf()).outcome()).isSameAs(Disposition.ALLOWABLE);
    }

    @Test
    void aDepthInsideTheCapPasses() {
        Check checked = Rules.depthCheck(
                        caseOf(dent("0.020"), LimitSet.allowable().withMaxDepth(inch("0.060"))))
                .orElseThrow();
        assertThat(checked.passed()).isTrue();
        assertThat(checked.reason().detail()).contains("0.020 deep against a cap of 0.060");
    }

    @Test
    void aScratchOverTheCapLandsOnReworkBecauseItCanBeGroundOut() {
        Check checked = Rules.depthCheck(
                        caseOf(scratch("0.006"), LimitSet.allowable().withMaxDepth(inch("0.003"))))
                .orElseThrow();
        assertThat(checked.passed()).isFalse();
        assertThat(checked.outcome()).isSameAs(Disposition.REWORKABLE);
    }

    private static Finding scoreAt(String depth, int degrees) {
        return Sample.cardWith()
                .kind(DamageKind.SCRATCH)
                .geometry(new Scoring(inch(depth), inch("2.000"), inch("0.060"), Angle.ofDegrees(degrees)))
                .at(Sample.cabinPointOn(10, "4.000"))
                .build();
    }

    @Test
    void aScoreAlongTheBodyIsHeldToItsHoopShareOfTheCap() {
        LimitSet page = LimitSet.allowable().withMaxDepth(inch("0.004")).withHoopLoadedShare(new Ratio("0.50"));
        Check along = Rules.depthCheck(caseOf(scoreAt("0.003", 0), page)).orElseThrow();
        Check around = Rules.depthCheck(caseOf(scoreAt("0.003", 90), page)).orElseThrow();
        assertThat(along.passed()).isFalse();
        assertThat(along.reason().detail()).contains("against a cap of 0.002 along the body");
        assertThat(around.passed()).isTrue();
        assertThat(around.reason().detail())
                .contains("against a cap of 0.004 at")
                .doesNotContain("along the body");
    }

    @Test
    void aScoreAlongTheBodyOnAPageWithNoShareIsReadAgainstTheWholeCap() {
        Check along = Rules.depthCheck(
                        caseOf(scoreAt("0.003", 0), LimitSet.allowable().withMaxDepth(inch("0.004"))))
                .orElseThrow();
        assertThat(along.passed()).isTrue();
        assertThat(along.reason().detail()).doesNotContain("along the body");
    }

    @Test
    void aDentOverTheCapLandsOnRepairBecauseThereIsNothingToGrind() {
        Check checked = Rules.depthCheck(
                        caseOf(dent("0.080"), LimitSet.allowable().withMaxDepth(inch("0.060"))))
                .orElseThrow();
        assertThat(checked.passed()).isFalse();
        assertThat(checked.outcome()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void theSameScratchLandsOnRepairWhereGrindingIsNotAllowed() {
        Finding onARow = Sample.cardWith()
                .kind(DamageKind.SCRATCH)
                .geometry(new Scoring(inch("0.006"), inch("2.000"), inch("0.060"), Angle.ofDegrees(0)))
                .at(Sample.cabinPointOn(8, "0.500"))
                .build();
        Check checked = Rules.depthCheck(caseOf(onARow, LimitSet.allowable().withMaxDepth(inch("0.003"))))
                .orElseThrow();
        assertThat(checked.passed()).isFalse();
        assertThat(checked.outcome()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void aFractionalCapIsReadAgainstTheThicknessAtTheDamage() {
        Check checked = Rules.depthCheck(
                        caseOf(scratch("0.003"), LimitSet.allowable().withMaxDepthFraction(new Ratio("0.10"))))
                .orElseThrow();
        assertThat(checked.passed()).isTrue();
    }

    @Test
    void aPageWithNoDepthCapRunsNoDepthCheck() {
        assertThat(Rules.depthCheck(caseOf(dent(), LimitSet.allowable().withMaxLength(inch("1.0")))))
                .isEmpty();
    }

    @Test
    void aKindWithNoDepthRunsNoDepthCheck() {
        Finding cracked = Sample.cardWith()
                .kind(DamageKind.CRACK)
                .geometry(new Crack(inch("0.750"), Angle.ofDegrees(0)))
                .at(Sample.cabinPointOn(10, "4.000"))
                .origin(DamageOrigin.FATIGUE)
                .build();
        assertThat(Rules.depthCheck(caseOf(cracked, LimitSet.allowable().withMaxDepth(inch("0.010")))))
                .isEmpty();
    }

    @Test
    void theResidualFloorIsMeasuredOnWhatTheCleanupWouldLeave() {
        LimitSet floor = LimitSet.allowable().withMinResidualFraction(new Ratio("0.80"));
        assertThat(Rules.residualCheck(caseOf(scratch("0.002"), floor))
                        .orElseThrow()
                        .passed())
                .isTrue();
        Check over = Rules.residualCheck(caseOf(scratch("0.010"), floor)).orElseThrow();
        assertThat(over.passed()).isFalse();
        assertThat(over.outcome()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void theCleanupCutsBelowTheBottomOfTheDamage() {
        Case shallow = caseOf(scratch("0.002"));
        assertThat(shallow.cutDepth()).contains(shallow.depth().orElseThrow().plus(inch("0.002")));
    }

    @Test
    void aKindThatCannotBeBlendedIsCutNoDeeperThanTheDamage() {
        Case solid = caseOf(dent("0.020"));
        assertThat(solid.cutDepth()).isEqualTo(solid.depth());
    }

    @Test
    void aSharpDentFailsTheSharpnessCheck() {
        LimitSet limits = LimitSet.allowable().withMaxSharpness(new Ratio("0.05"));
        assertThat(Rules.sharpnessCheck(caseOf(dent("0.020", "3.000", "2.000"), limits))
                        .orElseThrow()
                        .passed())
                .isTrue();
        Check tight = Rules.sharpnessCheck(caseOf(dent("0.020", "0.300", "0.250"), limits))
                .orElseThrow();
        assertThat(tight.passed()).isFalse();
    }

    @Test
    void sharpnessIsTakenOnTheNarrowDiameter() {
        LimitSet limits = LimitSet.allowable().withMaxSharpness(new Ratio("0.05"));
        Check checked = Rules.sharpnessCheck(caseOf(dent("0.020", "12.000", "0.300"), limits))
                .orElseThrow();
        assertThat(checked.passed()).isFalse();
    }

    @Test
    void aCrackedOrSharpBottomedDentFailsTheSmoothnessCheck() {
        assertThat(Rules.smoothnessCheck(caseOf(dent())).orElseThrow().passed()).isTrue();
        Finding cracked = dent("0.020", "3.000", "2.000", false, true);
        Finding sharp = dent("0.020", "3.000", "2.000", true, false);
        assertThat(Rules.smoothnessCheck(caseOf(cracked)).orElseThrow().passed())
                .isFalse();
        assertThat(Rules.smoothnessCheck(caseOf(sharp)).orElseThrow().passed()).isFalse();
        assertThat(Rules.smoothnessCheck(caseOf(cracked)).orElseThrow().reason().detail())
                .contains("cracked");
    }

    @Test
    void onlyADentGetsTheDentChecks() {
        assertThat(Rules.sharpnessCheck(caseOf(scratch()))).isEmpty();
        assertThat(Rules.smoothnessCheck(caseOf(scratch()))).isEmpty();
    }

    @Test
    void aScoreLongerThanTheCapFails() {
        LimitSet limits = LimitSet.allowable().withMaxLength(inch("1.000"));
        assertThat(Rules.lengthCheck(caseOf(scratch("0.002", "2.000"), limits))
                        .orElseThrow()
                        .passed())
                .isFalse();
        assertThat(Rules.lengthCheck(caseOf(scratch("0.002", "0.500"), limits))
                        .orElseThrow()
                        .passed())
                .isTrue();
    }

    @Test
    void onlyTheKindsMeasuredAsAPatchGetTheAreaCheck() {
        LimitSet limits = LimitSet.allowable().withMaxArea(new BigDecimal("4.00"));
        assertThat(Rules.areaCheck(caseOf(dent(), limits))).isEmpty();
        assertThat(Rules.areaCheck(caseOf(rot(), limits)).orElseThrow().passed())
                .isTrue();
    }

    @Test
    void corrosionOverTheAreaCapFails() {
        LimitSet limits = LimitSet.allowable().withMaxArea(new BigDecimal("1.00"));
        assertThat(Rules.areaCheck(caseOf(rot("4.000", "2.000", "0.001", false), limits))
                        .orElseThrow()
                        .passed())
                .isFalse();
    }

    @Test
    void exfoliationFailsWhateverTheDepthSays() {
        assertThat(Rules.exfoliationCheck(caseOf(rot())).orElseThrow().passed()).isTrue();
        Check lifted = Rules.exfoliationCheck(caseOf(rot("2.000", "1.000", "0.001", true)))
                .orElseThrow();
        assertThat(lifted.passed()).isFalse();
        assertThat(lifted.outcome()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void onlyCorrosionGetsTheExfoliationCheck() {
        assertThat(Rules.exfoliationCheck(caseOf(dent()))).isEmpty();
    }

    @Test
    void theEdgeMarginCheckReadsTheFastenerWhereThereIsOne() {
        Finding onARow = Sample.card(Sample.cabinPointOn(8, "0.500"));
        LimitSet limits = LimitSet.allowable().withMinEdgeMarginDiameters(new BigDecimal("2.0"));
        Optional<Check> checked = Rules.edgeMarginCheck(caseOf(onARow, limits));
        assertThat(checked).isPresent();
        assertThat(checked.get().outcome()).isIn(Disposition.ALLOWABLE, Disposition.REPAIRABLE);
    }

    @Test
    void aPageWithNoEdgeMarginRunsNoEdgeMarginCheck() {
        assertThat(Rules.edgeMarginCheck(caseOf(dent(), LimitSet.allowable()))).isEmpty();
    }

    @Test
    void aBlendThatWouldTakeTooMuchOfThePanelFails() {
        Finding wide = Sample.cardWith()
                .kind(DamageKind.SCRATCH)
                .geometry(new Scoring(inch("0.006"), inch("30.000"), inch("0.060"), Angle.ofDegrees(0)))
                .at(Sample.cabinPointOn(10, "4.000"))
                .build();
        Check checked = Rules.blendShareCheck(caseOf(wide)).orElseThrow();
        assertThat(checked.passed()).isFalse();
        assertThat(checked.reason().detail()).contains("of it");
    }

    @Test
    void aSmallBlendPassesTheShareCheck() {
        assertThat(Rules.blendShareCheck(caseOf(scratch())).orElseThrow().passed())
                .isTrue();
    }

    @Test
    void noBlendShareIsCheckedWhereGrindingIsNotAllowed() {
        assertThat(Rules.blendShareCheck(caseOf(dent()))).isEmpty();
    }

    private static Finding strike(int pits, boolean onAFastener) {
        return Sample.cardWith()
                .kind(DamageKind.LIGHTNING_BURN)
                .geometry(new LightningBurn(inch("1.000"), inch("1.000"), pits, inch("0.004"), onAFastener, true))
                .at(Sample.cabinPointOn(10, "4.000"))
                .origin(DamageOrigin.LIGHTNING)
                .build();
    }

    @Test
    void aStrikeOnTheOpenSkinWithAFewPitsGetsPastTheAttachmentRule() {
        Check checked = Rules.strikeCheck(caseOf(strike(4, false))).orElseThrow();
        assertThat(checked.passed()).isTrue();
        assertThat(checked.reason().detail()).isEqualTo("4 pits on the open skin");
    }

    @Test
    void aStrikeThatAttachedOnAFastenerTakesTheFastenerOut() {
        Check checked = Rules.strikeCheck(caseOf(strike(2, true))).orElseThrow();
        assertThat(checked.passed()).isFalse();
        assertThat(checked.outcome()).isSameAs(Disposition.REPAIRABLE);
        assertThat(checked.reason().detail()).contains("checked for arcing");
    }

    @Test
    void aSweptStrokeIsBlendedAndLookedAtAsOneZone() {
        Check swept = Rules.strikeCheck(caseOf(strike(LightningBurn.SWEPT_STROKE_PITS, false)))
                .orElseThrow();
        assertThat(swept.passed()).isFalse();
        assertThat(swept.outcome()).isSameAs(Disposition.REWORKABLE);
        assertThat(swept.reason().detail()).isEqualTo("a swept stroke of 12 pits, blended and looked at as one zone");
        assertThat(Rules.strikeCheck(caseOf(strike(LightningBurn.SWEPT_STROKE_PITS - 1, false)))
                        .orElseThrow()
                        .passed())
                .isTrue();
    }

    @Test
    void theAttachmentRuleStandsAsideForAnythingButAStrike() {
        assertThat(Rules.strikeCheck(caseOf(scratch()))).isEmpty();
        assertThat(Rules.strikeCheck(caseOf(dent()))).isEmpty();
    }

    private static Finding crackWithTips(int tips) {
        return Sample.cardWith()
                .kind(DamageKind.CRACK)
                .geometry(new Crack(inch("0.600"), Angle.ofDegrees(90), true, false, tips))
                .at(Sample.cabinPointOn(10, "4.000"))
                .origin(DamageOrigin.FATIGUE)
                .build();
    }

    @Test
    void aCrackWithBothTipsFoundGetsPastTheTipsRule() {
        Check checked = Rules.crackTipsCheck(caseOf(crackWithTips(2))).orElseThrow();
        assertThat(checked.passed()).isTrue();
        assertThat(checked.reason().detail()).isEqualTo("both tips found");
    }

    @Test
    void aCrackWithATipNobodyFoundGoesToTheManufacturer() {
        for (int tips : new int[] {0, 1}) {
            Check checked = Rules.crackTipsCheck(caseOf(crackWithTips(tips))).orElseThrow();
            assertThat(checked.passed()).isFalse();
            assertThat(checked.outcome()).isSameAs(Disposition.OUT_OF_LIMITS);
            assertThat(checked.reason().detail())
                    .isEqualTo("only " + tips + " of 2 tips found, so 0.600 is a lower bound");
        }
    }

    @Test
    void theTipsRuleStandsAsideForAnythingButACrack() {
        assertThat(Rules.crackTipsCheck(caseOf(scratch()))).isEmpty();
    }

    private static Case dentOff(JointMember member, String distance) {
        Finding finding = dent("0.020", "3.000", "2.000");
        Placement placement =
                Placement.place(Sample.tailContext(), finding).withMember(new Margin(member, inch(distance)));
        return new Case(
                finding,
                placement,
                LimitSet.allowable(),
                Cleanups.standardRules().forKind(finding.kind()));
    }

    @Test
    void aDentReachingOverAFrameIsNotLeftAsItStands() {
        Check checked =
                Rules.overTheMemberCheck(dentOff(new Frame(42), "1.500")).orElseThrow();
        assertThat(checked.passed()).isFalse();
        assertThat(checked.outcome()).isSameAs(Disposition.REPAIRABLE);
        assertThat(checked.reason().detail()).isEqualTo("reaches over FR42, which took the blow under the skin");
    }

    @Test
    void aFrameIsReachedAlongTheBodyAndAStringerRoundIt() {
        assertThat(Rules.overTheMemberCheck(dentOff(new Frame(42), "1.501"))
                        .orElseThrow()
                        .passed())
                .isTrue();
        assertThat(Rules.overTheMemberCheck(dentOff(new Stringer(3, Side.LEFT), "1.500"))
                        .orElseThrow()
                        .passed())
                .isTrue();
        Check reached = Rules.overTheMemberCheck(dentOff(new Stringer(3, Side.LEFT), "1.000"))
                .orElseThrow();
        assertThat(reached.passed()).isFalse();
        assertThat(reached.reason().detail()).startsWith("reaches over S-3L");
    }

    @Test
    void theMemberRuleStandsAsideForAnythingButADentAndWhereNoMemberWasResolved() {
        assertThat(Rules.overTheMemberCheck(caseOf(scratch()))).isEmpty();
        Finding onTheWing = Sample.cardWith()
                .geometry(new Dent(inch("0.020"), inch("3.000"), inch("2.000")))
                .at(Sample.lowerWingPoint())
                .build();
        assertThat(Rules.overTheMemberCheck(caseOf(onTheWing))).isEmpty();
    }

    @Test
    void everyCheckTakesACaseAndAnswersOrStandsAside() {
        Case walked = caseOf(dent(), LimitSet.allowable().withMaxDepth(inch("0.060")));
        for (Function<Case, Optional<Check>> check : Rules.CHECKS) {
            Optional<Check> answer = check.apply(walked);
            assertThat(answer.map(one -> !one.reason().rule().isEmpty()).orElse(true))
                    .isTrue();
        }
    }
}
