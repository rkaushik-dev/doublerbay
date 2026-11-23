package io.github.rkaushikdev.doublerbay.repair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.grid.Point;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Material;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SchemeTest {

    private static final Reference PAGE = Reference.parse("53-40-01");

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Placement placed(Point at) {
        return Placement.place(Sample.tailContext(), Sample.card(at));
    }

    private static Placement placed() {
        return placed(Sample.cabinPointOn(10));
    }

    private static RepairScheme scheme(Point at, boolean temporary, String along, String around) {
        return Schemes.selectScheme(inch(along), inch(around), placed(at), PAGE, new FastenerSize(5), temporary);
    }

    private static RepairScheme scheme(Point at) {
        return scheme(at, false, "2.000", "2.000");
    }

    private static RepairScheme scheme(boolean temporary) {
        return scheme(Sample.cabinPointOn(10), temporary, "2.000", "2.000");
    }

    private static RepairScheme scheme(String along, String around) {
        return scheme(Sample.cabinPointOn(10), false, along, around);
    }

    private static RepairScheme scheme() {
        return scheme(false);
    }

    private static DoublerPlan doubler() {
        return Doublers.sizeDoubler(
                inch("2"), inch("2"), inch("0.040"), Material.lookup("2024-T3 CLAD"), new FastenerSize(5));
    }

    private static Placement onTheWing() {
        return Placement.place(Sample.tailContext(), Sample.card(Sample.lowerWingPoint()));
    }

    @Test
    void theCategoriesSayWhatTheyCommitTheOperatorTo() {
        assertThat(RepairCategory.A.needsInspection()).isFalse();
        assertThat(RepairCategory.B.needsInspection()).isTrue();
        assertThat(RepairCategory.C.needsInspection()).isTrue();
        assertThat(RepairCategory.A.isPermanent()).isTrue();
        assertThat(RepairCategory.B.isPermanent()).isTrue();
        assertThat(RepairCategory.C.isPermanent()).isFalse();
        assertThat(RepairCategory.B).hasToString("category B");
    }

    @Test
    void onlyAnExternalPatchStandsInTheAirflow() {
        assertThat(RepairForm.EXTERNAL.isInTheAirflow()).isTrue();
        assertThat(RepairForm.FLUSH.isInTheAirflow()).isFalse();
        assertThat(RepairForm.INTERNAL.isInTheAirflow()).isFalse();
    }

    @Test
    void ordinaryBarrelSkinTakesAnExternalPatch() {
        assertThat(Schemes.formFor(placed())).isSameAs(RepairForm.EXTERNAL);
    }

    @Test
    void theCrownAndTheWingWillNotHaveOneStandingProud() {
        Placement crown = placed(Sample.cabinPointOn(2));
        Placement wing = onTheWing();
        assertThat(Schemes.formFor(crown)).isSameAs(RepairForm.FLUSH);
        assertThat(Schemes.formFor(wing)).isSameAs(RepairForm.FLUSH);
    }

    @Test
    void aFlushRepairCarriesAFillerToMakeUpTheSkinLine() {
        RepairScheme crown = scheme(Sample.cabinPointOn(2));
        assertThat(crown.form()).isSameAs(RepairForm.FLUSH);
        assertThat(crown.filler()).isPresent();
    }

    @Test
    void anExternalRepairCarriesNoFiller() {
        assertThat(scheme().filler()).isEmpty();
    }

    @Test
    void aFlushRepairWithNoFillerIsRefused() {
        assertThatThrownBy(() ->
                        new RepairScheme(RepairForm.FLUSH, RepairCategory.A, doubler(), true, PAGE, Optional.empty()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("needs a filler");
    }

    @Test
    void aFillerWithNoThicknessIsRefused() {
        assertThatThrownBy(() -> new RepairScheme(
                        RepairForm.EXTERNAL, RepairCategory.A, doubler(), true, PAGE, Optional.of(inch("0"))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a filler has a thickness");
    }

    @Test
    void ordinaryOpenSkinIsAPermanentRepair() {
        assertThat(Schemes.categoryFor(placed())).isSameAs(RepairCategory.A);
        assertThat(scheme().needsInspection()).isFalse();
    }

    @Test
    void theTensionSurfaceOfAWingIsAlwaysInspected() {
        assertThat(Schemes.categoryFor(onTheWing())).isSameAs(RepairCategory.B);
    }

    @Test
    void aRepairOnAFastenerRowIsAlwaysInspected() {
        Placement onARow = placed(Sample.cabinPointOn(8, "0.500"));
        assertThat(Schemes.categoryFor(onARow)).isSameAs(RepairCategory.B);
    }

    @Test
    void aStopgapIsTemporaryWhateverItIsOn() {
        assertThat(Schemes.categoryFor(placed(), true)).isSameAs(RepairCategory.C);
        assertThat(scheme(true).category()).isSameAs(RepairCategory.C);
    }

    @Test
    void aRepairThroughThePressureBoundaryIsSealed() {
        assertThat(Schemes.needsSealing(placed())).isTrue();
        assertThat(scheme().sealed()).isTrue();
    }

    @Test
    void aSmallHoleThroughThePressureBoundaryStillGetsTwoRows() {
        assertThat(Schemes.leastRowsFor(placed())).isEqualTo(Schemes.PRESSURISED_ROWS);
        RepairScheme small = scheme("0.250", "0.250");
        assertThat(small.doubler().rowsEachSide()).isEqualTo(2);
        Placement tailCone = placed(Sample.cabinPointAtFrame(50, 10));
        assertThat(Schemes.leastRowsFor(tailCone)).isEqualTo(1);
        assertThat(scheme(Sample.cabinPointAtFrame(50, 10), false, "0.250", "0.250")
                        .doubler()
                        .rowsEachSide())
                .isEqualTo(1);
    }

    @Test
    void aDoublerReachingTheNearestStringerNamesIt() {
        RepairScheme made = scheme();
        assertThat(Schemes.memberUnder(made.doubler(), placed()))
                .hasValueSatisfying(member -> assertThat(member).hasToString("S-10L"));
    }

    @Test
    void aDoublerClearOfEveryMemberNamesNone() {
        Placement between = placed(Sample.cabinPointOn(10, "4.000"));
        RepairScheme made = scheme(Sample.cabinPointOn(10, "4.000"), false, "2.000", "2.000");
        assertThat(made.doubler().around().isLessThan(Sample.inch("8.000"))).isTrue();
        assertThat(Schemes.memberUnder(made.doubler(), between)).isEmpty();
        assertThat(Schemes.memberUnder(made.doubler(), onTheWing())).isEmpty();
    }

    @Test
    void aRepairOnTheTailConeIsNotSealed() {
        Placement tailCone = placed(Sample.cabinPointAtFrame(50, 10));
        assertThat(Schemes.needsSealing(tailCone)).isFalse();
        assertThat(scheme(Sample.cabinPointAtFrame(50, 10)).sealed()).isFalse();
    }

    @Test
    void theSchemeCarriesTheDoublerItSized() {
        RepairScheme made = scheme("2.000", "2.000");
        assertThat(made.doubler().along().isGreaterThan(inch("2.000"))).isTrue();
        assertThat(made.fastenerCount()).isEqualTo(made.doubler().fastenerCount());
    }

    @Test
    void aBiggerHoleTakesMoreFasteners() {
        RepairScheme small = scheme("1.000", "1.000");
        RepairScheme large = scheme("6.000", "6.000");
        assertThat(large.fastenerCount()).isGreaterThan(small.fastenerCount());
    }

    @Test
    void theSchemeReadsBackAsARepairLine() {
        String printed = scheme().toString();
        assertThat(printed).startsWith("external doubler, category A");
        assertThat(printed).endsWith("sealed");
    }

    @Test
    void aWingRepairReadsBackFlushAndInspected() {
        RepairScheme wing =
                Schemes.selectScheme(inch("2.000"), inch("2.000"), onTheWing(), Reference.parse("57-40-01"));
        String printed = wing.toString();
        assertThat(printed).contains("flush doubler");
        assertThat(printed).contains("category B");
    }
}
