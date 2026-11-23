package io.github.rkaushikdev.doublerbay.v320;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.manual.LimitRow;
import io.github.rkaushikdev.doublerbay.manual.LimitTable;
import io.github.rkaushikdev.doublerbay.manual.Page;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Ratio;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.Region;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class Manual53Test {

    private static final Length SKIN = Length.ofInches("0.063");

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static LimitRow lookUp(DamageKind kind, PanelKind panel, Region region, int revision) {
        return Manual53.allowableDamage(revision).lookup(kind, panel, region);
    }

    private static LimitRow lookUp(DamageKind kind, PanelKind panel, Region region) {
        return lookUp(kind, panel, region, 12);
    }

    private static LimitRow lookUp(DamageKind kind, PanelKind panel, int revision) {
        return lookUp(kind, panel, Region.OPEN_FIELD, revision);
    }

    private static LimitRow lookUp(DamageKind kind, PanelKind panel) {
        return lookUp(kind, panel, Region.OPEN_FIELD);
    }

    private static LimitRow lookUp(DamageKind kind, Region region) {
        return lookUp(kind, PanelKind.PRESSURISED_SKIN, region);
    }

    private static LimitRow lookUp(DamageKind kind, int revision) {
        return lookUp(kind, PanelKind.PRESSURISED_SKIN, revision);
    }

    private static LimitRow lookUp(DamageKind kind) {
        return lookUp(kind, PanelKind.PRESSURISED_SKIN);
    }

    private static Length depthOf(LimitRow found) {
        return found.limits().maxDepth().orElseThrow();
    }

    @Test
    void theChapterCarriesItsRevisionHistory() {
        assertThat(Manual53.manual().latest().number()).isEqualTo(13);
        assertThat(Manual53.manual().inForceOn(LocalDate.of(2024, 6, 1)).number())
                .isEqualTo(11);
        assertThat(Manual53.manual().inForceOn(LocalDate.of(2025, 1, 1)).number())
                .isEqualTo(12);
    }

    @Test
    void thePagesAreTheFourSubjectsTheDeskReads() {
        Set<String> titles =
                Manual53.PAGES.stream().map(page -> page.reference().toString()).collect(Collectors.toSet());
        assertThat(titles).isEqualTo(Set.of("53-10-01", "53-20-01", "53-30-01", "53-30-02", "53-40-01"));
    }

    @Test
    void theLightningPageArrivedARevisionAfterTheOthers() {
        Page lightning = Manual53.PAGES.stream()
                .filter(page -> page.reference().toString().equals("53-30-02"))
                .findFirst()
                .orElseThrow();
        assertThat(lightning.isLiveAt(9)).isFalse();
        assertThat(lightning.isLiveAt(10)).isTrue();
    }

    @Test
    void theTableIsBuiltOncePerRevisionAndShared() {
        assertThat(Manual53.allowableDamage(12)).isSameAs(Manual53.allowableDamage(12));
        assertThat(Manual53.allowableDamage(12)).isNotSameAs(Manual53.allowableDamage(11));
    }

    @Test
    void aDentOnOrdinaryPressurisedSkinGetsThePressurisedRow() {
        LimitRow found = lookUp(DamageKind.DENT);
        assertThat(found.limits().maxDepth()).contains(inch("0.060"));
        assertThat(found.limits().maxSharpness()).contains(new Ratio("0.05"));
    }

    @Test
    void aScoreAlongThePressurisedBarrelGetsHalfTheCap() {
        for (DamageKind kind : List.of(DamageKind.SCRATCH, DamageKind.GOUGE, DamageKind.NICK)) {
            LimitRow found = lookUp(kind);
            assertThat(found.limits().hoopLoadedShare()).contains(new Ratio("0.50"));
            assertThat(found.limits().depthCap(SKIN, true)).contains(new Length(2));
            assertThat(found.limits().depthCap(SKIN, false)).contains(new Length(3));
            assertThat(lookUp(kind, PanelKind.UNPRESSURISED_SKIN).limits().hoopLoadedShare())
                    .isEmpty();
        }
    }

    @Test
    void theTailConeGetsTheLoosestDentLimits() {
        LimitRow loose = lookUp(DamageKind.DENT, PanelKind.UNPRESSURISED_SKIN);
        LimitRow tight = lookUp(DamageKind.DENT);
        assertThat(depthOf(loose).isGreaterThan(depthOf(tight))).isTrue();
        assertThat(loose.limits().maxSharpness().orElseThrow())
                .isGreaterThan(tight.limits().maxSharpness().orElseThrow());
    }

    @Test
    void theCrownSitsBetweenTheTwo() {
        LimitRow crown = lookUp(DamageKind.DENT, PanelKind.CROWN_SKIN);
        assertThat(crown.limits().maxDepth()).contains(inch("0.080"));
    }

    @Test
    void theCampaignRevisionTightenedThePressurisedDentLimits() {
        LimitRow before = lookUp(DamageKind.DENT, Manual53.DENT_CAMPAIGN_REVISION - 1);
        LimitRow after = lookUp(DamageKind.DENT, Manual53.DENT_CAMPAIGN_REVISION);
        assertThat(before.limits().maxDepth()).contains(inch("0.080"));
        assertThat(after.limits().maxDepth()).contains(inch("0.060"));
    }

    @Test
    void theCampaignLeftTheOtherSkinsAlone() {
        LimitRow before = lookUp(DamageKind.DENT, PanelKind.CROWN_SKIN, 11);
        LimitRow after = lookUp(DamageKind.DENT, PanelKind.CROWN_SKIN, 12);
        assertThat(before.limits().maxDepth()).isEqualTo(after.limits().maxDepth());
    }

    @Test
    void nothingIsAllowableOnAFastenerRow() {
        for (DamageKind kind : List.of(
                DamageKind.DENT,
                DamageKind.SCRATCH,
                DamageKind.GOUGE,
                DamageKind.NICK,
                DamageKind.CORROSION,
                DamageKind.LIGHTNING_BURN)) {
            LimitRow found = lookUp(kind, Region.FASTENER_ROW);
            assertThat(found.limits().allowed()).isFalse();
        }
    }

    @Test
    void nothingIsAllowableInACutoutCornerOrOnAChemMillStep() {
        for (Region region : List.of(Region.CUTOUT_CORNER, Region.CHEM_MILL_STEP)) {
            assertThat(lookUp(DamageKind.DENT, region).limits().allowed()).isFalse();
        }
    }

    @Test
    void aScoreIsCappedAsAShareOfTheThicknessAtTheDamage() {
        LimitRow found = lookUp(DamageKind.SCRATCH);
        assertThat(found.limits().depthCap(SKIN)).contains(new Length(3));
        assertThat(found.limits().depthCap(inch("0.160"))).contains(new Length(8));
    }

    @Test
    void aGougeIsHeldTighterThanAScratchOnOpenSkin() {
        LimitRow gouge = lookUp(DamageKind.GOUGE, PanelKind.UNPRESSURISED_SKIN);
        LimitRow scratch = lookUp(DamageKind.SCRATCH, PanelKind.UNPRESSURISED_SKIN);
        assertThat(gouge.limits()
                        .depthCap(SKIN)
                        .orElseThrow()
                        .isLessThan(scratch.limits().depthCap(SKIN).orElseThrow()))
                .isTrue();
    }

    @Test
    void everyScoreRowCarriesTheBlendTaperAndTheEdgeMargin() {
        for (DamageKind kind : List.of(DamageKind.SCRATCH, DamageKind.GOUGE, DamageKind.NICK)) {
            LimitRow found = lookUp(kind);
            assertThat(found.limits().blendTaper()).isPresent();
            assertThat(found.limits().minEdgeMarginDiameters().orElseThrow()).isEqualByComparingTo("2.0");
        }
    }

    @Test
    void corrosionIsCappedOnAreaAsWellAsDepth() {
        LimitRow found = lookUp(DamageKind.CORROSION);
        assertThat(found.limits().maxArea().orElseThrow()).isEqualByComparingTo("4.00");
        assertThat(found.limits().depthCap(SKIN)).contains(new Length(3));
    }

    @Test
    void corrosionGetsAGentlerBlendThanAScratch() {
        LimitRow corrosion = lookUp(DamageKind.CORROSION);
        LimitRow scratch = lookUp(DamageKind.SCRATCH);
        assertThat(corrosion
                        .limits()
                        .blendTaper()
                        .orElseThrow()
                        .satisfies(scratch.limits().blendTaper().orElseThrow()))
                .isTrue();
    }

    @Test
    void aLightningBurnIsCappedAbsolutelyAndNotAsAShare() {
        LimitRow found = lookUp(DamageKind.LIGHTNING_BURN);
        assertThat(found.limits().maxDepth()).contains(inch("0.010"));
        assertThat(found.limits().maxDepthFraction()).isEmpty();
    }

    @Test
    void thePenetrationKindsHaveNoAllowableSizeAnywhere() {
        for (DamageKind kind : List.of(
                DamageKind.CRACK, DamageKind.HOLE, DamageKind.PUNCTURE, DamageKind.BUCKLE, DamageKind.DISBOND)) {
            for (PanelKind panel : List.of(PanelKind.PRESSURISED_SKIN, PanelKind.UNPRESSURISED_SKIN)) {
                assertThat(lookUp(kind, panel).limits().allowed()).isFalse();
            }
        }
    }

    @Test
    void everyKindTheDeskMeasuresHasARowSomewhereInTheChapter() {
        LimitTable table = Manual53.allowableDamage(12);
        assertThat(table.kindsCovered()).isEqualTo(EnumSet.allOf(DamageKind.class));
    }

    @Test
    void aPanelKindWithNoRowOfItsOwnFallsToTheCatchAll() {
        LimitRow found = lookUp(DamageKind.DENT, PanelKind.FAIRING, Region.CUTOUT_EDGE);
        assertThat(found.limits().maxDepth()).contains(inch("0.100"));
    }
}
