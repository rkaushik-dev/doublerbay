package io.github.rkaushikdev.doublerbay.cli;

import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.repair.DoublerPlan;
import io.github.rkaushikdev.doublerbay.repair.RepairPair;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.repair.RepairScheme;
import io.github.rkaushikdev.doublerbay.repair.Schemes;
import io.github.rkaushikdev.doublerbay.report.RepairWeight;
import io.github.rkaushikdev.doublerbay.service.Embodiment;
import io.github.rkaushikdev.doublerbay.service.Repairs;
import io.github.rkaushikdev.doublerbay.store.StoreContext;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.tolerance.Schedules;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Drawing and fitting a repair from the terminal.
 *
 * <p>The shop wants the drawing before it wants anything written down, so the draw command works out
 * the doubler and prints it without touching the database. Somebody reads the numbers, argues about
 * the gauge, and only then runs fit.
 *
 * <p>That split is the whole design here. A command that drew and committed in one step would mean
 * every question about what a repair would look like left a repair on the register, and the fleet
 * would accumulate repairs nobody fitted.
 */
public final class Repairing {

    /** The page a repair is drawn off unless another is named. */
    public static final String DEFAULT_PAGE = "53-40-01";

    private Repairing() {}

    /** Read a measurement the way it is written on the sheet, in inches. */
    public static Length asInches(String text) {
        return Length.ofInches(text);
    }

    /** The drawing laid out the way the shop reads it off the sheet. */
    public static List<List<String>> drawingRows(RepairScheme scheme, Placement placement) {
        DoublerPlan plan = scheme.doubler();
        List<List<String>> rows = new ArrayList<>(List.of(
                List.of("part", placement.part().toString()),
                List.of("region", placement.region().toString()),
                List.of("skin", inches(placement.thickness())),
                List.of("form", scheme.form().value()),
                List.of("category", scheme.category().value()),
                List.of("sealed", scheme.sealed() ? "yes" : "no"),
                List.of("page", scheme.reference().toString()),
                List.of("material", plan.material().toString()),
                List.of("gauge", inches(plan.gauge())),
                List.of("along", inches(plan.along())),
                List.of("around", inches(plan.around())),
                List.of("weight", plan.weight().toPlainString() + " lb"),
                List.of("fastener", "dash " + plan.size().dash()),
                List.of("fasteners", String.valueOf(plan.fastenerCount())),
                List.of("rows each side", String.valueOf(plan.rowsEachSide())),
                List.of("per row", String.valueOf(plan.perRow())),
                List.of("pitch", inches(plan.pitch())),
                List.of("row spacing", inches(plan.rowSpacing())),
                List.of("edge distance", inches(plan.edgeDistance()))));
        scheme.filler().ifPresent(filler -> rows.add(List.of("filler", inches(filler))));
        if (!scheme.category().isPermanent()) {
            rows.add(List.of(
                    "replace by", Schedules.temporaryLimitFor(placement.panelKind()) + ", whichever comes first"));
        }
        Schemes.memberUnder(plan, placement)
                .ifPresent(
                        member -> rows.add(List.of("over", member + ", joggle over it or take it into the pattern")));
        return rows;
    }

    private static String inches(Length length) {
        return Decimals.text(length.inches());
    }

    /** A repair worked out for a hole against a card, where it lands, and the tail it is on. */
    record Drawn(RepairScheme scheme, Placement placement, Serial serial) {
        Drawn {
            Objects.requireNonNull(scheme, "scheme");
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(serial, "serial");
        }
    }

    /**
     * Work out the repair for a hole against a card, without writing anything. A null or nought dash
     * number is the standard fastener.
     */
    static Drawn drawnFor(
            UnitOfWork unit,
            FindingId reference,
            String along,
            String around,
            String page,
            Integer dash,
            boolean temporary,
            LocalDate on) {
        Finding finding = unit.findings().latest(reference);
        TailContext tail = StoreContext.contextFor(unit, new TypeCatalogue(), finding.serial(), on);
        Placement placement = Placement.place(tail, finding);
        RepairScheme scheme = Schemes.selectScheme(
                asInches(along),
                asInches(around),
                placement,
                Reference.parse(page),
                dash != null && dash != 0 ? new FastenerSize(dash) : null,
                temporary);
        return new Drawn(scheme, placement, finding.serial());
    }

    /** Repairs: draw one, fit it, take it off. */
    @Command(
            name = "repair",
            customSynopsis = "doublerbay repair [OPTIONS] COMMAND [ARGS]...",
            description = "Repairs: draw one, fit it, take it off.",
            subcommands = {RepairDraw.class, RepairFit.class, RepairList.class, RepairRemove.class})
    static final class Group {}

    /** Work out the repair for a hole and print it, writing nothing. */
    @Command(
            name = "draw",
            customSynopsis = "doublerbay repair draw [OPTIONS] REFERENCE",
            description = "Work out the repair for a hole and print it, writing nothing.")
    static final class RepairDraw extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Option(names = "--along", required = true, description = "how long the cut out is, in inches")
        String along;

        @Option(names = "--around", required = true, description = "how wide the cut out is, in inches")
        String around;

        @Option(names = "--on", required = true, description = "yyyy-mm-dd")
        String onDay;

        @Option(names = "--page", defaultValue = DEFAULT_PAGE, showDefaultValue = Visibility.ALWAYS)
        String page;

        @Option(names = "--fastener", description = "dash number")
        Integer dash;

        @Option(names = "--temporary")
        boolean temporary;

        @Override
        public Integer call() {
            Drawn drawn = work(unit -> {
                FindingId held = FindingId.parse(reference);
                LocalDate day = Output.asDate(onDay);
                return drawnFor(unit, held, along, around, page, dash, temporary, day);
            });
            say(Output.columns(drawingRows(drawn.scheme(), drawn.placement())));
            return Main.OK;
        }
    }

    /** Fit a repair, close the card behind it and lift its deferral. */
    @Command(
            name = "fit",
            customSynopsis = "doublerbay repair fit [OPTIONS] REFERENCE",
            description = "Fit a repair, close the card behind it and lift its deferral.")
    static final class RepairFit extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Option(names = "--sequence", required = true, description = "the repair number in the chapter")
        int sequence;

        @Option(names = "--along", required = true)
        String along;

        @Option(names = "--around", required = true)
        String around;

        @Option(names = "--centre-along", required = true, description = "body station of the centre")
        String centreAlong;

        @Option(names = "--centre-around", required = true, description = "offset around from the centre line")
        String centreAround;

        @Option(names = "--on", required = true, description = "the day it went on")
        String onDay;

        @Option(names = "--by", required = true)
        String by;

        @Option(names = "--page", defaultValue = DEFAULT_PAGE, showDefaultValue = Visibility.ALWAYS)
        String page;

        @Option(names = "--fastener")
        Integer dash;

        @Option(names = "--temporary")
        boolean temporary;

        @Override
        public Integer call() {
            FindingId held = FindingId.parse(reference);
            LocalDate day = Output.asDate(onDay);
            Embodiment answer = work(unit -> {
                Drawn drawn = drawnFor(unit, held, along, around, page, dash, temporary, day);
                RepairRecord record = new RepairRecord(
                        new RepairId(drawn.placement().chapter().value(), sequence),
                        drawn.placement().part(),
                        held,
                        drawn.scheme(),
                        new Offset(asInches(centreAlong), asInches(centreAround)),
                        day,
                        unit.aircraft().logFor(drawn.serial()).at(day));
                return Repairs.embody(unit, record, drawn.serial(), by, day);
            });
            say(answer.toString());
            for (RepairPair clash : answer.clashes()) {
                say("too close: " + clash.one() + " and " + clash.other());
            }
            return Main.OK;
        }
    }

    /** Every repair on one tail. */
    @Command(
            name = "list",
            customSynopsis = "doublerbay repair list [OPTIONS]",
            description = "Every repair on one tail.")
    static final class RepairList extends Step {

        @Option(names = "--tail", required = true)
        String serial;

        @Option(names = "--all", description = "include the ones taken off")
        boolean everything;

        @Override
        public Integer call() {
            List<RepairRecord> fitted = work(unit -> {
                Serial held = new Serial(serial);
                unit.aircraft().get(held);
                return unit.repairs().onTail(held, !everything);
            });
            if (fitted.isEmpty()) {
                say(Output.sayNothingFound("fitted to " + serial));
                return Main.OK;
            }
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("reference", "part", "against", "category", "lb", "fitted", "off"));
            fitted.stream().map(RepairList::row).forEach(rows::add);
            say(Output.columns(rows));
            say(RepairWeight.of(fitted).toString());
            return Main.OK;
        }

        /** One repair as a line of the list, its plate weight beside its category. */
        static List<String> row(RepairRecord one) {
            return List.of(
                    one.reference().toString(),
                    one.part().toString(),
                    one.against().toString(),
                    one.scheme().category().name(),
                    one.scheme().doubler().weight().toPlainString(),
                    one.embodied().toString(),
                    one.removed().map(LocalDate::toString).orElse(""));
        }
    }

    /** Take a repair off, which does not reopen the card it answered. */
    @Command(
            name = "remove",
            customSynopsis = "doublerbay repair remove [OPTIONS] CHAPTER SEQUENCE",
            description = "Take a repair off, which does not reopen the card it answered.")
    static final class RepairRemove extends Step {

        @Parameters(index = "0", paramLabel = "CHAPTER")
        int chapter;

        @Parameters(index = "1", paramLabel = "SEQUENCE")
        int sequence;

        @Option(names = "--on", required = true, description = "the day it came off")
        String onDay;

        @Override
        public Integer call() {
            RepairId held = new RepairId(chapter, sequence);
            inWork(unit -> Repairs.remove(unit, held, Output.asDate(onDay)));
            say("removed " + held);
            return Main.OK;
        }
    }
}
