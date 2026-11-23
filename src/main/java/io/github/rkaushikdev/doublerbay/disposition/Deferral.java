package io.github.rkaushikdev.doublerbay.disposition;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A decision to keep flying with a damage, and the limit on it.
 *
 * <p>A deferral is a decision to leave damage on an aeroplane that is going to keep flying, and it
 * is only defensible with a limit on it. So many cycles, so many hours, so many days, whichever
 * comes first, and a re inspection at the end of it. A deferral without a limit is not a deferral,
 * it is a decision nobody wrote down.
 *
 * <p>The limit is the tightest of three things and the third is the one people forget. The book's
 * own deferral allowance for the kind of damage, whatever the engineer set on this particular card,
 * which may be shorter than the book and never longer, and the next scheduled check the aeroplane is
 * going into, because there is no sense deferring past an opportunity to fix it.
 *
 * <p>An expired deferral is not an error. It is an aeroplane flying with damage outside its limit,
 * which is a grounding item, and the sweep that finds them is the most important report on the
 * desk.
 */
public record Deferral(
        FindingId against,
        LocalDate granted,
        Usage atUsage,
        Interval limit,
        InspectionMethod reInspectWith,
        String by,
        String reason) {

    /** What the book allows without an engineer setting something tighter. */
    public static final Interval STANDARD_DEFERRAL = Interval.parse("500 FC / 750 FH / 180 DY");

    /** What the book allows a crack, which keeps growing between one look at it and the next. */
    public static final Interval CRACK_DEFERRAL = Interval.parse("100 FC / 150 FH / 30 DY");

    /** What the book allows a hole or a puncture, which has no skin left to carry load across it. */
    public static final Interval OPENING_DEFERRAL = Interval.parse("50 FC / 75 FH / 10 DY");

    /** A deferral granted by somebody, with a reason that may be empty. */
    public Deferral {
        Objects.requireNonNull(against, "against");
        Objects.requireNonNull(granted, "granted");
        Objects.requireNonNull(atUsage, "atUsage");
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(reInspectWith, "reInspectWith");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(reason, "reason");
        if (Text.isBlank(by)) {
            throw new BadReading(against + ": a deferral is granted by somebody");
        }
    }

    /** A deferral with no reason written on it. */
    public Deferral(
            FindingId against,
            LocalDate granted,
            Usage atUsage,
            Interval limit,
            InspectionMethod reInspectWith,
            String by) {
        this(against, granted, atUsage, limit, reInspectWith, by, "");
    }

    /** Where the limit lands, counted from the day and the reading it was granted at. */
    public DueAt due() {
        return limit.dueFrom(atUsage, granted);
    }

    /** Whether the aeroplane has flown past the limit on any of its clocks. */
    public boolean isExpired(Usage flown, LocalDate today) {
        return due().isDue(flown, today);
    }

    /** The days left on the calendar clock, none once it has run out, when the limit has one. */
    public Optional<Integer> daysLeft(LocalDate today) {
        return due().remainingDays(today);
    }

    /** A shorter limit than the one granted, which an engineer may always set, keeping the reason. */
    public Deferral tightenedTo(Interval shorter, String engineer) {
        return tightenedTo(shorter, engineer, "");
    }

    /**
     * A shorter limit than the one granted, which an engineer may always set. The day and the
     * reading it was granted at stay, and an empty reason keeps the one already written.
     *
     * @throws BadReading when the new limit runs past the granted one on a clock both state
     */
    public Deferral tightenedTo(Interval shorter, String engineer, String why) {
        shorter.pastOn(limit).ifPresent(clock -> {
            throw new BadReading(against + ": a tightened limit is not a longer one, and " + clock);
        });
        return new Deferral(against, granted, atUsage, shorter, reInspectWith, engineer, why.isEmpty() ? reason : why);
    }

    /** The book's limit, as it stands. */
    public static Interval limitFor() {
        return limitFor(null, null, null);
    }

    /**
     * The book's own allowance for a kind of damage.
     *
     * <p>Most of what the desk defers is a dent or a blend waiting for a slot, and those get the
     * standard allowance. A crack is flown against a much shorter one because it grows while it waits,
     * and a hole through the skin shorter again.
     */
    public static Interval bookLimitFor(DamageKind kind) {
        return switch (kind) {
            case CRACK -> CRACK_DEFERRAL;
            case HOLE, PUNCTURE -> OPENING_DEFERRAL;
            default -> STANDARD_DEFERRAL;
        };
    }

    /**
     * The tightest of the book's allowance for this kind of damage, the engineer, and the next check.
     *
     * <p>An engineer may set a shorter limit than the book and never a longer one. A limit that runs
     * past the book's on any clock both of them state is refused rather than quietly cut back, because
     * the engineer who wrote it meant something by it and should be told.
     *
     * @throws BadReading when the engineer's limit runs past the book's on a clock
     */
    public static Interval limitFor(DamageKind kind, Interval engineerSet, LocalDate nextCheck, LocalDate granted) {
        Interval book = bookLimitFor(kind);
        if (engineerSet != null) {
            engineerSet.pastOn(book).ifPresent(clock -> {
                throw new BadReading("an engineer tightens the book's limit for a " + kind + ", and " + clock);
            });
        }
        return tightest(book, engineerSet, nextCheck, granted);
    }

    /** The engineer's limit, or the book's when the engineer set none. */
    public static Interval limitFor(Interval engineerSet) {
        return limitFor(engineerSet, null, null);
    }

    /**
     * The tightest of the book, the engineer, and the next check the tail goes into.
     *
     * <p>A null engineer limit is the book's. The next check only bites when both it and the day
     * the deferral is granted are given: the limit then runs in days to that check, keeping its
     * cycles and hours, unless its own days are already as tight.
     */
    public static Interval limitFor(Interval engineerSet, LocalDate nextCheck, LocalDate granted) {
        return tightest(STANDARD_DEFERRAL, engineerSet, nextCheck, granted);
    }

    /** The engineer's limit or else the book's, pulled in to the next check when there is one. */
    private static Interval tightest(Interval book, Interval engineerSet, LocalDate nextCheck, LocalDate granted) {
        Interval limit = engineerSet != null ? engineerSet : book;
        if (nextCheck == null || granted == null) {
            return limit;
        }
        long days = ChronoUnit.DAYS.between(granted, nextCheck);
        if (days < 1) {
            throw new BadReading("a deferral to a check that has already started is not a deferral");
        }
        if (limit.days().isPresent() && limit.days().get() <= days) {
            return limit;
        }
        return new Interval(limit.cycles(), limit.hours(), Optional.empty(), Optional.of(Math.toIntExact(days)));
    }

    /** Grant a deferral on the book's limit, to be re inspected with a detailed visual. */
    public static Deferral grant(FindingId against, LocalDate granted, Usage atUsage, String by) {
        return grant(against, granted, atUsage, by, InspectionMethod.DVI, null, null, "");
    }

    /**
     * Grant a deferral with the tightest limit the three sources allow.
     *
     * <p>A null engineer limit leaves the book's, and a null next check leaves no check to pull it in.
     */
    public static Deferral grant(
            FindingId against,
            LocalDate granted,
            Usage atUsage,
            String by,
            InspectionMethod method,
            Interval engineerSet,
            LocalDate nextCheck,
            String reason) {
        return new Deferral(against, granted, atUsage, limitFor(engineerSet, nextCheck, granted), method, by, reason);
    }

    /**
     * Grant a deferral on a card, held to the book's allowance for the card's kind of damage.
     *
     * <p>A null engineer limit leaves the book's for the kind, and a null next check leaves no check
     * to pull it in.
     *
     * @throws BadReading when the engineer's limit runs past the book's for the kind
     */
    public static Deferral grant(
            Finding card,
            LocalDate granted,
            Usage atUsage,
            String by,
            InspectionMethod method,
            Interval engineerSet,
            LocalDate nextCheck,
            String reason) {
        return new Deferral(
                card.reference(),
                granted,
                atUsage,
                limitFor(card.kind(), engineerSet, nextCheck, granted),
                method,
                by,
                reason);
    }

    /** Every deferral the aeroplane has flown past, which is a grounding list. */
    public static List<Deferral> expired(Iterable<Deferral> deferrals, Usage flown, LocalDate today) {
        List<Deferral> found = new ArrayList<>();
        for (Deferral one : deferrals) {
            if (one.isExpired(flown, today)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    /** {@code DR-2025-004182 deferred to 14300 FC or 24950.0 FH or 2025-09-10, detailed visual}. */
    @Override
    public String toString() {
        return against + " deferred to " + due() + ", " + reInspectWith;
    }
}
