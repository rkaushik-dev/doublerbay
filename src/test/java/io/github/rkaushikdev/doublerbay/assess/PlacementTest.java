package io.github.rkaushikdev.doublerbay.assess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Chapter;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.Region;
import io.github.rkaushikdev.doublerbay.support.Sample;
import org.junit.jupiter.api.Test;

class PlacementTest {

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    /** Place a card at a cabin point, clear of the window line unless asked. */
    private static Placement placed(int stringer, String down) {
        return Placement.place(Sample.tailContext(), Sample.card(Sample.cabinPointOn(stringer, down)));
    }

    @Test
    void aCabinCardPlacesOntoPressurisedSkin() {
        Placement found = Placement.place(Sample.tailContext(), Sample.card());
        assertThat(found.panelKind()).isSameAs(PanelKind.PRESSURISED_SKIN);
        assertThat(found.chapter()).isSameAs(Chapter.FUSELAGE);
        assertThat(found.isOnTheWing()).isFalse();
    }

    @Test
    void aCabinCardKeepsTheNearestMemberItWasMeasuredOff() {
        Placement onTheLine = placed(6, "0.000");
        assertThat(onTheLine.member()).isPresent();
        assertThat(onTheLine.member().get().distance()).isEqualTo(new Length(0));
        assertThat(onTheLine.reachesTheMember(inch("1.000"), inch("0.002"))).isTrue();
        Placement offTheLine = placed(6, "2.000");
        assertThat(offTheLine.reachesTheMember(inch("1.000"), inch("3.999"))).isFalse();
        assertThat(offTheLine.reachesTheMember(inch("1.000"), inch("4.000"))).isTrue();
    }

    @Test
    void aWingCardHasNoBarrelMemberToReach() {
        Placement wing = Placement.place(Sample.tailContext(), Sample.card(Sample.lowerWingPoint()));
        assertThat(wing.member()).isEmpty();
        assertThat(wing.reachesTheMember(inch("40.000"), inch("40.000"))).isFalse();
    }

    @Test
    void theThicknessIsReadAtTheDamageAndNotOffTheDrawing() {
        Placement onTheLand = placed(10, "0.000");
        Placement inTheBay = placed(10, "4.000");
        assertThat(onTheLand.thickness().isGreaterThan(inTheBay.thickness())).isTrue();
    }

    @Test
    void theRegionComesOffTheSameResolvedPointAsTheThickness() {
        Placement onTheLand = placed(10, "0.000");
        assertThat(onTheLand.region()).isSameAs(Region.LAND);
        Placement inTheBay = placed(10, "4.000");
        assertThat(inTheBay.region()).isIn(Region.OPEN_FIELD, Region.CHEM_MILL_STEP);
    }

    @Test
    void aCardOnALapJointPlacesOntoItsRowsAndNamesTheFastener() {
        FuselagePoint onTheJoint = Sample.cabinPointOn(8, "0.500");
        Placement found = Placement.place(Sample.tailContext(), Sample.card(onTheJoint));
        assertThat(found.region()).isSameAs(Region.FASTENER_ROW);
        assertThat(found.nearestFastener()).contains(new FastenerSize(5));
    }

    @Test
    void aCardAwayFromAJointNamesNoFastener() {
        Placement found = placed(10, "4.000");
        assertThat(found.nearestFastener()).isEmpty();
    }

    @Test
    void aCardByAWindowPlacesIntoItsKeepOut() {
        FuselagePoint byTheWindow = Sample.cabinPoint(20, "16.000", 7, "0.000");
        Placement found = Placement.place(Sample.tailContext(), Sample.card(byTheWindow));
        assertThat(found.region()).isIn(Region.CUTOUT_EDGE, Region.CUTOUT_CORNER);
    }

    @Test
    void theTailConePlacesOntoUnpressurisedSkin() {
        Placement found = Placement.place(Sample.tailContext(), Sample.card(Sample.cabinPointAtFrame(50)));
        assertThat(found.panelKind()).isSameAs(PanelKind.UNPRESSURISED_SKIN);
    }

    @Test
    void aWingCardPlacesOntoTheLowerSurface() {
        Placement found = Placement.place(Sample.tailContext(), Sample.card(Sample.lowerWingPoint()));
        assertThat(found.panelKind()).isSameAs(PanelKind.WING_LOWER);
        assertThat(found.chapter()).isSameAs(Chapter.WINGS);
        assertThat(found.isOnTheWing()).isTrue();
    }

    @Test
    void aWingCardReadsThePlateGaugeAtTheDamage() {
        Placement root = Placement.place(Sample.tailContext(), Sample.card(Sample.lowerWingPoint(2)));
        Placement outboard = Placement.place(Sample.tailContext(), Sample.card(Sample.lowerWingPoint(22)));
        assertThat(root.thickness().isGreaterThan(outboard.thickness())).isTrue();
    }

    @Test
    void aWingCardOnTheLandReadsTheFullPlate() {
        Placement onLand =
                Placement.place(Sample.tailContext(), Sample.card(Sample.lowerWingPoint(2, "1.000", "20.000")));
        assertThat(onLand.region()).isSameAs(Region.LAND);
        assertThat(onLand.thickness()).isEqualTo(inch("0.250"));
    }

    @Test
    void aWingCardNamesNoFastenerBecauseTheBoxCarriesNoJoints() {
        Placement found = Placement.place(Sample.tailContext(), Sample.card(Sample.lowerWingPoint()));
        assertThat(found.nearestFastener()).isEmpty();
    }

    @Test
    void thePanelExtentIsTheLongerSideOfThePanel() {
        Placement found = Placement.place(Sample.tailContext(), Sample.card());
        assertThat(found.panelExtent().mils()).isPositive();
        assertThat(found.panelExtent().isAtLeast(inch("30.000"))).isTrue();
    }

    @Test
    void theMaterialComesOffThePanelTheDamageIsOn() {
        Placement barrel = Placement.place(Sample.tailContext(), Sample.card());
        Placement wing = Placement.place(Sample.tailContext(), Sample.card(Sample.lowerWingPoint()));
        assertThat(barrel.material().clad()).isTrue();
        assertThat(wing.material().alloy()).isEqualTo("2024");
    }

    @Test
    void aCardRaisedOnAnotherTailIsRefused() {
        assertThatThrownBy(() -> Placement.place(Sample.tailContext(), Sample.card(1, "5240")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not a card on");
    }

    @Test
    void thePlacementReadsBackThePartRegionAndThickness() {
        String printed = Placement.place(Sample.tailContext(), Sample.card()).toString();
        assertThat(printed).startsWith("5310-");
        assertThat(printed).contains("thick");
    }

    @Test
    void theEdgeMarginIsTheGoverningOneAndNotTheMemberMargin() {
        Placement found = placed(10, "0.000");
        assertThat(found.edgeMargin().mils()).isNotNegative();
    }
}
