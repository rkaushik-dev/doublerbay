package io.github.rkaushikdev.doublerbay.measure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How much flying, or how much time, a limit allows, against up to three clocks at once.
 *
 * <p>Nothing structural is limited by a single number. A repeat inspection reads three thousand
 * cycles or four thousand five hundred hours or twenty four months, whichever comes first, and a
 * deferral reads ten days or fifty cycles. An interval carries whichever of the clocks the manual
 * set and stays silent on the others. Turning it into a {@link DueAt} is the only place the clocks
 * meet.
 */
public record Interval(
        Optional<Cycles> cycles, Optional<Hours> hours, Optional<Integer> months, Optional<Integer> days) {

    private static final Pattern TERM = Pattern.compile("([0-9]+)\\s*(FC|FH|MO|DY)", Pattern.CASE_INSENSITIVE);

    /** An interval against at least one clock, every clock it names counting at least one. */
    public Interval {
        Objects.requireNonNull(cycles, "cycles");
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(months, "months");
        Objects.requireNonNull(days, "days");
        if (cycles.isEmpty() && hours.isEmpty() && months.isEmpty() && days.isEmpty()) {
            throw new BadReading("an interval has to be against at least one clock");
        }
        if (cycles.isPresent() && cycles.get().count() < 1) {
            throw new BadReading("an interval in cycles counts at least one");
        }
        if (hours.isPresent() && hours.get().minutes() < 1) {
            throw new BadReading("an interval in hours counts at least one minute");
        }
        if (months.isPresent() && months.get() < 1) {
            throw new BadReading("an interval in months counts at least one");
        }
        if (days.isPresent() && days.get() < 1) {
            throw new BadReading("an interval in days counts at least one");
        }
    }

    /**
     * The first clock this interval runs past another on, in words, looking only at the clocks both of
     * them state: cycles, then hours, then months, then days.
     */
    public Optional<String> pastOn(Interval other) {
        if (cycles.isPresent()
                && other.cycles.isPresent()
                && cycles.get().count() > other.cycles.get().count()) {
            return Optional.of(cycles.get() + " is past " + other.cycles.get());
        }
        if (hours.isPresent()
                && other.hours.isPresent()
                && hours.get().minutes() > other.hours.get().minutes()) {
            return Optional.of(hours.get() + " is past " + other.hours.get());
        }
        if (months.isPresent() && other.months.isPresent() && months.get() > other.months.get()) {
            return Optional.of(months.get() + " MO is past " + other.months.get() + " MO");
        }
        if (days.isPresent() && other.days.isPresent() && days.get() > other.days.get()) {
            return Optional.of(days.get() + " DY is past " + other.days.get() + " DY");
        }
        return Optional.empty();
    }

    /** An interval from whichever clocks are given; a null clock is one the interval is silent on. */
    public static Interval of(Cycles cycles, Hours hours, Integer months, Integer days) {
        return new Interval(
                Optional.ofNullable(cycles),
                Optional.ofNullable(hours),
                Optional.ofNullable(months),
                Optional.ofNullable(days));
    }

    /** An interval in cycles alone. */
    public static Interval ofCycles(Cycles cycles) {
        return of(Objects.requireNonNull(cycles, "cycles"), null, null, null);
    }

    /** An interval in flight hours alone. */
    public static Interval ofHours(Hours hours) {
        return of(null, Objects.requireNonNull(hours, "hours"), null, null);
    }

    /** An interval in calendar months alone. */
    public static Interval ofMonths(int months) {
        return of(null, null, months, null);
    }

    /** An interval in calendar days alone. */
    public static Interval ofDays(int days) {
        return of(null, null, null, days);
    }

    /** The same interval, also against a number of cycles. */
    public Interval withCycles(Cycles count) {
        return new Interval(Optional.of(count), hours, months, days);
    }

    /** The same interval, also against flight hours. */
    public Interval withHours(Hours time) {
        return new Interval(cycles, Optional.of(time), months, days);
    }

    /** The same interval, also against calendar months. */
    public Interval withMonths(int count) {
        return new Interval(cycles, hours, Optional.of(count), days);
    }

    /** The same interval, also against calendar days. */
    public Interval withDays(int count) {
        return new Interval(cycles, hours, months, Optional.of(count));
    }

    /** Read the form the manual prints, terms separated by a slash: {@code 3000 FC / 24 MO}. */
    public static Interval parse(String text) {
        Cycles cycles = null;
        Hours hours = null;
        Integer months = null;
        Integer days = null;
        List<String> terms = new ArrayList<>();
        for (String part : text.split("/", -1)) {
            String term = Text.strip(part);
            if (!term.isEmpty()) {
                terms.add(term);
            }
        }
        if (terms.isEmpty()) {
            throw new BadReading(Literal.of(text) + " carries no interval");
        }
        for (String term : terms) {
            Matcher match = TERM.matcher(term);
            if (!match.matches()) {
                throw new BadReading(Literal.of(term) + " is not an interval term");
            }
            int count = Integer.parseInt(match.group(1));
            switch (Text.upper(match.group(2))) {
                case "FC" -> cycles = new Cycles(count);
                case "FH" -> hours = Hours.ofHours(count);
                case "MO" -> months = count;
                default -> days = count;
            }
        }
        return of(cycles, hours, months, days);
    }

    /** Calendar arithmetic that lands on the last day of a short month. */
    public static LocalDate addMonths(LocalDate start, int months) {
        int total = start.getMonthValue() - 1 + months;
        int year = start.getYear() + Math.floorDiv(total, 12);
        int month = Math.floorMod(total, 12) + 1;
        int day = Math.min(start.getDayOfMonth(), YearMonth.of(year, month).lengthOfMonth());
        return LocalDate.of(year, month, day);
    }

    /** An escalation or a cut, applied to every clock the interval names. */
    public Interval scaled(BigDecimal factor) {
        if (factor.signum() <= 0) {
            throw new BadReading("an interval cannot be scaled to nothing");
        }
        Function<Integer, Integer> scale = value -> Decimals.toInt(Decimals.times(factor, value));
        return new Interval(
                cycles.map(count -> new Cycles(scale.apply(count.count()))),
                hours.map(time -> new Hours(scale.apply(time.minutes()))),
                months.map(scale),
                days.map(scale));
    }

    /** An escalation or a cut written as decimal text. */
    public Interval scaled(String factor) {
        return scaled(Decimals.parse(factor));
    }

    /** Where the limit lands, given where the tail is now. The tighter of months and days wins. */
    public DueAt dueFrom(Usage flown, LocalDate on) {
        LocalDate byDate = months.map(count -> addMonths(on, count)).orElse(null);
        if (days.isPresent()) {
            LocalDate candidate = on.plusDays(days.get());
            byDate = byDate == null || candidate.isBefore(byDate) ? candidate : byDate;
        }
        return DueAt.of(
                cycles.map(flown.cycles()::plus).orElse(null),
                hours.map(flown.hours()::plus).orElse(null),
                byDate);
    }

    /** {@code 3000 FC / 4500.0 FH / 24 MO}, naming only the clocks that are set. */
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        cycles.ifPresent(count -> parts.add(count.count() + " FC"));
        hours.ifPresent(time -> parts.add(time.hours().toPlainString() + " FH"));
        months.ifPresent(count -> parts.add(count + " MO"));
        days.ifPresent(count -> parts.add(count + " DY"));
        return String.join(" / ", parts);
    }
}
