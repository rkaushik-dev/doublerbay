package io.github.rkaushikdev.doublerbay.repair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Opening;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Sample;
import org.junit.jupiter.api.Test;

class OversizeTest {

    private static final FastenerSize DASH_FIVE = new FastenerSize(5);

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Opening worked(String bore) {
        return new Opening(inch(bore), inch("0.156"), false, true);
    }

    @Test
    void theSizesUpAreTheStockedOnesBiggerThanThisOne() {
        assertThat(FastenerSize.sizesUp(DASH_FIVE))
                .containsExactly(new FastenerSize(6), new FastenerSize(8), new FastenerSize(10), new FastenerSize(12));
        assertThat(FastenerSize.sizesUp(new FastenerSize(12))).isEmpty();
    }

    @Test
    void aWornHoleGoesToTheSmallestOversizeThatCleansItUp() {
        Oversize slight =
                Oversizes.oversizeFor(worked("0.170"), DASH_FIVE, inch("0.500")).orElseThrow();
        assertThat(slight.to()).isEqualTo(new FastenerSize(6));
        assertThat(slight.steps()).isEqualTo(1);
        assertThat(slight).hasToString("ream -5 to -6, 0.188 at 0.500 edge distance");
        Oversize far =
                Oversizes.oversizeFor(worked("0.200"), DASH_FIVE, inch("0.600")).orElseThrow();
        assertThat(far.to()).isEqualTo(new FastenerSize(8));
        assertThat(far.steps()).isEqualTo(2);
    }

    @Test
    void pastTwoSizesUpTheHoleTakesADoubler() {
        assertThat(Oversizes.oversizeFor(worked("0.260"), DASH_FIVE, inch("1.000")))
                .isEmpty();
    }

    @Test
    void anOversizeThatWouldLoseItsEdgeDistanceIsNotOffered() {
        assertThat(Oversizes.oversizeFor(worked("0.170"), DASH_FIVE, inch("0.374")))
                .isEmpty();
        assertThat(Oversizes.oversizeFor(worked("0.170"), DASH_FIVE, inch("0.375")))
                .isPresent();
    }

    @Test
    void aTornHoleIsTrimmedRatherThanOversized() {
        Opening torn = new Opening(inch("0.170"), inch("0.156"), true, true);
        assertThat(Oversizes.oversizeFor(torn, DASH_FIVE, inch("1.000"))).isEmpty();
        assertThatThrownBy(() ->
                        Oversizes.oversizeFor(new Opening(inch("0.170"), inch("0.170")), DASH_FIVE, inch("1.000")))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("not a fastener hole");
    }

    @Test
    void anOversizeIsBiggerCleansUpAndKeepsItsEdgeDistance() {
        assertThatThrownBy(() -> new Oversize(DASH_FIVE, DASH_FIVE, inch("0.150"), inch("1.000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("bigger than the fastener");
        assertThatThrownBy(() -> new Oversize(DASH_FIVE, new FastenerSize(6), inch("0.200"), inch("1.000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("cleans the worked bore");
        assertThatThrownBy(() -> new Oversize(DASH_FIVE, new FastenerSize(6), inch("0.170"), inch("0.300")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("edge distance");
    }

    @Test
    void aWorkedHoleInALapJointRowIsOfferedAnOversizeOffThatRow() {
        Finding hole = Sample.cardWith()
                .kind(DamageKind.HOLE)
                .geometry(worked("0.170"))
                .at(Sample.cabinPointOn(8, "0.500"))
                .build();
        Placement placement = Placement.place(Sample.tailContext(), hole);
        assertThat(Schemes.oversizeFor(hole, placement)).hasValueSatisfying(oversize -> {
            assertThat(oversize.from()).isEqualTo(DASH_FIVE);
            assertThat(oversize.to()).isEqualTo(new FastenerSize(6));
        });
        Finding clear = Sample.cardWith()
                .kind(DamageKind.HOLE)
                .geometry(worked("0.170"))
                .at(Sample.cabinPointOn(10, "4.000"))
                .build();
        assertThat(Schemes.oversizeFor(clear, Placement.place(Sample.tailContext(), clear)))
                .isEmpty();
    }

    @Test
    void aCutOutHasToTakeTornEdgesBackToCleanMetal() {
        Finding torn = Sample.cardWith()
                .kind(DamageKind.HOLE)
                .geometry(new Opening(inch("1.000"), inch("0.800"), true, false))
                .at(Sample.cabinPointOn(10, "4.000"))
                .build();
        assertThatThrownBy(() -> Schemes.checkCutOut(torn, inch("1.200"), inch("1.000")))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("at least 1.250 by 1.050");
        Schemes.checkCutOut(torn, inch("1.250"), inch("1.050"));
        Schemes.checkCutOut(Sample.card(), inch("0.100"), inch("0.100"));
        assertThat(Schemes.fastenerOrStandard(null)).isEqualTo(Schemes.STANDARD_FASTENER);
        assertThat(Schemes.fastenerOrStandard(0)).isEqualTo(Schemes.STANDARD_FASTENER);
        assertThat(Schemes.fastenerOrStandard(6)).isEqualTo(new FastenerSize(6));
        assertThat(Schemes.TRIM_BACK).isEqualTo(inch("0.125"));
    }
}
