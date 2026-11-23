package io.github.rkaushikdev.doublerbay.assess;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.JointMember;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.manual.LimitRow;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.FastenerRow;
import io.github.rkaushikdev.doublerbay.structure.Joint;
import io.github.rkaushikdev.doublerbay.structure.JointKind;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssessJointsTest {

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static FastenerRow row(String offset) {
        return new FastenerRow(new FastenerSize(5), inch("1.000"), inch(offset));
    }

    private static Joint joint(JointKind kind, List<String> offsets, boolean longitudinal) {
        return new Joint(
                kind,
                member(longitudinal),
                offsets.stream().map(AssessJointsTest::row).toList());
    }

    private static Joint joint() {
        return joint(JointKind.LAP, List.of("0.500"), true);
    }

    /** A joint follows a stringer along the body or a frame around it. */
    private static JointMember member(boolean longitudinal) {
        return longitudinal ? new Stringer(6, Side.LEFT) : new Frame(15);
    }

    private static Crack crack(int bearing) {
        return new Crack(inch("0.400"), new Angle(bearing));
    }

    private static Crack crack() {
        return crack(0);
    }

    private static Finding card(Geometry geometry, DamageKind kind, DamageOrigin origin) {
        return Sample.cardWith()
                .sequence(1)
                .kind(kind)
                .geometry(geometry != null ? geometry : crack())
                .at(Sample.cabinPointOn(10))
                .origin(origin)
                .build();
    }

    private static Finding card(Geometry geometry) {
        return card(geometry, DamageKind.CRACK, DamageOrigin.FATIGUE);
    }

    private static Finding card() {
        return card(null);
    }

    /** The same placement with the fastener named, or with none when the dash is nought. */
    private static Placement withFastener(Placement placement, int dash) {
        Optional<FastenerSize> size = dash != 0 ? Optional.of(new FastenerSize(dash)) : Optional.empty();
        return new Placement(
                placement.part(),
                placement.panelKind(),
                placement.chapter(),
                placement.material(),
                placement.region(),
                placement.thickness(),
                placement.edgeMargin(),
                placement.panelExtent(),
                placement.centre(),
                size,
                placement.joint(),
                placement.member());
    }

    private static Case caseOf(Geometry geometry, String margin, int dash, DamageKind kind, DamageOrigin origin) {
        Finding finding = card(geometry, kind, origin);
        Placement placement = Placement.place(Sample.tailContext(), finding);
        if (margin != null) {
            placement = placement.withEdgeMargin(inch(margin));
        }
        placement = withFastener(placement, dash);
        Book book = Book.v320();
        LimitRow found = book.limitsFor(
                placement.chapter(),
                Sample.DAY,
                Sample.aircraft(),
                finding.kind(),
                placement.panelKind(),
                placement.region());
        return new Case(finding, placement, found.limits(), book.cleanup().forKind(finding.kind()));
    }

    private static Case caseOf(String margin) {
        return caseOf(null, margin, 5, DamageKind.CRACK, DamageOrigin.FATIGUE);
    }

    private static JointCase held(String margin, Geometry geometry, int dash) {
        return new JointCase(caseOf(geometry, margin, dash, DamageKind.CRACK, DamageOrigin.FATIGUE), joint());
    }

    private static JointCase held(String margin, Geometry geometry) {
        return held(margin, geometry, 5);
    }

    private static JointCase held(String margin) {
        return held(margin, null);
    }

    private static JointCase held() {
        return held("0.100");
    }

    private static Finding aDent() {
        return Sample.card(1, DamageKind.DENT, new Dent(inch("0.020"), inch("3.000"), inch("2.000")));
    }

    @Test
    void aCrackHasABearing() {
        assertThat(Joints.bearingOf(card())).isPresent();
    }

    @Test
    void aDentHasNoBearing() {
        assertThat(Joints.bearingOf(aDent())).isEmpty();
    }

    @Test
    void aCrackAlongTheBodyRunsWithALongitudinalRow() {
        assertThat(Joints.runsAlong(card(crack(0)), joint())).contains(true);
    }

    @Test
    void aCrackAcrossTheBodyRunsAgainstALongitudinalRow() {
        assertThat(Joints.runsAlong(card(crack(900)), joint())).contains(false);
    }

    @Test
    void aCrackJustOffTheLineStillRunsAlongIt() {
        assertThat(Joints.runsAlong(card(crack(Joints.ALONG_WITHIN_DEGREES * 10)), joint()))
                .contains(true);
    }

    @Test
    void aCrackPastTheToleranceDoesNotRunAlongIt() {
        assertThat(Joints.runsAlong(card(crack(Joints.ALONG_WITHIN_DEGREES * 10 + 10)), joint()))
                .contains(false);
    }

    @Test
    void aCrackAtTheFarEndOfTheLineStillRunsAlongIt() {
        assertThat(Joints.runsAlong(card(crack(1800)), joint())).contains(true);
    }

    @Test
    void aCrackAcrossACircumferentialRowRunsAlongIt() {
        Joint across = joint(JointKind.BUTT, List.of("0.500"), false);
        assertThat(Joints.runsAlong(card(crack(900)), across)).contains(true);
    }

    @Test
    void aDamageWithNoDirectionSaysNothing() {
        assertThat(Joints.runsAlong(aDent(), joint())).isEmpty();
    }

    @Test
    void aDamageCloseToTheRowIsInIt() {
        assertThat(held("0.400").isInTheRow()).isTrue();
    }

    @Test
    void aDamageFarFromTheRowIsNotInIt() {
        assertThat(held("8.000").isInTheRow()).isFalse();
    }

    @Test
    void aDamageWithNoKnownFastenerIsNeverInTheRow() {
        assertThat(held("0.400", null, 0).isInTheRow()).isFalse();
    }

    @Test
    void theThresholdIsInFastenerDiameters() {
        assertThat(Joints.IN_THE_ROW).isEqualByComparingTo("2");
    }

    @Test
    void aJointCaseReadsBackWithItsJoint() {
        assertThat(held().toString()).startsWith("DR-2025-000001 on ");
    }

    @Test
    void nothingAppliesWhenTheDamageIsClearOfTheRow() {
        assertThat(Joints.insideMarginCheck(held("8.000"))).isEmpty();
        assertThat(Joints.alongTheRowCheck(held("8.000"))).isEmpty();
        assertThat(Joints.fatigueCriticalCheck(held("8.000"))).isEmpty();
    }

    @Test
    void aDamageInsideTheMarginFailsTheMarginRule() {
        Check answer = Joints.insideMarginCheck(held("0.400")).orElseThrow();
        assertThat(answer.passed()).isFalse();
        assertThat(answer.outcome()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void aDamageOutsideTheMarginPassesIt() {
        JointCase made = new JointCase(caseOf("0.100"), joint(JointKind.LAP, List.of("0.200"), true));
        Optional<Check> answer = Joints.insideMarginCheck(made);
        assertThat(answer).isPresent();
        assertThat(answer.get().passed()).isTrue();
    }

    @Test
    void aCrackAlongTheRowFailsThatRule() {
        Check answer = Joints.alongTheRowCheck(held("0.400", crack(0))).orElseThrow();
        assertThat(answer.passed()).isFalse();
        assertThat(answer.toString()).contains("runs with the rivet line");
    }

    @Test
    void aCrackAcrossTheRowPassesIt() {
        Check answer = Joints.alongTheRowCheck(held("0.400", crack(900))).orElseThrow();
        assertThat(answer.passed()).isTrue();
    }

    @Test
    void aDamageWithNoDirectionGetsNoAlongRule() {
        JointCase made = new JointCase(
                caseOf(
                        new Dent(inch("0.020"), inch("3.000"), inch("2.000")),
                        "0.400",
                        5,
                        DamageKind.DENT,
                        DamageOrigin.IMPACT),
                joint());
        assertThat(Joints.alongTheRowCheck(made)).isEmpty();
    }

    @Test
    void aLapJointIsFatigueCritical() {
        Check answer = Joints.fatigueCriticalCheck(held("0.400")).orElseThrow();
        assertThat(answer.passed()).isFalse();
        assertThat(answer.outcome()).isSameAs(Disposition.REWORKABLE);
    }

    @Test
    void aButtJointIsNotFatigueCritical() {
        JointCase made = new JointCase(caseOf("0.400"), joint(JointKind.BUTT, List.of("0.500"), false));
        assertThat(Joints.fatigueCriticalCheck(made).orElseThrow().passed()).isTrue();
    }

    @Test
    void noJointMeansNoJointRules() {
        assertThat(Joints.checksFor(caseOf("0.400"), Optional.empty())).isEmpty();
    }

    @Test
    void aDamageInARowCollectsEveryRule() {
        assertThat(Joints.checksFor(caseOf("0.400"), joint())).hasSize(3);
    }

    @Test
    void aDamageClearOfTheRowCollectsNone() {
        assertThat(Joints.checksFor(caseOf("8.000"), joint())).isEmpty();
    }

    private static JointCase onABond(String margin) {
        Joint bonded = new Joint(JointKind.BONDED, new Stringer(6, Side.LEFT), List.of());
        return new JointCase(caseOf(null, margin, 0, DamageKind.CRACK, DamageOrigin.FATIGUE), bonded);
    }

    @Test
    void aDamageOnTheBondLineOfABondedJointGoesToTheManufacturer() {
        Check checked = Joints.bondLineCheck(onABond("0.750")).orElseThrow();
        assertThat(checked.passed()).isFalse();
        assertThat(checked.outcome()).isSameAs(Disposition.OUT_OF_LIMITS);
        assertThat(checked.reason().detail()).isEqualTo("on the bond line of the bonded joint, 0.750 in off S-6L");
        assertThat(Joints.bondLineCheck(onABond("1.000")).orElseThrow().passed())
                .isFalse();
    }

    @Test
    void aDamageClearOfTheBondLineGetsPastTheBondRule() {
        Check checked = Joints.bondLineCheck(onABond("1.001")).orElseThrow();
        assertThat(checked.passed()).isTrue();
        assertThat(checked.reason().detail()).startsWith("clear of the bond line");
    }

    @Test
    void theBondRuleStandsAsideOnARivetedJointAndTheRowRulesOnABondedOne() {
        assertThat(Joints.bondLineCheck(held("0.500"))).isEmpty();
        List<Check> onTheBond =
                Joints.checksFor(onABond("0.500").subject(), onABond("0.500").joint());
        assertThat(onTheBond).hasSize(1);
        assertThat(onTheBond.get(0).reason().rule()).isEqualTo("bond line");
    }

    @Test
    void theRowRulesReadTheSameThroughTheSharedAnswers() {
        Check inside = Joints.insideMarginCheck(held("0.700")).orElseThrow();
        assertThat(inside.reason().rule()).isEqualTo("joint edge margin");
        assertThat(inside.reason().detail()).startsWith("inside the fastener row, ");
        Check fatigue = Joints.fatigueCriticalCheck(held("0.400")).orElseThrow();
        assertThat(fatigue.passed()).isFalse();
        assertThat(fatigue.outcome()).isSameAs(Disposition.REWORKABLE);
        assertThat(fatigue.reason().detail()).isEqualTo("lap joint carries the pressurisation cycle");
    }
}
