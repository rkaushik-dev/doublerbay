package io.github.rkaushikdev.doublerbay.repair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Material;
import org.junit.jupiter.api.Test;

class DoublerTest {

    private static final Length SKIN = Length.ofInches("0.040");
    private static final Material CLAD = Material.lookup("2024-T3 CLAD");

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static DoublerPlan plan(String along, String around, String skin, int dash) {
        return Doublers.sizeDoubler(inch(along), inch(around), inch(skin), CLAD, new FastenerSize(dash));
    }

    private static DoublerPlan plan(String skin) {
        return plan("2.000", "2.000", skin, 5);
    }

    private static DoublerPlan plan(int dash) {
        return plan("2.000", "2.000", "0.040", dash);
    }

    private static DoublerPlan plan() {
        return plan(5);
    }

    private static DoublerPlan laidOut(int rowsEachSide, int perRow) {
        return new DoublerPlan(
                inch("0.050"),
                CLAD,
                inch("3"),
                inch("3"),
                new FastenerSize(5),
                rowsEachSide,
                perRow,
                inch("0.625"),
                inch("0.625"),
                inch("0.391"));
    }

    @Test
    void theShearAllowableComesOffTheStandardPractices() {
        assertThat(Doublers.shearAllowable(new FastenerSize(5))).isEqualTo(596);
        assertThat(Doublers.shearAllowable(new FastenerSize(8)))
                .isGreaterThan(Doublers.shearAllowable(new FastenerSize(5)));
    }

    @Test
    void aFastenerWithNoAllowableOnFileIsRefused() {
        assertThatThrownBy(() -> Doublers.shearAllowable(new FastenerSize(7)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no shear allowable on file");
    }

    @Test
    void theCarriedLoadIsTheStripAreaTimesTheUltimate() {
        assertThat(Doublers.carriedLoad(inch("0.040"), inch("2.000"), CLAD)).isEqualTo(5120);
    }

    @Test
    void aThickerSkinOverTheSameWidthCarriesMore() {
        int thin = Doublers.carriedLoad(inch("0.040"), inch("2.000"), CLAD);
        int thick = Doublers.carriedLoad(inch("0.080"), inch("2.000"), CLAD);
        assertThat(thick).isEqualTo(thin * 2);
    }

    @Test
    void aStrongerAlloyOverTheSameStripCarriesMore() {
        int tough = Doublers.carriedLoad(inch("0.040"), inch("2.000"), Material.lookup("2024-T3 CLAD"));
        int strong = Doublers.carriedLoad(inch("0.040"), inch("2.000"), Material.lookup("7075-T6"));
        assertThat(strong).isGreaterThan(tough);
    }

    @Test
    void aStripWithNoSizeCarriesNothingAndIsRefused() {
        assertThatThrownBy(() -> Doublers.carriedLoad(inch("0"), inch("2.000"), CLAD))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a thickness and a width");
    }

    @Test
    void theFastenerCountIsTheLoadOverWhatOneWillTake() {
        assertThat(Doublers.fastenersEachSide(inch("0.040"), inch("2.000"), CLAD, new FastenerSize(5)))
                .isEqualTo(9);
    }

    @Test
    void aFastenerBearsOnTheSheetAtOneAndAHalfTimesItsUltimate() {
        assertThat(CLAD.bearingKsi()).isEqualTo(96);
        assertThat(Material.lookup("7075-T6").bearingKsi()).isEqualTo(124);
        assertThat(Doublers.bearingAllowable(new FastenerSize(5), inch("0.040"), CLAD))
                .isEqualTo(599);
        assertThat(Doublers.bearingAllowable(new FastenerSize(5), inch("0.032"), CLAD))
                .isEqualTo(479);
    }

    @Test
    void theFastenerCarriesTheLesserOfShearAndBearing() {
        assertThat(Doublers.fastenerAllowable(new FastenerSize(5), inch("0.040"), CLAD))
                .isEqualTo(Doublers.shearAllowable(new FastenerSize(5)));
        assertThat(Doublers.fastenerAllowable(new FastenerSize(5), inch("0.032"), CLAD))
                .isEqualTo(479);
        assertThat(Doublers.fastenerAllowable(new FastenerSize(8), inch("0.040"), CLAD))
                .isEqualTo(960);
    }

    @Test
    void thinSkinIsCountedOffBearingAndTakesMoreFasteners() {
        assertThat(Doublers.carriedLoad(inch("0.032"), inch("2.000"), CLAD)).isEqualTo(4096);
        assertThat(Math.ceilDiv(4096, Doublers.shearAllowable(new FastenerSize(5))))
                .isEqualTo(7);
        assertThat(Doublers.fastenersEachSide(inch("0.032"), inch("2.000"), CLAD, new FastenerSize(5)))
                .isEqualTo(9);
    }

    @Test
    void aSheetWithNoThicknessBearsNothing() {
        assertThatThrownBy(() -> Doublers.bearingAllowable(new FastenerSize(5), inch("0"), CLAD))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("bears on a sheet with a thickness");
    }

    @Test
    void aBiggerFastenerNeedsFewerOfThem() {
        int small = Doublers.fastenersEachSide(inch("0.040"), inch("2.000"), CLAD, new FastenerSize(4));
        int large = Doublers.fastenersEachSide(inch("0.040"), inch("2.000"), CLAD, new FastenerSize(8));
        assertThat(large).isLessThan(small);
    }

    @Test
    void evenATrivialLoadTakesOneFastener() {
        assertThat(Doublers.fastenersEachSide(inch("0.001"), inch("0.100"), CLAD, new FastenerSize(8)))
                .isEqualTo(1);
    }

    @Test
    void theDoublerGoesOnOneGaugeHeavierThanTheSkin() {
        assertThat(plan("0.040").gauge()).isEqualTo(inch("0.050"));
        assertThat(plan("0.063").gauge()).isEqualTo(inch("0.071"));
    }

    @Test
    void theDoublerReachesPastTheHoleByItsSkirt() {
        DoublerPlan made = plan("2.000", "2.000", "0.040", 5);
        assertThat(made.along()).isEqualTo(inch("2.000").plus(made.skirt().times(2)));
        assertThat(made.around()).isEqualTo(inch("2.000").plus(made.skirt().times(2)));
    }

    @Test
    void theSkirtGrowsWithEveryExtraRow() {
        DoublerPlan oneRow = laidOut(1, 4);
        DoublerPlan twoRows = laidOut(2, 4);
        assertThat(twoRows.skirt().isGreaterThan(oneRow.skirt())).isTrue();
    }

    @Test
    void theSpacingsComeOffTheFastenerSize() {
        DoublerPlan made = plan(5);
        assertThat(made.pitch()).isEqualTo(new FastenerSize(5).minPitch());
        assertThat(made.rowSpacing()).isEqualTo(new FastenerSize(5).minRowSpacing());
        assertThat(made.edgeDistance()).isEqualTo(new FastenerSize(5).preferredEdgeDistance());
    }

    @Test
    void aBiggerFastenerSpreadsThePatternOut() {
        DoublerPlan small = plan(4);
        DoublerPlan large = plan(8);
        assertThat(large.pitch().isGreaterThan(small.pitch())).isTrue();
        assertThat(large.edgeDistance().isGreaterThan(small.edgeDistance())).isTrue();
    }

    @Test
    void theHoleGetsEnoughFastenersToCarryItsLoad() {
        DoublerPlan made = plan("2.000", "2.000", "0.040", 5);
        int needed = Doublers.fastenersEachSide(SKIN, inch("2.000"), CLAD, new FastenerSize(5));
        assertThat(made.rowsEachSide() * made.perRow()).isGreaterThanOrEqualTo(needed);
    }

    @Test
    void aHeavierSkinTakesMoreRowsOverTheSameHole() {
        DoublerPlan thin = plan("0.040");
        DoublerPlan thick = plan("0.190");
        assertThat(thick.rowsEachSide()).isGreaterThan(thin.rowsEachSide());
    }

    @Test
    void theFastenerCountIsThePatternOnFourSides() {
        DoublerPlan made = plan();
        assertThat(made.fastenerCount()).isEqualTo(made.rowsEachSide() * made.perRow() * 4);
    }

    @Test
    void aSmallHoleCarriesOneRowUnlessMoreAreAskedFor() {
        DoublerPlan bare = Doublers.sizeDoubler(inch("0.250"), inch("0.250"), SKIN, CLAD, new FastenerSize(5));
        DoublerPlan asked = Doublers.sizeDoubler(inch("0.250"), inch("0.250"), SKIN, CLAD, new FastenerSize(5), 2);
        assertThat(bare.rowsEachSide()).isEqualTo(1);
        assertThat(asked.rowsEachSide()).isEqualTo(2);
        assertThat(asked.perRow()).isEqualTo(bare.perRow());
        assertThat(asked.along())
                .isEqualTo(bare.along().plus(new FastenerSize(5).minRowSpacing().times(2)));
    }

    @Test
    void askingForMoreRowsThanTheLoadNeedsNeverTakesAnyAway() {
        DoublerPlan loaded = plan("2.000", "2.000", "0.040", 5);
        DoublerPlan asked = Doublers.sizeDoubler(inch("2.000"), inch("2.000"), SKIN, CLAD, new FastenerSize(5), 2);
        assertThat(asked).isEqualTo(loaded);
        assertThatThrownBy(() -> Doublers.sizeDoubler(inch("2.000"), inch("2.000"), SKIN, CLAD, new FastenerSize(5), 0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least one row");
    }

    @Test
    void aLaidOutPlanTakesItsSpacingsOffTheFastener() {
        DoublerPlan made =
                DoublerPlan.laidOut(inch("0.050"), CLAD, inch("1.000"), inch("1.000"), new FastenerSize(5), 2, 3);
        assertThat(made.pitch()).isEqualTo(new FastenerSize(5).minPitch());
        assertThat(made.skirt())
                .isEqualTo(new FastenerSize(5).preferredEdgeDistance().plus(made.rowSpacing()));
        assertThat(made.along()).isEqualTo(inch("1.000").plus(made.skirt().times(2)));
    }

    @Test
    void aPlateWeighsItsAreaTimesItsGaugeTimesItsDensity() {
        assertThat(laidOut(1, 4).weight()).isEqualByComparingTo("0.045");
        DoublerPlan heavy = new DoublerPlan(
                inch("0.100"),
                Material.lookup("7075-T6 CLAD"),
                inch("10"),
                inch("10"),
                new FastenerSize(5),
                2,
                4,
                inch("0.625"),
                inch("0.625"),
                inch("0.391"));
        assertThat(heavy.weight()).isEqualByComparingTo("1.010");
        assertThat(heavy.weight().scale()).isEqualTo(3);
    }

    @Test
    void aRowOfOneFastenerIsNotAJoint() {
        assertThatThrownBy(() -> laidOut(1, 1))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is a hinge, not a joint");
    }

    @Test
    void aDoublerWithNoRowsIsRefused() {
        assertThatThrownBy(() -> laidOut(0, 4)).isInstanceOf(BadReading.class).hasMessageContaining("at least one row");
    }

    @Test
    void aHoleWithNoSizeIsRefused() {
        assertThatThrownBy(() -> plan("0", "2.000", "0.040", 5))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("covers a hole with a size");
    }

    @Test
    void thePlanReadsBackAsAPartsLine() {
        String printed = plan().toString();
        assertThat(printed).startsWith("0.050 2024-T3 CLAD");
        assertThat(printed).contains("off -5");
    }
}
