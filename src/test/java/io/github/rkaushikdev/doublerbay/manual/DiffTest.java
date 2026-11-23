package io.github.rkaushikdev.doublerbay.manual;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Ratio;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.v320.Manual53;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiffTest {

    private static final Reference PAGE = Reference.parse("53-10");

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static LimitSet limits() {
        return LimitSet.allowable();
    }

    private static LimitRow row(DamageKind kind, LimitSet limits) {
        return new LimitRow(new LimitKey(kind), limits, PAGE);
    }

    private static LimitRow row(LimitSet limits) {
        return row(DamageKind.DENT, limits);
    }

    private static LimitRow row(DamageKind kind) {
        return row(kind, limits());
    }

    private static LimitRow row() {
        return row(limits());
    }

    private static LimitTable table(int revision, LimitRow... rows) {
        return new LimitTable(PAGE, revision, List.of(rows));
    }

    @Test
    void twoIdenticalTablesDiffToNothing() {
        LimitTable before = table(11, row(limits().withMaxDepth(inch("0.080"))));
        LimitTable after = table(12, row(limits().withMaxDepth(inch("0.080"))));
        RevisionDiff found = Diffs.diffTables(before, after);
        assertThat(found.isEmpty()).isTrue();
        assertThat(found.toString()).endsWith("no change");
    }

    @Test
    void aSmallerDepthCapIsATightening() {
        RevisionDiff found = Diffs.diffTables(
                table(11, row(limits().withMaxDepth(inch("0.080")))),
                table(12, row(limits().withMaxDepth(inch("0.060")))));
        assertThat(found.tightenings()).hasSize(1);
        assertThat(found.loosenings()).isEmpty();
        assertThat(found.changed().get(0).field()).isEqualTo("max_depth");
    }

    @Test
    void aLargerDepthCapIsALoosening() {
        RevisionDiff found = Diffs.diffTables(
                table(11, row(limits().withMaxDepth(inch("0.060")))),
                table(12, row(limits().withMaxDepth(inch("0.080")))));
        assertThat(found.loosenings()).hasSize(1);
        assertThat(found.tightenings()).isEmpty();
    }

    @Test
    void aLargerResidualFloorIsATighteningAndNotALoosening() {
        RevisionDiff found = Diffs.diffTables(
                table(11, row(limits().withMinResidualFraction(new Ratio("0.90")))),
                table(12, row(limits().withMinResidualFraction(new Ratio("0.95")))));
        assertThat(found.tightenings()).hasSize(1);
        assertThat(found.changed().get(0).field()).isEqualTo("min_residual_fraction");
    }

    @Test
    void aSmallerResidualFloorIsALoosening() {
        RevisionDiff found = Diffs.diffTables(
                table(11, row(limits().withMinResidualFraction(new Ratio("0.95")))),
                table(12, row(limits().withMinResidualFraction(new Ratio("0.90")))));
        assertThat(found.loosenings()).hasSize(1);
    }

    @Test
    void aLimitAppearingWhereThereWasNoneIsATightening() {
        RevisionDiff found =
                Diffs.diffTables(table(11, row()), table(12, row(limits().withMaxArea(new BigDecimal("4.00")))));
        assertThat(found.changed().get(0).direction()).isSameAs(Direction.TIGHTER);
    }

    @Test
    void aLimitDisappearingIsALoosening() {
        RevisionDiff found =
                Diffs.diffTables(table(11, row(limits().withMaxArea(new BigDecimal("4.00")))), table(12, row()));
        assertThat(found.changed().get(0).direction()).isSameAs(Direction.LOOSER);
    }

    @Test
    void aRowThatStopsAllowingAnythingIsATightening() {
        RevisionDiff found = Diffs.diffTables(
                table(11, row(limits().withMaxDepth(inch("0.060")))),
                table(12, new LimitRow(new LimitKey(DamageKind.DENT), LimitSet.nothingAllowable(), PAGE)));
        assertThat(found.changed().get(0).field()).isEqualTo("allowed");
        assertThat(found.changed().get(0).direction()).isSameAs(Direction.TIGHTER);
    }

    @Test
    void aRowThatStartsAllowingSomethingIsALoosening() {
        RevisionDiff found = Diffs.diffTables(
                table(12, new LimitRow(new LimitKey(DamageKind.DENT), LimitSet.nothingAllowable(), PAGE)),
                table(13, row(limits().withMaxDepth(inch("0.060")))));
        assertThat(found.changed().get(0).direction()).isSameAs(Direction.LOOSER);
    }

    @Test
    void aRowThatStopsAllowingAnythingReportsOnlyThat() {
        RevisionDiff found = Diffs.diffTables(
                table(11, row(limits().withMaxDepth(inch("0.060")).withMaxArea(new BigDecimal("4.00")))),
                table(12, new LimitRow(new LimitKey(DamageKind.DENT), LimitSet.nothingAllowable(), PAGE)));
        assertThat(found.changed()).hasSize(1);
    }

    @Test
    void aNewRowIsReportedAsAdded() {
        RevisionDiff found = Diffs.diffTables(table(11, row()), table(12, row(), row(DamageKind.SCRATCH)));
        assertThat(found.added()).hasSize(1);
        assertThat(found.removed()).isEmpty();
    }

    @Test
    void aWithdrawnRowIsReportedAsRemoved() {
        RevisionDiff found = Diffs.diffTables(table(11, row(), row(DamageKind.SCRATCH)), table(12, row()));
        assertThat(found.removed()).hasSize(1);
        assertThat(found.added()).isEmpty();
    }

    @Test
    void theReviewListIsEverythingThatDidNotGetLooser() {
        RevisionDiff found = Diffs.diffTables(
                table(
                        11,
                        row(limits().withMaxDepth(inch("0.080"))),
                        row(DamageKind.SCRATCH, limits().withMaxDepth(inch("0.005")))),
                table(
                        12,
                        row(limits().withMaxDepth(inch("0.060"))),
                        row(DamageKind.SCRATCH, limits().withMaxDepth(inch("0.008")))));
        assertThat(found.needsReview()).hasSize(1);
        assertThat(Direction.LOOSER.needsReview()).isFalse();
        assertThat(Direction.TIGHTER.needsReview()).isTrue();
    }

    @Test
    void aDiffSaysWhetherItTouchesAGivenRow() {
        LimitKey key = new LimitKey(DamageKind.DENT);
        RevisionDiff found = Diffs.diffTables(
                table(11, row(limits().withMaxDepth(inch("0.080")))),
                table(12, row(limits().withMaxDepth(inch("0.060")))));
        assertThat(found.touches(key)).isTrue();
        assertThat(found.touches(new LimitKey(DamageKind.CRACK))).isFalse();
    }

    @Test
    void aDiffReadsBackASummaryOfWhatMoved() {
        RevisionDiff found = Diffs.diffTables(
                table(11, row(limits().withMaxDepth(inch("0.080")))),
                table(12, row(limits().withMaxDepth(inch("0.060")))));
        assertThat(found).hasToString("53-10 rev 11 to 12: 0 added, 0 removed, 1 tightened, 0 loosened");
    }

    @Test
    void theRealCampaignRevisionShowsUpAsATightening() {
        RevisionDiff found = Diffs.diffTables(Manual53.allowableDamage(11), Manual53.allowableDamage(12));
        List<LimitChange> tightened = found.tightenings();
        assertThat(tightened).hasSize(4);
        assertThat(tightened).allSatisfy(change -> assertThat(change.key().panelKind())
                .hasValueSatisfying(skin -> assertThat(Manual53.PRESSURE_SKINS).contains(skin)));
        assertThat(tightened.stream().map(change -> change.key().panelKind().orElseThrow()))
                .containsOnly(PanelKind.PRESSURISED_SKIN, PanelKind.BELLY_SKIN);
        assertThat(found.loosenings()).isEmpty();
    }

    @Test
    void aRevisionThatChangedNothingDiffsToNothing() {
        assertThat(Diffs.diffTables(Manual53.allowableDamage(12), Manual53.allowableDamage(13))
                        .isEmpty())
                .isTrue();
    }

    @Test
    void aChangeReadsBackTheFieldAndTheDirection() {
        RevisionDiff found = Diffs.diffTables(
                table(11, row(limits().withMaxDepth(inch("0.080")))),
                table(12, row(limits().withMaxDepth(inch("0.060")))));
        String printed = found.changed().get(0).toString();
        assertThat(printed).contains("max_depth");
        assertThat(printed).contains("(tighter)");
    }
}
