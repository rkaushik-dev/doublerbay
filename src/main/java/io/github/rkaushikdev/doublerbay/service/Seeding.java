package io.github.rkaushikdev.doublerbay.service;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.DiscoverySource;
import io.github.rkaushikdev.doublerbay.fleet.Series;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import java.time.LocalDate;
import java.util.List;

/**
 * A small fleet with a plausible history on it, for a demonstration.
 *
 * <p>Every service like this ends up with a seed script, and most of them seed one aeroplane with one
 * dent, which demonstrates nothing. The interesting reports are the ones that need a shape: a tail
 * that is grounded, a tail that is clean, a panel with three repairs on it, a corrosion location that
 * has come back.
 *
 * <p>So this builds four aeroplanes with different problems on purpose. It is not random and it takes
 * no seed value, because a demonstration that shows something different every time is one nobody can
 * write a script against.
 *
 * <p>It refuses to run against a database that already holds a fleet. Seeding on top of real records
 * is how a demonstration ends up in a fleet report.
 */
public final class Seeding {

    /** Four tails, four different problems. */
    public static final List<SeedTail> FLEET = List.of(
            new SeedTail("5127", "N412VD", 1140, LocalDate.of(2016, 4, 12)),
            new SeedTail("5240", "N908QA", 1204, LocalDate.of(2017, 9, 3)),
            new SeedTail("5388", "N551KE", 1310, LocalDate.of(2019, 2, 18)),
            new SeedTail("5461", "N720LM", 1388, LocalDate.of(2021, 11, 30)));

    /** The day of every tail's first reading after delivery. */
    public static final LocalDate OPENED = LocalDate.of(2024, 1, 1);

    /** The day of every tail's latest reading, and the day the seed answers as of unless told. */
    public static final LocalDate LATEST = LocalDate.of(2025, 3, 1);

    private Seeding() {}

    private static Length inches(String text) {
        return Length.ofInches(text);
    }

    private static Usage flown(int cycles, long hours) {
        return new Usage(new Cycles(cycles), Hours.ofHours(hours));
    }

    private static FuselagePoint cabin(int frame, int stringer) {
        return cabin(frame, stringer, "0.000");
    }

    private static FuselagePoint cabin(int frame, int stringer, String down) {
        return new FuselagePoint(new Frame(frame), inches("10.000"), new Stringer(stringer, Side.LEFT), inches(down));
    }

    private static Aircraft aircraftOf(SeedTail tail) {
        return new Aircraft(
                new Serial(tail.serial()),
                Registration.parse(tail.marks()),
                Series.STANDARD,
                tail.line(),
                tail.delivered());
    }

    /** Put the four tails on with two readings each, so every one has a rate. */
    private static void enrol(UnitOfWork unit) {
        for (int step = 0; step < FLEET.size(); step++) {
            Aircraft held = aircraftOf(FLEET.get(step));
            unit.aircraft().add(held);
            UtilisationLog log = UtilisationLog.opened(held.serial(), held.delivered())
                    .posted(new Snapshot(OPENED, flown(11000 + step * 400, 19000L + step * 700)))
                    .posted(new Snapshot(LATEST, flown(13800 + step * 400, 24200L + step * 700)));
            unit.aircraft().openLog(log);
        }
    }

    private static Finding card(
            FindingId reference,
            String serial,
            DamageKind kind,
            Geometry geometry,
            FuselagePoint at,
            LocalDate found,
            DamageOrigin origin) {
        return new Finding(
                reference,
                new Serial(serial),
                kind,
                geometry,
                at,
                found,
                DiscoverySource.SCHEDULED_CHECK,
                origin,
                flown(13800, 24200));
    }

    private static Dent dent(String depth) {
        return new Dent(inches(depth), inches("3.000"), inches("2.000"));
    }

    private static Corrosion rust(String depth) {
        return new Corrosion(inches("2.000"), inches("1.000"), inches(depth));
    }

    /** Build the demonstration fleet as of its latest readings. */
    public static Seeded seed(UnitOfWork unit) {
        return seed(unit, LATEST);
    }

    /** Build the demonstration fleet, refusing a database that already holds one. */
    public static Seeded seed(UnitOfWork unit, LocalDate today) {
        if (!unit.aircraft().all().isEmpty()) {
            throw new BadReading("this database already holds a fleet, seed an empty one");
        }

        enrol(unit);
        unit.flush();
        Book book = Book.v320();
        TypeCatalogue catalogue = new TypeCatalogue();

        int cards = 0;
        // 5127 carries a shallow dent and a deep one, so it has both answers on it.
        List<String> depths = List.of("0.010", "0.220");
        for (int step = 1; step <= depths.size(); step++) {
            Intake.takeIn(
                    unit,
                    book,
                    catalogue,
                    card(
                            new FindingId(2025, step),
                            "5127",
                            DamageKind.DENT,
                            dent(depths.get(step - 1)),
                            cabin(15 + step * 4, 6),
                            today.minusDays(20L * step),
                            DamageOrigin.IMPACT));
            cards++;
        }

        // 5240 has corrosion that came back in the same place, which is a level two. It sits inside one
        // bay so the second finding is the one that makes it a level two, not the stringer under it.
        List<LocalDate> found = List.of(LocalDate.of(2023, 6, 1), LocalDate.of(2025, 1, 12));
        for (int step = 3; step < 3 + found.size(); step++) {
            Intake.takeIn(
                    unit,
                    book,
                    catalogue,
                    card(
                            new FindingId(2025, step),
                            "5240",
                            DamageKind.CORROSION,
                            rust("0.030"),
                            cabin(22, 9, "4.000"),
                            found.get(step - 3),
                            DamageOrigin.ENVIRONMENT));
            cards++;
        }

        // 5388 has one dent, deferred, which puts it on the due list.
        FindingId reference = new FindingId(2025, 5);
        Finding dented = card(
                reference,
                "5388",
                DamageKind.DENT,
                dent("0.120"),
                cabin(30, 4),
                LocalDate.of(2025, 2, 2),
                DamageOrigin.HANDLING);
        Intake.takeIn(unit, book, catalogue, dented);
        cards++;
        unit.flush();
        DispositionRecord record = unit.dispositions().get(reference);
        record = record.movedTo(DispositionState.ASSESSED, LocalDate.of(2025, 2, 3), "a.okonkwo");
        unit.dispositions().save(record.movedTo(DispositionState.DEFERRED, LocalDate.of(2025, 2, 4), "j.mercer"));
        unit.deferrals()
                .grant(Deferral.grant(
                        dented,
                        LocalDate.of(2025, 2, 4),
                        flown(14000, 24600),
                        "j.mercer",
                        InspectionMethod.DVI,
                        Interval.parse("300 FC / 90 DY"),
                        null,
                        "awaiting a doubler"));

        // 5461 is left clean, because every report needs a tail with nothing on it.
        return new Seeded(FLEET.size(), cards, 1);
    }
}
