package io.github.rkaushikdev.doublerbay.v320;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.manual.LimitRow;
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

class Manual57Test {

    private static final Length PLATE = Length.ofInches("0.160");

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static LimitRow lookUp(DamageKind kind, PanelKind panel, Region region, int revision) {
        return Manual57.allowableDamage(revision).lookup(kind, panel, region);
    }

    private static LimitRow lookUp(DamageKind kind, PanelKind panel) {
        return lookUp(kind, panel, Region.OPEN_FIELD, 10);
    }

    private static LimitRow lookUp(DamageKind kind, Region region) {
        return lookUp(kind, PanelKind.WING_LOWER, region, 10);
    }

    private static LimitRow lookUp(DamageKind kind) {
        return lookUp(kind, PanelKind.WING_LOWER);
    }

    private static LimitRow barrelScratch() {
        return Manual53.allowableDamage(12).lookup(DamageKind.SCRATCH, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD);
    }

    private static Length capAt(LimitRow found, Length thickness) {
        return found.limits().depthCap(thickness).orElseThrow();
    }

    @Test
    void theChapterCarriesItsOwnRevisionHistory() {
        assertThat(Manual57.manual().latest().number()).isEqualTo(10);
        assertThat(Manual57.manual().inForceOn(LocalDate.of(2024, 8, 1)).number())
                .isEqualTo(9);
        assertThat(Manual57.manual().latest().number())
                .isNotEqualTo(Manual53.manual().latest().number());
    }

    @Test
    void theWingPagesAreTheFourSubjects() {
        Set<String> references =
                Manual57.PAGES.stream().map(page -> page.reference().toString()).collect(Collectors.toSet());
        assertThat(references).isEqualTo(Set.of("57-10-01", "57-20-01", "57-30-01", "57-40-01"));
    }

    @Test
    void aChordwiseScoreOnTheLowerSurfaceGetsHalfTheCap() {
        Length plate = inch("0.200");
        for (DamageKind kind : List.of(DamageKind.SCRATCH, DamageKind.GOUGE, DamageKind.NICK)) {
            LimitRow lower = lookUp(kind);
            assertThat(lower.limits().acrossLoadShare()).contains(new Ratio(Manual57.CHORDWISE_SHARE));
            assertThat(lower.limits().depthCap(plate, false)).contains(inch("0.005"));
            assertThat(lower.limits().depthCap(plate, true)).contains(new Length(2));
            LimitRow upper = lookUp(kind, PanelKind.WING_UPPER);
            assertThat(upper.limits().acrossLoadShare()).isEmpty();
            assertThat(upper.limits().depthCap(plate, true))
                    .isEqualTo(upper.limits().depthCap(plate, false));
        }
    }

    @Test
    void aSurfaceStatedWithoutAShareTreatsEveryDirectionAlike() {
        Manual57.SurfaceLimits plain = new Manual57.SurfaceLimits("0.05", "0.88", "0.010");
        assertThat(plain.acrossTheLoad()).isEmpty();
        assertThat(Manual57.SURFACE_LIMITS.get(PanelKind.WING_LOWER).acrossTheLoad())
                .contains("0.50");
    }

    @Test
    void aDentIsNeverAllowableOnTheLowerSurface() {
        assertThat(lookUp(DamageKind.DENT, PanelKind.WING_LOWER).limits().allowed())
                .isFalse();
    }

    @Test
    void aDentOnTheUpperSurfaceHasALimit() {
        LimitRow upper = lookUp(DamageKind.DENT, PanelKind.WING_UPPER);
        assertThat(upper.limits().allowed()).isTrue();
        assertThat(upper.limits().maxDepth()).contains(inch("0.040"));
    }

    @Test
    void theTensionSurfaceGetsHalfOfWhatTheCompressionSurfaceGets() {
        LimitRow lower = lookUp(DamageKind.SCRATCH, PanelKind.WING_LOWER);
        LimitRow upper = lookUp(DamageKind.SCRATCH, PanelKind.WING_UPPER);
        assertThat(capAt(lower, PLATE).times(2)).isEqualTo(capAt(upper, PLATE));
    }

    @Test
    void theAbsoluteCapBitesOnTheHeavyPlateInboard() {
        LimitRow upper = lookUp(DamageKind.SCRATCH, PanelKind.WING_UPPER);
        Length root = capAt(upper, inch("0.250"));
        Length tip = capAt(upper, inch("0.100"));
        assertThat(root).isEqualTo(inch("0.010"));
        assertThat(tip).isEqualTo(new Length(5));
    }

    @Test
    void theWingIsTighterThanTheFuselageOnTheSameDamage() {
        LimitRow wing = lookUp(DamageKind.SCRATCH, PanelKind.WING_LOWER);
        LimitRow barrel = barrelScratch();
        assertThat(capAt(wing, PLATE).isLessThan(capAt(barrel, PLATE))).isTrue();
    }

    @Test
    void theWingAsksForMoreEdgeMarginThanTheBarrel() {
        LimitRow wing = lookUp(DamageKind.SCRATCH, PanelKind.WING_UPPER);
        LimitRow barrel = barrelScratch();
        assertThat(wing.limits().minEdgeMarginDiameters().orElseThrow())
                .isGreaterThan(barrel.limits().minEdgeMarginDiameters().orElseThrow());
    }

    @Test
    void theWingBlendIsGentlerThanTheBarrelBlend() {
        LimitRow wing = lookUp(DamageKind.SCRATCH, PanelKind.WING_UPPER);
        LimitRow barrel = barrelScratch();
        assertThat(wing.limits()
                        .blendTaper()
                        .orElseThrow()
                        .satisfies(barrel.limits().blendTaper().orElseThrow()))
                .isTrue();
    }

    @Test
    void corrosionOnTheLowerSurfaceIsCappedOnASmallerArea() {
        LimitRow lower = lookUp(DamageKind.CORROSION, PanelKind.WING_LOWER);
        LimitRow upper = lookUp(DamageKind.CORROSION, PanelKind.WING_UPPER);
        assertThat(lower.limits().maxArea().orElseThrow()).isEqualByComparingTo("1.50");
        assertThat(upper.limits().maxArea().orElseThrow()).isEqualByComparingTo("3.00");
    }

    @Test
    void nothingIsAllowableOnARowOrACornerOnTheWingEither() {
        for (Region region : List.of(Region.FASTENER_ROW, Region.CUTOUT_CORNER, Region.CHEM_MILL_STEP)) {
            for (DamageKind kind : List.of(DamageKind.SCRATCH, DamageKind.CORROSION, DamageKind.DENT)) {
                assertThat(lookUp(kind, region).limits().allowed()).isFalse();
            }
        }
    }

    @Test
    void thePenetrationKindsHaveNoAllowableSizeOnTheWing() {
        for (DamageKind kind : List.of(
                DamageKind.CRACK, DamageKind.HOLE, DamageKind.PUNCTURE, DamageKind.BUCKLE, DamageKind.DISBOND)) {
            assertThat(lookUp(kind).limits().allowed()).isFalse();
        }
    }

    @Test
    void everyKindTheDeskMeasuresHasARowInTheWingChapter() {
        assertThat(Manual57.allowableDamage(10).kindsCovered()).isEqualTo(EnumSet.allOf(DamageKind.class));
    }

    @Test
    void aPanelKindOffTheWingFallsToTheCatchAllRow() {
        LimitRow found = lookUp(DamageKind.SCRATCH, PanelKind.FAIRING);
        assertThat(found.limits().maxDepth()).contains(inch("0.005"));
    }

    @Test
    void theWingLightningLimitIsTighterThanTheBarrelOne() {
        LimitRow wing = lookUp(DamageKind.LIGHTNING_BURN);
        LimitRow barrel = Manual53.allowableDamage(12)
                .lookup(DamageKind.LIGHTNING_BURN, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD);
        assertThat(wing.limits()
                        .maxDepth()
                        .orElseThrow()
                        .isLessThan(barrel.limits().maxDepth().orElseThrow()))
                .isTrue();
        assertThat(wing.limits().maxArea().orElseThrow())
                .isLessThan(barrel.limits().maxArea().orElseThrow());
    }

    @Test
    void theTableIsBuiltOncePerRevisionAndShared() {
        assertThat(Manual57.allowableDamage(10)).isSameAs(Manual57.allowableDamage(10));
    }
}
