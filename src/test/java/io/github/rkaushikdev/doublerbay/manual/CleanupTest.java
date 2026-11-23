package io.github.rkaushikdev.doublerbay.manual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import io.github.rkaushikdev.doublerbay.structure.Region;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CleanupTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static CleanupRule rule(Taper taper, boolean blendable, String radius, String fraction) {
        return CleanupRule.of(
                taper == null ? new Taper(20) : taper,
                blendable,
                radius == null || radius.isEmpty() ? null : inch(radius),
                fraction == null || fraction.isEmpty() ? null : new BigDecimal(fraction));
    }

    private static CleanupRule rule(Taper taper) {
        return rule(taper, true, "0.250", "0.25");
    }

    private static CleanupRule rule() {
        return rule(null);
    }

    @Test
    void aRuleReadsBackHowTheBlendRuns() {
        assertThat(rule()).hasToString("blend at 20:1 with 0.250 ends, up to 25.00% of the panel");
        assertThat(CleanupRule.of(new Taper(20), true, null, new BigDecimal("0.25")))
                .hasToString("blend at 20:1, up to 25.00% of the panel");
        assertThat(rule(null, false, null, null)).hasToString("not blendable");
    }

    @Test
    void aKindThatCannotBeBlendedCarriesNoRadius() {
        assertThatThrownBy(() -> rule(null, false, "0.250", "0.25"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("carries no blend radius");
    }

    @Test
    void aBlendFractionIsAPartOfThePanel() {
        assertThatThrownBy(() -> rule(null, true, "0.250", "1.5"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is a part of the panel");
        assertThatThrownBy(() -> rule(null, true, "0.250", "0"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is a part of the panel");
    }

    @Test
    void theRunoutComesOffTheTaperAndTheDepth() {
        assertThat(rule().runout(inch("0.010"))).isEqualTo(inch("0.200"));
        assertThat(rule(new Taper(30)).runout(inch("0.010"))).isEqualTo(inch("0.300"));
    }

    @Test
    void aDepthThatRunsBackwardsIsRefused() {
        assertThatThrownBy(() -> rule().runout(inch("-0.001")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not run backwards");
    }

    @Test
    void theBlendCoversTheDamagePlusARunoutAtEachEnd() {
        assertThat(rule().blendExtent(inch("2.000"), inch("0.010"))).isEqualTo(inch("2.400"));
    }

    @Test
    void aBlendIsRefusedOnAChemMillStepAtAnyDepth() {
        assertThat(rule().allowedIn(Region.CHEM_MILL_STEP)).isFalse();
        assertThat(Cleanups.NO_BLEND_REGIONS).contains(Region.CHEM_MILL_STEP);
    }

    @Test
    void aBlendIsRefusedOnAFastenerRowAndUnderADoubler() {
        assertThat(rule().allowedIn(Region.FASTENER_ROW)).isFalse();
        assertThat(rule().allowedIn(Region.UNDER_DOUBLER)).isFalse();
    }

    @Test
    void aBlendIsAllowedInTheOpenFieldAndOnALand() {
        assertThat(rule().allowedIn(Region.OPEN_FIELD)).isTrue();
        assertThat(rule().allowedIn(Region.LAND)).isTrue();
    }

    @Test
    void aKindThatCannotBeBlendedIsNotBlendableAnywhere() {
        CleanupRule never = rule(null, false, null, null);
        assertThat(never.allowedIn(Region.OPEN_FIELD)).isFalse();
    }

    @Test
    void aBlendInsideThePanelShareIsAllowedAndPastItIsNot() {
        assertThat(rule().withinPanelShare(inch("5.000"), inch("20.000"))).isTrue();
        assertThat(rule().withinPanelShare(inch("5.000"), inch("20.001"))).isTrue();
        assertThat(rule().withinPanelShare(inch("6.000"), inch("20.000"))).isFalse();
    }

    @Test
    void aRuleWithNoShareLimitNeverTakesTooMuch() {
        CleanupRule openRule = rule(null, true, "0.250", null);
        assertThat(openRule.withinPanelShare(inch("19.000"), inch("20.000"))).isTrue();
    }

    @Test
    void aPanelWithNoExtentCannotBeSharedInto() {
        assertThatThrownBy(() -> rule().withinPanelShare(inch("1.000"), inch("0")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a panel extent is a length");
    }

    @Test
    void theStandardRulesCoverEveryKindTheDeskMeasures() {
        CleanupRules rules = Cleanups.standardRules();
        for (DamageKind kind : DamageKind.values()) {
            assertThat(rules.covers(kind)).isTrue();
        }
        assertThat(rules.kinds()).isEqualTo(EnumSet.allOf(DamageKind.class));
    }

    @Test
    void theKindsWithNoAllowableSizeAreAlsoNotBlendable() {
        CleanupRules rules = Cleanups.standardRules();
        for (DamageKind kind : DamageKind.values()) {
            if (kind.isNeverAllowable()) {
                assertThat(rules.forKind(kind).blendable()).isFalse();
            }
        }
    }

    @Test
    void aDentIsNotBlendedOutBecauseItHasLostNoMetal() {
        assertThat(Cleanups.standardRules().forKind(DamageKind.DENT).blendable())
                .isFalse();
    }

    @Test
    void theMaterialLossKindsAreTheOnesThatGetBlended() {
        CleanupRules rules = Cleanups.standardRules();
        for (DamageKind kind : List.of(DamageKind.SCRATCH, DamageKind.GOUGE, DamageKind.NICK, DamageKind.CORROSION)) {
            assertThat(rules.forKind(kind).blendable()).isTrue();
        }
    }

    @Test
    void corrosionGetsTheGentlerTaperAndTheLargerShare() {
        CleanupRules rules = Cleanups.standardRules();
        CleanupRule corrosion = rules.forKind(DamageKind.CORROSION);
        CleanupRule scratch = rules.forKind(DamageKind.SCRATCH);
        assertThat(corrosion.taper().satisfies(scratch.taper())).isTrue();
        assertThat(corrosion.maxBlendFraction().orElseThrow())
                .isGreaterThan(scratch.maxBlendFraction().orElseThrow());
    }

    @Test
    void aNickIsHeldToTheSmallestShareOfThePanel() {
        CleanupRules rules = Cleanups.standardRules();
        assertThat(rules.forKind(DamageKind.NICK).maxBlendFraction().orElseThrow())
                .isEqualByComparingTo("0.10");
    }

    @Test
    void aKindTheBookHasNoRuleForIsALookupMiss() {
        CleanupRules thin = new CleanupRules(Map.of(DamageKind.DENT, rule(null, false, null, null)));
        assertThat(thin.covers(DamageKind.SCRATCH)).isFalse();
        assertThatThrownBy(() -> thin.forKind(DamageKind.SCRATCH))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("no cleanup rule for a scratch");
    }

    @Test
    void theRulesReadBackHowManyKindsTheyCover() {
        assertThat(Cleanups.standardRules()).hasToString("cleanup rules for 11 kinds");
    }

    @Test
    void aBlendIsNeverNarrowerThanTheCutterItsEndsAreFormedWith() {
        CleanupRule radiused = CleanupRule.blendable(new Taper(20), inch("0.250"), new BigDecimal("0.25"));
        assertThat(radiused.blendExtent(inch("0.060"), inch("0.004"))).isEqualTo(inch("0.500"));
        assertThat(radiused.blendExtent(inch("2.000"), inch("0.010"))).isEqualTo(inch("2.400"));
        CleanupRule square = CleanupRule.of(new Taper(20), true, null, null);
        assertThat(square.blendExtent(inch("0.060"), inch("0.004"))).isEqualTo(inch("0.220"));
    }
}
