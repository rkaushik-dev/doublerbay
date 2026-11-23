package io.github.rkaushikdev.doublerbay.cli;

import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.report.Counted;
import io.github.rkaushikdev.doublerbay.report.DamageCount;
import io.github.rkaushikdev.doublerbay.report.DeskReports;
import io.github.rkaushikdev.doublerbay.report.DueItem;
import io.github.rkaushikdev.doublerbay.report.DueReport;
import io.github.rkaushikdev.doublerbay.report.Export;
import io.github.rkaushikdev.doublerbay.report.FleetDamage;
import io.github.rkaushikdev.doublerbay.report.Moved;
import io.github.rkaushikdev.doublerbay.report.Revisions;
import io.github.rkaushikdev.doublerbay.report.Sweep;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The commands that decide things, and the commands that print lists.
 *
 * <p>Deferring is two writes and they belong together. The deferral itself is what the aeroplane
 * flies against, and the disposition move is what makes the card show up as deferred on every
 * report. One without the other is a card that is either flying against a limit nobody reports on,
 * or reported as deferred with no limit behind it. Both have happened on real fleets.
 *
 * <p>A card has to be assessed before it can be deferred. That is the domain's rule, not this
 * class's, and the refusal comes back with the states it would not move between. It reads like an
 * obstruction the first time somebody hits it at six in the morning, and it is the reason nobody
 * defers a crack they have not looked up.
 */
public final class Desk {

    /** The short names the shop uses for the methods, in the order the methods are listed. */
    public static final List<String> METHODS = Arrays.stream(InspectionMethod.values())
            .map(one -> Text.lower(one.name()))
            .toList();

    private Desk() {}

    /**
     * Read a method off the short name the shop uses rather than the long one.
     *
     * @throws Misused for a name that is not one of {@link #METHODS}
     */
    public static InspectionMethod methodNamed(String text) {
        String name = Text.upper(Text.strip(text));
        for (InspectionMethod one : InspectionMethod.values()) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        throw Misused.badValue(Literal.of(text) + " is not one of " + String.join(", ", METHODS));
    }

    /** A day off the command line, or the real one when it is left off. */
    public static LocalDate todayOr(String day) {
        return Step.given(day) ? Output.asDate(day) : LocalDate.now();
    }

    /** The due list laid out: tail, card, when it falls due, how much margin is left, and what it is. */
    static List<List<String>> dueRows(List<DueItem> items) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("tail", "card", "due", "margin", "what"));
        for (DueItem one : items) {
            rows.add(List.of(
                    one.serial().number(),
                    one.against().toString(),
                    one.due().toString(),
                    one.margin().toString(),
                    one.what()));
        }
        return rows;
    }

    /** One tail's open count laid out as a short block. */
    static List<List<String>> openRows(DamageCount counted) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("tail", counted.serial().number()));
        rows.add(List.of("as of", counted.on().toString()));
        rows.add(List.of("open", String.valueOf(counted.total())));
        rows.add(List.of("raised recently", String.valueOf(counted.raisedRecently())));
        for (Counted<DamageKind> kind : counted.byKind()) {
            rows.add(List.of("  " + kind.what(), String.valueOf(kind.count())));
        }
        for (Counted<Integer> chapter : counted.byChapter()) {
            rows.add(List.of("  chapter " + chapter.what(), String.valueOf(chapter.count())));
        }
        return rows;
    }

    private static OptionalInt within(Integer days) {
        return days == null ? OptionalInt.empty() : OptionalInt.of(days);
    }

    /** Deferrals: grant them, tighten them, lift them. */
    @Command(
            name = "defer",
            customSynopsis = "doublerbay defer [OPTIONS] COMMAND [ARGS]...",
            description = "Deferrals: grant them, tighten them, lift them.",
            subcommands = {DeferGrant.class, DeferLift.class, DeferShow.class, DeferTighten.class})
    static final class Defer {}

    /** Defer a card, writing the limit and the disposition move together. */
    @Command(
            name = "grant",
            customSynopsis = "doublerbay defer grant [OPTIONS] REFERENCE",
            description = "Defer a card, writing the limit and the disposition move together.")
    static final class DeferGrant extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Option(names = "--by", required = true, description = "who is granting it")
        String by;

        @Option(names = "--on", required = true, description = "yyyy-mm-dd")
        String onDay;

        @Option(
                names = "--method",
                defaultValue = "dvi",
                showDefaultValue = Visibility.ALWAYS,
                description = "one of gvi, dvi, xray, ut, lfec, hfec")
        String method;

        @Option(names = "--limit", description = "for example \"300 FC / 90 DY\"")
        String limit;

        @Option(names = "--until-check", description = "tighten to a check on this day")
        String untilCheck;

        @Option(names = "--reason", defaultValue = "")
        String reason;

        @Override
        public Integer call() {
            FindingId held = FindingId.parse(reference);
            LocalDate day = Output.asDate(onDay);
            Deferral deferral = work(unit -> {
                Finding finding = unit.findings().latest(held);
                Usage flown = unit.aircraft().logFor(finding.serial()).at(day);
                Deferral granted = Deferral.grant(
                        finding,
                        day,
                        flown,
                        by,
                        methodNamed(method),
                        given(limit) ? Interval.parse(limit) : null,
                        given(untilCheck) ? Output.asDate(untilCheck) : null,
                        reason);
                unit.deferrals().grant(granted);
                DispositionRecord record = unit.dispositions().get(held);
                unit.dispositions().save(record.movedTo(DispositionState.DEFERRED, day, by, reason));
                return granted;
            });
            say("deferred " + held + " until " + deferral.due());
            return Main.OK;
        }
    }

    /** Cut a live deferral back to a shorter limit. */
    @Command(
            name = "tighten",
            customSynopsis = "doublerbay defer tighten [OPTIONS] REFERENCE",
            description = "Cut a live deferral back to a shorter limit.")
    static final class DeferTighten extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Option(names = "--limit", required = true, description = "the shorter limit, for example \"50 FC\"")
        String limit;

        @Option(names = "--by", required = true)
        String by;

        @Option(names = "--reason", defaultValue = "")
        String reason;

        @Override
        public Integer call() {
            FindingId held = FindingId.parse(reference);
            Deferral after = work(unit -> {
                Deferral current = unit.deferrals().get(held);
                unit.deferrals().tighten(current.tightenedTo(Interval.parse(limit), by, reason));
                return unit.deferrals().get(held);
            });
            say("tightened " + held + " to " + after.due());
            return Main.OK;
        }
    }

    /** Close a deferral because the damage under it has been dealt with. */
    @Command(
            name = "lift",
            customSynopsis = "doublerbay defer lift [OPTIONS] REFERENCE",
            description = "Close a deferral because the damage under it has been dealt with.")
    static final class DeferLift extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Option(names = "--on", required = true, description = "yyyy-mm-dd")
        String onDay;

        @Override
        public Integer call() {
            FindingId held = FindingId.parse(reference);
            inWork(unit -> unit.deferrals().lift(held, Output.asDate(onDay)));
            say("lifted " + held);
            return Main.OK;
        }
    }

    /** One live deferral, with what it is due against. */
    @Command(
            name = "show",
            customSynopsis = "doublerbay defer show [OPTIONS] REFERENCE",
            description = "One live deferral, with what it is due against.")
    static final class DeferShow extends Step {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        String reference;

        @Override
        public Integer call() {
            Deferral held = work(unit -> unit.deferrals().get(FindingId.parse(reference)));
            say(Output.columns(List.of(
                    List.of("against", held.against().toString()),
                    List.of("granted", held.granted().toString()),
                    List.of("granted by", held.by()),
                    List.of("at", held.atUsage().toString()),
                    List.of("limit", held.limit().toString()),
                    List.of("due", held.due().toString()),
                    List.of("re inspect", held.reInspectWith().toString()),
                    List.of("reason", held.reason().isEmpty() ? "not given" : held.reason()))));
            return Main.OK;
        }
    }

    /** The lists the desk prints: what is due, what is open, what is grounded. */
    @Command(
            name = "report",
            customSynopsis = "doublerbay report [OPTIONS] COMMAND [ARGS]...",
            description = "The lists the desk prints: what is due, what is open, what is grounded.",
            subcommands = {ReportDue.class, ReportGrounded.class, ReportOpen.class, ReportRevision.class})
    static final class Report {}

    /** What falls due, worst first. */
    @Command(
            name = "due",
            customSynopsis = "doublerbay report due [OPTIONS]",
            description = "What falls due, worst first.")
    static final class ReportDue extends Step {

        @Option(names = "--tail", description = "one tail, or leave it off for the fleet")
        String serial;

        @Option(names = "--on", required = true, description = "yyyy-mm-dd")
        String onDay;

        @Option(names = "--within", description = "only what falls due inside this many days")
        Integer within;

        @Option(names = "--csv", description = "write a file")
        Path asFile;

        @Override
        public Integer call() {
            LocalDate day = Output.asDate(onDay);
            List<DueItem> items = work(unit -> {
                if (given(serial)) {
                    return DeskReports.dueOnTail(unit, new Serial(serial), day, within(within), new TypeCatalogue())
                            .items();
                }
                return DeskReports.dueAcrossFleet(unit, day, within(within)).stream()
                        .flatMap(report -> report.items().stream())
                        .toList();
            });
            if (asFile != null) {
                Texts.write(asFile, Export.dueAsCsv(items));
                say("wrote " + items.size() + " rows to " + asFile);
                return Main.OK;
            }
            if (items.isEmpty()) {
                say(Output.sayNothingFound("due"));
                return Main.OK;
            }
            say(Output.columns(dueRows(items)));
            return Main.OK;
        }
    }

    /** The tails with something already past its limit. */
    @Command(
            name = "grounded",
            customSynopsis = "doublerbay report grounded [OPTIONS]",
            description = "The tails with something already past its limit.")
    static final class ReportGrounded extends Step {

        @Option(names = "--on", required = true, description = "yyyy-mm-dd")
        String onDay;

        @Override
        public Integer call() {
            LocalDate day = Output.asDate(onDay);
            List<DueReport> reports = work(unit -> DeskReports.grounded(unit, day));
            if (reports.isEmpty()) {
                say(Output.sayNothingFound("grounded"));
                return Main.OK;
            }
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("tail", "over", "due", "rate"));
            for (DueReport one : reports) {
                rows.add(List.of(
                        one.serial().number(),
                        String.valueOf(one.overrun().size()),
                        String.valueOf(one.items().size()),
                        one.measuredRate() ? "measured" : "planning"));
            }
            say(Output.columns(rows));
            return Main.OK;
        }
    }

    /** How much damage is open, by kind and by chapter. */
    @Command(
            name = "open",
            customSynopsis = "doublerbay report open [OPTIONS]",
            description = "How much damage is open, by kind and by chapter.")
    static final class ReportOpen extends Step {

        @Option(names = "--tail")
        String serial;

        @Option(names = "--on", required = true, description = "yyyy-mm-dd")
        String onDay;

        @Option(
                names = "--recent",
                defaultValue = "30",
                showDefaultValue = Visibility.ALWAYS,
                description = "the recent window in days")
        int recent;

        @Override
        public Integer call() {
            LocalDate day = Output.asDate(onDay);
            if (given(serial)) {
                DamageCount counted = work(unit -> DeskReports.openOnTail(unit, new Serial(serial), day, recent));
                say(Output.columns(openRows(counted)));
                return Main.OK;
            }
            FleetDamage fleet = work(unit -> DeskReports.openAcrossFleet(unit, day, recent));
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("tail", "open", "recent", "worst kind"));
            for (DamageCount one : fleet.tails()) {
                rows.add(List.of(
                        one.serial().number(),
                        String.valueOf(one.total()),
                        String.valueOf(one.raisedRecently()),
                        one.worstKind().map(DamageKind::toString).orElse("none")));
            }
            rows.add(List.of("", "", "", ""));
            rows.add(List.of("fleet", String.valueOf(fleet.total()), "", ""));
            say(Output.columns(rows));
            return Main.OK;
        }
    }

    /** Which open cards a revision of the manual would answer differently. */
    @Command(
            name = "revision",
            customSynopsis = "doublerbay report revision [OPTIONS]",
            description = "Which open cards a revision of the manual would answer differently.")
    static final class ReportRevision extends Step {

        @Option(names = "--before", required = true, description = "a day the old book was in force")
        String before;

        @Option(names = "--after", required = true, description = "a day the new one is")
        String after;

        @Override
        public Integer call() {
            Sweep swept = work(unit -> Revisions.sweep(
                    unit, Book.v320(), new TypeCatalogue(), Output.asDate(before), Output.asDate(after)));
            say(swept.toString());
            if (swept.isQuiet()) {
                return Main.OK;
            }
            say("");
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("card", "tail", "was", "now", "way"));
            for (Moved one : swept.moved()) {
                rows.add(List.of(
                        one.reference().toString(),
                        one.serial().number(),
                        one.was().value(),
                        one.now().value(),
                        one.gotWorse() ? "worse" : "better"));
            }
            say(Output.columns(rows));
            for (FindingId reference : swept.unreadable()) {
                say("could not assess " + reference);
            }
            return Main.OK;
        }
    }
}
