package io.github.rkaushikdev.doublerbay.assess;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Region;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The joint rules seen through the assessor rather than on their own.
 *
 * <p>The unit tests for the rules build a joint by hand. These ones let the real V320 structure decide
 * where the joints are, which is the only way to catch a rule that works perfectly and never fires
 * because nothing on the aeroplane ever reaches it.
 */
class AssessWithJointsTest {

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Crack crack(int bearing) {
        return new Crack(inch("0.300"), new Angle(bearing));
    }

    private static Finding card(Geometry geometry, FuselagePoint at, DamageKind kind, DamageOrigin origin) {
        return Sample.cardWith()
                .sequence(1)
                .kind(kind)
                .geometry(geometry != null ? geometry : crack(0))
                .at(at != null ? at : Sample.cabinPoint())
                .origin(origin)
                .build();
    }

    private static Finding card(Geometry geometry, FuselagePoint at) {
        return card(geometry, at, DamageKind.CRACK, DamageOrigin.FATIGUE);
    }

    private static Finding card(FuselagePoint at) {
        return card(null, at);
    }

    private static Placement placed(Finding finding) {
        return Placement.place(Sample.tailContext(), finding);
    }

    private static Assessment assessed(Finding finding) {
        return Assessor.assess(Book.v320(), Sample.tailContext(), finding);
    }

    private static Set<String> named(Assessment answer) {
        return answer.verdict().reasons().stream().map(Reason::rule).collect(Collectors.toSet());
    }

    /**
     * A point the real structure puts in a fastener row.
     *
     * <p>Found by walking the stringers rather than asserted, because a hard coded offset is a test
     * that passes until somebody moves a stringer by an inch.
     */
    private static FuselagePoint onARow() {
        for (int stringer = 1; stringer < 30; stringer++) {
            for (String below : List.of("0.000", "0.200", "0.500")) {
                FuselagePoint point = Sample.cabinPointOn(stringer, below);
                Placement spot = placed(card(point));
                if (spot.region() == Region.FASTENER_ROW) {
                    return point;
                }
            }
        }
        throw new AssertionError("no point on this aeroplane lands in a fastener row");
    }

    /** A point the real structure puts nowhere near a joint. */
    private static FuselagePoint awayFromARow() {
        for (int stringer = 1; stringer < 30; stringer++) {
            for (String below : List.of("6.000", "4.000", "3.000")) {
                FuselagePoint point = Sample.cabinPointOn(stringer, below);
                if (placed(card(point)).joint().isEmpty()) {
                    return point;
                }
            }
        }
        throw new AssertionError("no point on this aeroplane lands clear of a joint");
    }

    @Test
    void theStructureActuallyHasAFastenerRowOnIt() {
        assertThat(onARow()).isNotNull();
    }

    @Test
    void aDamageInARowCarriesTheJointOnItsPlacement() {
        assertThat(placed(card(onARow())).joint()).isPresent();
    }

    @Test
    void aDamageAwayFromARowCarriesNoJoint() {
        assertThat(placed(card(awayFromARow())).joint()).isEmpty();
    }

    @Test
    void aDamageInARowKnowsWhichFastenerIsNearest() {
        assertThat(placed(card(onARow())).nearestFastener()).isPresent();
    }

    @Test
    void theJointRulesReachTheVerdictInARow() {
        assertThat(named(assessed(card(onARow())))).contains("fatigue critical");
    }

    @Test
    void theJointRulesStayOutOfAVerdictAwayFromARow() {
        Set<String> named = named(assessed(card(awayFromARow())));
        assertThat(named).doesNotContain("fatigue critical");
        assertThat(named).doesNotContain("along the row");
    }

    @Test
    void aCrackAlongTheRowIsNamedInTheVerdict() {
        List<Reason> reasons = assessed(card(crack(0), onARow())).verdict().reasons();
        assertThat(reasons).anyMatch(one -> one.rule().equals("along the row"));
    }

    @Test
    void aDentInARowGetsNoDirectionRule() {
        Dent dent = new Dent(inch("0.010"), inch("1.000"), inch("1.000"));
        Finding made = card(dent, onARow(), DamageKind.DENT, DamageOrigin.IMPACT);
        Set<String> named = named(assessed(made));
        assertThat(named).doesNotContain("along the row");
        assertThat(named).contains("fatigue critical");
    }

    @Test
    void aDamageInALapJointIsNeverLeftAsItStands() {
        Assessment answer = assessed(card(onARow()));
        assertThat(answer.disposition()).isNotSameAs(Disposition.ALLOWABLE);
    }

    @Test
    void theSameDamageAwayFromARowCanBeAllowable() {
        Dent dent = new Dent(inch("0.005"), inch("1.000"), inch("1.000"));
        Finding made = card(dent, awayFromARow(), DamageKind.DENT, DamageOrigin.IMPACT);
        assertThat(assessed(made).disposition()).isSameAs(Disposition.ALLOWABLE);
    }

    @Test
    void everyJointRuleHasAChanceToFire() {
        Set<String> named = named(assessed(card(crack(0), onARow())));
        Set<String> jointRules = new HashSet<>(Set.of("joint edge margin", "along the row", "fatigue critical"));
        jointRules.retainAll(named);
        // The bond line rule only ever fires on a bonded joint, which has no rows for these three.
        assertThat(jointRules).hasSize(Joints.JOINT_CHECKS.size() - 1);
        assertThat(named).doesNotContain("bond line");
    }

    @Test
    void theOrdinaryRulesStillRunInARow() {
        assertThat(named(assessed(card(onARow())))).contains("allowable");
    }

    @Test
    void theVerdictStillNamesThePageItCameOff() {
        assertThat(assessed(card(onARow())).verdict().reference()).isPresent();
    }
}
