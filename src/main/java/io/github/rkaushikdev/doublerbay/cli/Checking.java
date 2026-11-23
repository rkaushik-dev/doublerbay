package io.github.rkaushikdev.doublerbay.cli;

import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.report.Export;
import io.github.rkaushikdev.doublerbay.report.PackLine;
import io.github.rkaushikdev.doublerbay.report.WorkPack;
import io.github.rkaushikdev.doublerbay.report.WorkPacks;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionTask;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The check commands: what is on a tail, and what a check has to clear.
 *
 * <p>These two live together because they are the same conversation. The programme list is the
 * standing picture and the pack is that picture cut to a window, and somebody planning a check reads
 * one then the other.
 *
 * <p>The pack prints in blocks with headings rather than one sorted table. A planner is going
 * to tear the page in half and give the inspections to the shop and the deferrals to the engineer,
 * and a single sorted list makes that a copying exercise.
 */
public final class Checking {

    /** The row between two blocks of a pack. */
    public static final List<String> BLANK = List.of("", "", "");

    /** The heading each block of a pack prints under, keyed on the word its lines carry. */
    public static final Map<String, String> HEADINGS = Map.of(
            "deferral", "deferrals",
            "inspection", "inspections",
            "waiting", "waiting on engineering",
            "strip", "paint off first");

    private Checking() {}

    /** The blocks, with a heading on each and a blank line between, the empty ones left out. */
    public static List<List<String>> packRows(WorkPack pack) {
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<String, List<PackLine>> block : pack.blocks()) {
            if (block.getValue().isEmpty()) {
                continue;
            }
            if (!rows.isEmpty()) {
                rows.add(BLANK);
            }
            rows.add(List.of(Text.upper(HEADINGS.get(block.getKey())), "", ""));
            for (PackLine one : block.getValue()) {
                rows.add(List.of(one.overrun() ? "OVER" : "", one.reference(), one.detail()));
            }
        }
        return rows;
    }

    /** What a check has to clear, and what the tail is carrying. */
    @Command(
            name = "check",
            customSynopsis = "doublerbay check [OPTIONS] COMMAND [ARGS]...",
            description = "What a check has to clear, and what the tail is carrying.",
            subcommands = {CheckDone.class, CheckDue.class, CheckPack.class, CheckProgramme.class})
    static final class Group {}

    /** Everything one tail owes the desk at one check. */
    @Command(
            name = "pack",
            customSynopsis = "doublerbay check pack [OPTIONS]",
            description = "Everything one tail owes the desk at one check.")
    static final class CheckPack extends Step {

        @Option(names = "--tail", required = true)
        String serial;

        @Option(names = "--opens", required = true, description = "the day the aeroplane comes in")
        String opens;

        @Option(names = "--closes", required = true, description = "the day it goes back out")
        String closes;

        @Option(names = "--within", description = "pull work in from this many days ahead")
        Integer within;

        @Option(names = "--csv", description = "write a file")
        Path asFile;

        @Override
        public Integer call() {
            Serial held = new Serial(serial);
            WorkPack pack = work(unit -> WorkPacks.build(
                    unit,
                    held,
                    Output.asDate(opens),
                    Output.asDate(closes),
                    within == null ? OptionalInt.empty() : OptionalInt.of(within)));
            if (asFile != null) {
                Texts.write(asFile, Export.packAsCsv(pack));
                say("wrote " + Export.packRows(pack).size() + " rows to " + asFile);
                return Main.OK;
            }
            say(pack.toString());
            if (pack.isEmpty()) {
                return Main.OK;
            }
            say("");
            say(Output.columns(packRows(pack)));
            return Main.OK;
        }
    }

    /** Every repeat inspection one tail is carrying. */
    @Command(
            name = "programme",
            customSynopsis = "doublerbay check programme [OPTIONS]",
            description = "Every repeat inspection one tail is carrying.")
    static final class CheckProgramme extends Step {

        @Option(names = "--tail", required = true)
        String serial;

        @Option(names = "--never-done", description = "only the ones never signed off")
        boolean unseen;

        @Override
        public Integer call() {
            Serial held = new Serial(serial);
            List<InspectionTask> tasks = work(unit -> {
                unit.aircraft().get(held);
                return unseen
                        ? unit.inspections().neverDone(held)
                        : unit.inspections().onTail(held);
            });
            if (tasks.isEmpty()) {
                say(Output.sayNothingFound("on the programme for " + serial));
                return Main.OK;
            }
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("repair", "part", "method", "threshold", "repeat", "last done"));
            for (InspectionTask one : tasks) {
                rows.add(List.of(
                        one.against().toString(),
                        one.part().toString(),
                        one.schedule().method().toString(),
                        one.schedule().threshold().toString(),
                        one.schedule().repeat().map(Object::toString).orElse("once only"),
                        one.lastDone().map(LocalDate::toString).orElse("never")));
            }
            say(Output.columns(rows));
            return Main.OK;
        }
    }

    /** The repeat inspections that have come due on one tail. */
    @Command(
            name = "due",
            customSynopsis = "doublerbay check due [OPTIONS]",
            description = "The repeat inspections that have come due on one tail.")
    static final class CheckDue extends Step {

        @Option(names = "--tail", required = true)
        String serial;

        @Option(names = "--on", required = true, description = "the day to measure against")
        String onDay;

        @Override
        public Integer call() {
            Serial held = new Serial(serial);
            LocalDate day = Output.asDate(onDay);
            List<InspectionTask> tasks = work(unit -> {
                unit.aircraft().get(held);
                Usage flown = unit.aircraft().logFor(held).at(day);
                return unit.inspections().dueOn(held, flown, day);
            });
            if (tasks.isEmpty()) {
                say(Output.sayNothingFound("due on " + serial));
                return Main.OK;
            }
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("repair", "part", "method", "due"));
            for (InspectionTask one : tasks) {
                rows.add(List.of(
                        one.against().toString(),
                        one.part().toString(),
                        one.schedule().method().toString(),
                        one.due().toString()));
            }
            say(Output.columns(rows));
            return Main.OK;
        }
    }

    /** Sign off a repeat inspection, at the counters it was done at. */
    @Command(
            name = "done",
            customSynopsis = "doublerbay check done [OPTIONS] CHAPTER SEQUENCE",
            description = "Sign off a repeat inspection, at the counters it was done at.")
    static final class CheckDone extends Step {

        @Parameters(index = "0", paramLabel = "CHAPTER")
        int chapter;

        @Parameters(index = "1", paramLabel = "SEQUENCE")
        int sequence;

        @Option(names = "--on", required = true, description = "the day it was done")
        String onDay;

        @Option(names = "--cycles", required = true)
        int cycles;

        @Option(names = "--hours", required = true)
        String hours;

        @Override
        public Integer call() {
            RepairId held = new RepairId(chapter, sequence);
            Usage flown = new Usage(new Cycles(cycles), Hours.ofHours(hours));
            Optional<String> following = work(unit -> {
                InspectionTask signed = unit.inspections().signOff(held, Output.asDate(onDay), flown);
                return signed.isAOneOff()
                        ? Optional.<String>empty()
                        : Optional.of(signed.due().toString());
            });
            say("signed off " + held + " at " + flown);
            following.ifPresent(next -> say("next due " + next));
            return Main.OK;
        }
    }
}
