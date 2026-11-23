package io.github.rkaushikdev.doublerbay.measure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IntervalTest {

    private static Usage flown(int cycles, int hours) {
        return new Usage(new Cycles(cycles), Hours.ofHours(hours));
    }

    @Test
    void anIntervalNeedsAtLeastOneClock() {
        assertThatThrownBy(() -> Interval.of(null, null, null, null))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least one clock");
    }

    @Test
    void aClockSetToNothingIsRefused() {
        assertThatThrownBy(() -> Interval.ofMonths(0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("in months counts at least one");
        assertThatThrownBy(() -> Interval.ofDays(0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("in days counts at least one");
        assertThatThrownBy(() -> Interval.ofCycles(new Cycles(0)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("in cycles counts at least one");
    }

    @Test
    void theManualFormReadsBack() {
        Interval parsed = Interval.parse("3000 FC / 4500 FH / 24 MO");
        assertThat(parsed.cycles()).contains(new Cycles(3000));
        assertThat(parsed.hours()).contains(Hours.ofHours(4500));
        assertThat(parsed.months()).contains(24);
        assertThat(parsed.days()).isEmpty();
        assertThat(parsed).hasToString("3000 FC / 4500.0 FH / 24 MO");
    }

    @Test
    void aSingleTermIsAnInterval() {
        assertThat(Interval.parse("10 DY").days()).contains(10);
        assertThat(Interval.parse("50fc").cycles()).contains(new Cycles(50));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "3000", "3000 XX", "FC 3000", "3000 FC / oops"})
    void theParserRefusesAnythingElse(String text) {
        assertThatThrownBy(() -> Interval.parse(text)).isInstanceOf(BadReading.class);
    }

    @Test
    void addingMonthsLandsInsideAShortMonth() {
        assertThat(Interval.addMonths(LocalDate.of(2025, 1, 31), 1)).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(Interval.addMonths(LocalDate.of(2024, 1, 31), 1)).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(Interval.addMonths(LocalDate.of(2025, 3, 15), 24)).isEqualTo(LocalDate.of(2027, 3, 15));
        assertThat(Interval.addMonths(LocalDate.of(2025, 12, 1), 1)).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void anIntervalLandsOnAReadingAndADate() {
        DueAt due = Interval.parse("3000 FC / 4500 FH / 24 MO").dueFrom(flown(12000, 20000), LocalDate.of(2025, 3, 15));
        assertThat(due.cycles()).contains(new Cycles(15000));
        assertThat(due.hours()).contains(Hours.ofHours(24500));
        assertThat(due.on()).contains(LocalDate.of(2027, 3, 15));
    }

    @Test
    void theTighterOfDaysAndMonthsWins() {
        DueAt due = Interval.ofMonths(1).withDays(10).dueFrom(Usage.zero(), LocalDate.of(2025, 3, 1));
        assertThat(due.on()).contains(LocalDate.of(2025, 3, 11));
    }

    @Test
    void aClockTheIntervalIsSilentOnStaysSilent() {
        DueAt due = Interval.parse("500 FC").dueFrom(flown(1000, 2000), LocalDate.of(2025, 3, 15));
        assertThat(due.cycles()).contains(new Cycles(1500));
        assertThat(due.hours()).isEmpty();
        assertThat(due.on()).isEmpty();
    }

    @Test
    void whicheverComesFirstFallsDueOnAnyOneOfThem() {
        DueAt due = DueAt.of(new Cycles(15000), Hours.ofHours(24500), LocalDate.of(2027, 3, 15));
        assertThat(due.isDue(flown(15000, 20000), LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(due.isDue(flown(12000, 24500), LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(due.isDue(flown(12000, 20000), LocalDate.of(2027, 3, 15))).isTrue();
        assertThat(due.isDue(flown(14999, 24499), LocalDate.of(2027, 3, 14))).isFalse();
    }

    @Test
    void aDueWithNothingOnItNeverFallsDue() {
        assertThat(DueAt.none().isDue(flown(90000, 90000), LocalDate.of(2099, 1, 1)))
                .isFalse();
        assertThat(DueAt.none()).hasToString("no limit");
    }

    @Test
    void whatIsLeftOnEachClock() {
        DueAt due = DueAt.of(new Cycles(15000), null, LocalDate.of(2026, 1, 11));
        assertThat(due.remainingCycles(flown(12000, 0))).contains(new Cycles(3000));
        assertThat(due.remainingDays(LocalDate.of(2026, 1, 1))).contains(10);
        assertThat(due.remainingCycles(flown(16000, 0))).contains(new Cycles(0));
        assertThat(due.remainingDays(LocalDate.of(2026, 2, 1))).contains(0);
    }

    @Test
    void aClockThatWasNeverSetHasNothingLeftOnIt() {
        DueAt due = DueAt.of(new Cycles(15000), null, null);
        assertThat(due.remainingDays(LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(DueAt.of(null, null, LocalDate.of(2026, 1, 1)).remainingCycles(flown(1, 1)))
                .isEmpty();
    }

    @Test
    void anEscalationMovesEveryClockTheIntervalNames() {
        Interval escalated = Interval.parse("3000 FC / 4500 FH / 24 MO").scaled("1.5");
        assertThat(escalated.cycles()).contains(new Cycles(4500));
        assertThat(escalated.hours()).contains(Hours.ofHours(6750));
        assertThat(escalated.months()).contains(36);
        assertThat(escalated.days()).isEmpty();
    }

    @Test
    void scalingToNothingIsRefused() {
        assertThatThrownBy(() -> Interval.ofMonths(24).scaled("0"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("scaled to nothing");
    }

    @Test
    void aDueReadsAsTheThreeWaysItCanLand() {
        DueAt due = DueAt.of(new Cycles(15000), null, LocalDate.of(2027, 3, 15));
        assertThat(due).hasToString("15000 FC or 2027-03-15");
    }

    @Test
    void anIntervalNamesTheFirstClockItRunsPastAnotherOn() {
        Interval book = Interval.parse("500 FC / 750 FH / 6 MO / 180 DY");
        assertThat(Interval.parse("400 FC / 800 FH").pastOn(book)).contains("800.0 FH is past 750.0 FH");
        assertThat(Interval.parse("7 MO").pastOn(book)).contains("7 MO is past 6 MO");
        assertThat(Interval.parse("200 DY").pastOn(book)).contains("200 DY is past 180 DY");
        assertThat(Interval.parse("500 FC / 750 FH").pastOn(book)).isEmpty();
        assertThat(Interval.parse("9000 FC").pastOn(Interval.parse("30 DY"))).isEmpty();
    }
}
