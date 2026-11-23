package io.github.rkaushikdev.doublerbay.assess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Scoring;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.manual.Cleanups;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PanelAssessmentTest {

    private static final Book BOOK = Book.v320();

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Finding smallDent(int sequence, String aft, int stringer, String down, String along) {
        return Sample.cardWith()
                .sequence(sequence)
                .geometry(new Dent(inch("0.002"), inch(along), inch(along)))
                .at(Sample.cabinPoint(15, aft, stringer, down))
                .build();
    }

    private static Finding smallDent(int sequence, String aft, String down) {
        return smallDent(sequence, aft, 10, down, "1.000");
    }

    private static Finding smallDent(int sequence, String aft) {
        return smallDent(sequence, aft, "4.000");
    }

    private static Finding smallDent(int sequence) {
        return smallDent(sequence, "10.000");
    }

    private static PanelAssessment panel(Finding... cards) {
        return PanelAssessment.assessPanel(BOOK, Sample.tailContext(), List.of(cards));
    }

    private static Set<String> rules(Assessment one) {
        return one.verdict().reasons().stream().map(Reason::rule).collect(Collectors.toSet());
    }

    private static Reason reasonNamed(Assessment one, String rule) {
        return one.verdict().reasons().stream()
                .filter(reason -> reason.rule().equals(rule))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void oneCardOnAPanelIsOneGroup() {
        PanelAssessment found = panel(smallDent(1));
        assertThat(found.cardCount()).isEqualTo(1);
        assertThat(found.damageCount()).isEqualTo(1);
        assertThat(found.disposition()).isSameAs(Disposition.ALLOWABLE);
    }

    @Test
    void thePanelReadsBackItsCardsGroupsAndAnswer() {
        String printed = panel(smallDent(1)).toString();
        assertThat(printed).contains("1 cards in 1 groups");
        assertThat(printed).contains("allowable");
    }

    @Test
    void aPanelWithNoCardsIsRefused() {
        assertThatThrownBy(() -> panel())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one card");
    }

    @Test
    void cardsOnTwoDifferentPanelsCannotBeAssessedAsOne() {
        Finding farApart = Sample.card(2, Sample.cabinPointAtFrame(40));
        assertThatThrownBy(() -> panel(smallDent(1), farApart))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not on");
    }

    @Test
    void twoFarApartCardsStayTwoGroups() {
        PanelAssessment found = panel(smallDent(1, "2.000"), smallDent(2, "16.000"));
        assertThat(found.damageCount()).isEqualTo(2);
    }

    @Test
    void twoCloseCardsComeBackAsOneGroup() {
        PanelAssessment found = panel(smallDent(1, "8.000"), smallDent(2, "8.500"));
        assertThat(found.damageCount()).isEqualTo(1);
        assertThat(found.cardCount()).isEqualTo(2);
    }

    @Test
    void aCardTakenTogetherWithANeighbourSaysSoOnItsOwnFace() {
        PanelAssessment found = panel(smallDent(1, "8.000"), smallDent(2, "8.500"));
        Assessment one = found.forCard(smallDent(1).reference());
        assertThat(rules(one)).contains("group");
        Reason groupReason = reasonNamed(one, "group");
        assertThat(groupReason.detail()).contains("inside the clear separation");
    }

    @Test
    void aGroupInsideTheCountDoesNotRaiseTheAnswer() {
        PanelAssessment found = panel(smallDent(1, "8.000"), smallDent(2, "8.500"));
        assertThat(found.disposition()).isSameAs(Disposition.ALLOWABLE);
    }

    @Test
    void aGroupOverTheCountRaisesEveryCardInIt() {
        PanelAssessment crowded =
                panel(smallDent(1, "8.000"), smallDent(2, "8.400"), smallDent(3, "8.800"), smallDent(4, "9.200"));
        assertThat(crowded.damageCount()).isEqualTo(1);
        assertThat(crowded.disposition()).isSameAs(Disposition.REPAIRABLE);
        for (Assessment one : crowded.assessments()) {
            assertThat(one.disposition()).isSameAs(Disposition.REPAIRABLE);
        }
    }

    @Test
    void theBayCountBitesOnSeparateDamagesInOneBay() {
        PanelAssessment spread =
                panel(smallDent(1, "2.000", "2.000"), smallDent(2, "9.000", "6.000"), smallDent(3, "16.000", "2.000"));
        assertThat(spread.damageCount()).isEqualTo(3);
        assertThat(spread.disposition()).isSameAs(Disposition.REPAIRABLE);
        assertThat(rules(spread.assessments().get(0))).contains("bay count");
    }

    @Test
    void twoDamagesInABayAreInsideTheBayCount() {
        PanelAssessment pair = panel(smallDent(1, "2.000", "2.000"), smallDent(2, "16.000", "6.000"));
        assertThat(pair.disposition()).isSameAs(Disposition.ALLOWABLE);
    }

    @Test
    void everyCardCarriesThePanelCountInItsTrail() {
        PanelAssessment found = panel(smallDent(1));
        assertThat(rules(found.assessments().get(0))).contains("panel count");
    }

    @Test
    void thePanelCountIsTakenOnGroupsAndNotOnCards() {
        PanelAssessment crowded = panel(smallDent(1, "8.000"), smallDent(2, "8.400"), smallDent(3, "8.800"));
        assertThat(crowded.cardCount()).isEqualTo(3);
        assertThat(crowded.damageCount()).isEqualTo(1);
        Reason panelReason = reasonNamed(crowded.assessments().get(0), "panel count");
        assertThat(panelReason.detail()).startsWith("1 on");
    }

    @Test
    void aCardThatIsNotOnThePanelCannotBeLookedUp() {
        PanelAssessment found = panel(smallDent(1));
        assertThatThrownBy(() -> found.forCard(smallDent(9).reference())).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void cardsSplitAcrossPanelsComeBackAsOneAssessmentEach() {
        Finding forward = smallDent(1);
        Finding aft = Sample.card(2, Sample.cabinPointAtFrame(40, 10));
        List<PanelAssessment> panels = PanelAssessment.assessByPanel(BOOK, Sample.tailContext(), List.of(forward, aft));
        assertThat(panels).hasSize(2);
        assertThat(panels.stream().map(PanelAssessment::cardCount).collect(Collectors.toSet()))
                .isEqualTo(Set.of(1));
    }

    @Test
    void theSplitKeepsCardsOnOnePanelTogether() {
        List<PanelAssessment> panels = PanelAssessment.assessByPanel(
                BOOK, Sample.tailContext(), List.of(smallDent(1, "2.000"), smallDent(2, "16.000")));
        assertThat(panels).hasSize(1);
        assertThat(panels.get(0).cardCount()).isEqualTo(2);
    }

    private static Finding scoreToBlend(int sequence, String aft, String length) {
        return Sample.cardWith()
                .sequence(sequence)
                .kind(DamageKind.SCRATCH)
                .geometry(new Scoring(inch("0.002"), inch(length), inch("0.060"), Angle.ofDegrees(90)))
                .at(Sample.cabinPoint(15, aft, 10, "4.000"))
                .build();
    }

    @Test
    void oneBlendOnAPanelIsLeftToItsOwnShareCheck() {
        Assessment alone =
                panel(scoreToBlend(1, "10.000", "2.000")).assessments().get(0);
        assertThat(alone.disposition()).isSameAs(Disposition.REWORKABLE);
        assertThat(rules(alone)).doesNotContain("panel blends");
    }

    @Test
    void theBlendsOnOnePanelAreAddedUpAgainstTheShareTheBookAllows() {
        PanelAssessment two = panel(scoreToBlend(1, "2.000", "2.000"), scoreToBlend(2, "18.000", "2.000"));
        assertThat(two.assessments()).allSatisfy(one -> {
            Reason blends = reasonNamed(one, "panel blends");
            assertThat(blends.passed()).isTrue();
            assertThat(blends.detail()).startsWith("2 blends take ").endsWith(" against 0.25");
            assertThat(one.disposition()).isSameAs(Disposition.REWORKABLE);
        });
    }

    @Test
    void blendsThatEachFitButTogetherDoNotAllGoToARepair() {
        Finding first = scoreToBlend(1, "2.000", "6.000");
        Placement placed = Placement.place(Sample.tailContext(), first);
        Length each = Cleanups.standardRules().forKind(DamageKind.SCRATCH).blendExtent(inch("6.000"), inch("0.002"));
        assertThat(each.times(2).isGreaterThan(placed.panelExtent().times(new BigDecimal("0.25"))))
                .isTrue();
        assertThat(each.isAtMost(placed.panelExtent().times(new BigDecimal("0.25"))))
                .isTrue();
        PanelAssessment both = panel(first, scoreToBlend(2, "18.000", "6.000"));
        assertThat(both.assessments()).allSatisfy(one -> {
            assertThat(reasonNamed(one, "panel blends").passed()).isFalse();
            assertThat(one.disposition()).isSameAs(Disposition.REPAIRABLE);
            assertThat(reasonNamed(one, "blend share").passed()).isTrue();
        });
    }
}
