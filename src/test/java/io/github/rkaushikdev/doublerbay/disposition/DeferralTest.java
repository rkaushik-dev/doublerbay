package io.github.rkaushikdev.doublerbay.disposition;

import static io.github.rkaushikdev.doublerbay.fleet.UtilisationLog.usage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeferralTest {

    private static final FindingId DR = new FindingId(2025, 4182);
    private static final LocalDate GRANTED = LocalDate.of(2025, 3, 14);
    private static final Usage AT = usage(13800, 24200);

    private static Deferral deferral() {
        return deferral("structures");
    }

    private static Deferral deferral(String by) {
        return new Deferral(DR, GRANTED, AT, Deferral.STANDARD_DEFERRAL, InspectionMethod.DVI, by);
    }

    @Test
    void aDeferralReadsBackItsLimitAndWhatHappensAtTheEnd() {
        String printed = deferral().toString();
        assertThat(printed).startsWith("DR-2025-004182 deferred to");
        assertThat(printed).contains("detailed visual");
    }

    @Test
    void aDeferralIsGrantedBySomebody() {
        assertThatThrownBy(() -> deferral("  "))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("granted by somebody");
    }

    @Test
    void theStandardLimitRunsOnAllThreeClocks() {
        DueAt due = deferral().due();
        assertThat(due.cycles().orElseThrow().count()).isEqualTo(13800 + 500);
        assertThat(due.on()).contains(LocalDate.of(2025, 9, 10));
    }

    @Test
    void aDeferralExpiresOnWhicheverClockGetsThereFirst() {
        Deferral held = deferral();
        assertThat(held.isExpired(usage(14000, 24500), LocalDate.of(2025, 4, 1)))
                .isFalse();
        assertThat(held.isExpired(usage(14400, 24500), LocalDate.of(2025, 4, 1)))
                .isTrue();
        assertThat(held.isExpired(usage(14000, 24500), LocalDate.of(2025, 10, 1)))
                .isTrue();
    }

    @Test
    void theDaysLeftRunDownAndStopAtNothing() {
        Deferral held = deferral();
        assertThat(held.daysLeft(LocalDate.of(2025, 3, 14))).contains(180);
        assertThat(held.daysLeft(LocalDate.of(2025, 9, 10))).contains(0);
        assertThat(held.daysLeft(LocalDate.of(2026, 1, 1))).contains(0);
    }

    @Test
    void anEngineerCanSetATighterLimitThanTheBook() {
        Interval tight = Deferral.limitFor(Interval.parse("50 FC / 30 DY"));
        assertThat(tight.cycles().orElseThrow().count()).isEqualTo(50);
        assertThat(tight.days()).contains(30);
    }

    @Test
    void withNoEngineerLimitTheBookGoverns() {
        assertThat(Deferral.limitFor()).isEqualTo(Deferral.STANDARD_DEFERRAL);
    }

    @Test
    void aCheckBeforeTheLimitPullsTheDeferralIn() {
        Interval limited = Deferral.limitFor(null, LocalDate.of(2025, 5, 1), GRANTED);
        assertThat(limited.days()).contains(48);
        assertThat(limited.cycles()).isEqualTo(Deferral.STANDARD_DEFERRAL.cycles());
    }

    @Test
    void aCheckAfterTheLimitLeavesItAlone() {
        Interval limited = Deferral.limitFor(null, LocalDate.of(2026, 5, 1), GRANTED);
        assertThat(limited.days()).isEqualTo(Deferral.STANDARD_DEFERRAL.days());
    }

    @Test
    void aCheckThatHasAlreadyStartedIsNotSomethingToDeferTo() {
        assertThatThrownBy(() -> Deferral.limitFor(null, GRANTED, GRANTED))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("already started is not a deferral");
    }

    @Test
    void theEngineerLimitAndTheCheckBothBiteTogether() {
        Interval limited = Deferral.limitFor(Interval.parse("50 FC / 90 DY"), LocalDate.of(2025, 4, 1), GRANTED);
        assertThat(limited.cycles().orElseThrow().count()).isEqualTo(50);
        assertThat(limited.days()).contains(18);
    }

    @Test
    void grantingProducesADeferralWithTheTightestLimitOnIt() {
        Deferral held =
                Deferral.grant(DR, GRANTED, AT, "structures", InspectionMethod.DVI, null, LocalDate.of(2025, 5, 1), "");
        assertThat(held.limit().days()).contains(48);
        assertThat(held.by()).isEqualTo("structures");
    }

    @Test
    void aGrantedDeferralCarriesTheMethodItWillBeReInspectedWith() {
        Deferral held = Deferral.grant(DR, GRANTED, AT, "structures", InspectionMethod.HFEC, null, null, "");
        assertThat(held.reInspectWith()).isSameAs(InspectionMethod.HFEC);
    }

    @Test
    void aDeferralCanBeTightenedAfterwardsAndTheFirstOneStands() {
        Deferral before = deferral();
        Deferral after = before.tightenedTo(Interval.parse("30 DY"), "chief engineer", "fleet campaign");
        assertThat(after.limit().days()).contains(30);
        assertThat(after.reason()).isEqualTo("fleet campaign");
        assertThat(before.limit().days()).contains(180);
    }

    @Test
    void tighteningKeepsTheDateAndTheReadingItWasGrantedAt() {
        Deferral after = deferral().tightenedTo(Interval.parse("30 DY"), "chief engineer");
        assertThat(after.granted()).isEqualTo(GRANTED);
        assertThat(after.atUsage()).isEqualTo(AT);
    }

    @Test
    void theExpirySweepFindsTheOnesTheAeroplaneHasFlownPast() {
        Deferral fresh = deferral();
        Deferral stale =
                Deferral.grant(new FindingId(2025, 4183), LocalDate.of(2024, 1, 1), usage(12000, 21000), "structures");
        List<Deferral> found = Deferral.expired(List.of(fresh, stale), usage(14000, 24500), LocalDate.of(2025, 4, 1));
        assertThat(found.stream().map(one -> one.against().sequence()).toList()).containsExactly(4183);
    }

    @Test
    void theExpirySweepOverNothingFindsNothing() {
        assertThat(Deferral.expired(List.of(), usage(99000, 99000), LocalDate.of(2099, 1, 1)))
                .isEmpty();
    }

    @Test
    void aReasonRidesAlongWithTheDeferral() {
        Deferral held =
                Deferral.grant(DR, GRANTED, AT, "structures", InspectionMethod.DVI, null, null, "doubler on order");
        assertThat(held.reason()).isEqualTo("doubler on order");
    }

    @Test
    void theBookGivesACrackAndAHoleAShorterLeashThanADent() {
        assertThat(Deferral.bookLimitFor(DamageKind.DENT)).isEqualTo(Deferral.STANDARD_DEFERRAL);
        assertThat(Deferral.bookLimitFor(DamageKind.CORROSION)).isEqualTo(Deferral.STANDARD_DEFERRAL);
        assertThat(Deferral.bookLimitFor(DamageKind.CRACK)).hasToString("100 FC / 150.0 FH / 30 DY");
        assertThat(Deferral.bookLimitFor(DamageKind.HOLE)).isEqualTo(Deferral.OPENING_DEFERRAL);
        assertThat(Deferral.bookLimitFor(DamageKind.PUNCTURE)).isEqualTo(Deferral.OPENING_DEFERRAL);
    }

    @Test
    void aCrackDeferredOnTheBookGetsTheCrackAllowance() {
        assertThat(Deferral.limitFor(DamageKind.CRACK, null, null, GRANTED)).isEqualTo(Deferral.CRACK_DEFERRAL);
        Interval tighter = Interval.parse("40 FC / 10 DY");
        assertThat(Deferral.limitFor(DamageKind.CRACK, tighter, null, GRANTED)).isEqualTo(tighter);
    }

    @Test
    void anEngineerCannotSetALongerLimitThanTheBookAllowsTheKind() {
        assertThatThrownBy(() -> Deferral.limitFor(DamageKind.CRACK, Interval.parse("500 FC"), null, GRANTED))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("for a crack, and 500 FC is past 100 FC");
        assertThatThrownBy(() -> Deferral.limitFor(DamageKind.DENT, Interval.parse("181 DY"), null, GRANTED))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("181 DY is past 180 DY");
        assertThat(Deferral.limitFor(DamageKind.DENT, Interval.parse("500 FC / 12 MO"), null, GRANTED))
                .isEqualTo(Interval.parse("500 FC / 12 MO"));
    }

    @Test
    void theKindsLimitIsStillPulledInToTheNextCheck() {
        Interval limited = Deferral.limitFor(DamageKind.CRACK, null, GRANTED.plusDays(12), GRANTED);
        assertThat(limited.days()).contains(12);
        assertThat(limited.cycles()).isEqualTo(Deferral.CRACK_DEFERRAL.cycles());
    }

    @Test
    void aCardIsGrantedOnTheAllowanceForItsOwnKind() {
        Finding crack = Sample.cardWith()
                .kind(DamageKind.CRACK)
                .geometry(new Crack(Length.ofInches("0.400"), Angle.ofDegrees(90)))
                .origin(DamageOrigin.FATIGUE)
                .build();
        Deferral held = Deferral.grant(crack, GRANTED, AT, "structures", InspectionMethod.HFEC, null, null, "");
        assertThat(held.against()).isEqualTo(crack.reference());
        assertThat(held.limit()).isEqualTo(Deferral.CRACK_DEFERRAL);
        assertThat(held.due().cycles().orElseThrow().count()).isEqualTo(13900);
    }

    @Test
    void aTightenedLimitThatIsLongerOnAClockIsRefused() {
        assertThatThrownBy(() -> deferral().tightenedTo(Interval.parse("600 FC"), "chief engineer"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not a longer one, and 600 FC is past 500 FC");
        assertThat(deferral()
                        .tightenedTo(Interval.parse("12 MO"), "chief engineer")
                        .limit())
                .isEqualTo(Interval.parse("12 MO"));
    }
}
