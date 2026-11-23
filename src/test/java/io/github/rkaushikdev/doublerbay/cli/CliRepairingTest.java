package io.github.rkaushikdev.doublerbay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Opening;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.report.Export;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliRepairingTest {

    private static final String DAY = "2025-03-14";
    private static final String FITTED = "2025-04-02";

    private static Ran invoke(Database database, String... args) {
        return Cli.invoke(database, args);
    }

    private static Database stocked() {
        return Desk.stocked();
    }

    private static String assessed(Database database) {
        return assessed(database, 1);
    }

    private static String assessed(Database database, int sequence) {
        FindingId reference = new FindingId(2025, sequence);
        UnitOfWork.run(database, unit -> {
            unit.findings().raiseFinding(Sample.card(sequence, Sample.cabinPointOn(10)));
            unit.dispositions()
                    .save(DispositionRecord.raised(reference)
                            .movedTo(DispositionState.ASSESSED, LocalDate.of(2025, 3, 14), "a.okonkwo"));
        });
        return reference.toString();
    }

    private static Ran draw(Database database, String reference, String... extra) {
        List<String> args = new ArrayList<>(
                List.of("repair", "draw", reference, "--along", "2.000", "--around", "2.000", "--on", DAY));
        args.addAll(List.of(extra));
        return invoke(database, args.toArray(String[]::new));
    }

    private static Ran fit(Database database, String reference) {
        return fit(database, reference, 1, "468.000", "91.000");
    }

    private static Ran fit(Database database, String reference, int sequence) {
        return fit(database, reference, sequence, "468.000", "91.000");
    }

    private static Ran fit(Database database, String reference, int sequence, String along, String around) {
        return invoke(
                database,
                "repair",
                "fit",
                reference,
                "--sequence",
                String.valueOf(sequence),
                "--along",
                "2.000",
                "--around",
                "2.000",
                "--centre-along",
                along,
                "--centre-around",
                around,
                "--on",
                FITTED,
                "--by",
                "s.brandt");
    }

    @Test
    void aDrawingPrintsTheDoubler() {
        Database database = stocked();
        String printed = draw(database, assessed(database)).output();
        assertThat(printed).contains("material");
        assertThat(printed).contains("fasteners");
        assertThat(printed).contains("edge distance");
    }

    @Test
    void aDrawingOverAStringerSaysHowTheDoublerGetsPastIt() {
        Database database = stocked();
        String printed = draw(database, assessed(database)).output();
        assertThat(printed).contains("weight");
        assertThat(printed).contains("S-10L, joggle over it or take it into the pattern");
    }

    @Test
    void aDrawingClearOfEveryMemberHasNoOverLine() {
        Database database = stocked();
        UnitOfWork.run(database, unit -> {
            unit.findings().raiseFinding(Sample.card(7, Sample.cabinPointOn(10, "4.000")));
            unit.dispositions()
                    .save(DispositionRecord.raised(new FindingId(2025, 7))
                            .movedTo(DispositionState.ASSESSED, LocalDate.of(2025, 3, 14), "a.okonkwo"));
        });
        String printed = draw(database, "DR-2025-000007").output();
        assertThat(printed).contains("edge distance").doesNotContain("joggle");
    }

    @Test
    void aDrawingWritesNothing() {
        Database database = stocked();
        draw(database, assessed(database));
        List<RepairRecord> fitted =
                UnitOfWork.apply(database, unit -> unit.repairs().onTail(Sample.MSN));
        assertThat(fitted).isEmpty();
    }

    @Test
    void aDrawingNamesThePanelItLandedOn() {
        Database database = stocked();
        assertThat(draw(database, assessed(database)).output()).contains("5310");
    }

    @Test
    void aTemporaryDrawingSaysItIsTemporary() {
        Database database = stocked();
        assertThat(draw(database, assessed(database), "--temporary").output()).contains("temporary");
    }

    @Test
    void aTemporaryDrawingSaysWhenItComesOff() {
        Database database = stocked();
        String printed = draw(database, assessed(database), "--temporary").output();
        assertThat(printed).contains("replace by").contains("3000 FC / 12 MO, whichever comes first");
    }

    @Test
    void aPermanentDrawingHasNoReplacementLine() {
        Database database = stocked();
        assertThat(draw(database, assessed(database)).output()).doesNotContain("replace by");
    }

    @Test
    void aFastenerSizeCanBeAskedFor() {
        Database database = stocked();
        assertThat(draw(database, assessed(database), "--fastener", "6").output())
                .contains("dash 6");
    }

    @Test
    void aCardThatIsNotOnFileCannotBeDrawnFor() {
        Database database = stocked();
        assertThat(draw(database, "DR-2025-000099").exitCode()).isNotZero();
    }

    @Test
    void aRepairCanBeFitted() {
        Database database = stocked();
        Ran answer = fit(database, assessed(database));
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("RP-53-0001 fitted");
    }

    @Test
    void fittingClosesTheCard() {
        Database database = stocked();
        fit(database, assessed(database));
        DispositionState state = UnitOfWork.apply(
                database,
                unit -> unit.dispositions().get(new FindingId(2025, 1)).state());
        assertThat(state).isSameAs(DispositionState.CLOSED);
    }

    @Test
    void aClashIsPrintedUnderTheFit() {
        Database database = stocked();
        fit(database, assessed(database, 1), 1, "468.000", "91.000");
        String printed =
                fit(database, assessed(database, 2), 2, "469.000", "91.000").output();
        assertThat(printed).contains("too close: RP-53-0001 and RP-53-0002");
    }

    @Test
    void aCleanFitPrintsNoClashLine() {
        Database database = stocked();
        assertThat(fit(database, assessed(database)).output()).doesNotContain("too close");
    }

    @Test
    void aRepairNumberUsedTwiceIsRefused() {
        Database database = stocked();
        fit(database, assessed(database, 1), 1);
        assertThat(fit(database, assessed(database, 2), 1).exitCode()).isNotZero();
    }

    @Test
    void aMeasurementThatIsNotOneIsRefused() {
        Database database = stocked();
        Ran answer = invoke(
                database, "repair", "draw", assessed(database), "--along", "big", "--around", "2.000", "--on", DAY);
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void theRepairsOnATailAreListed() {
        Database database = stocked();
        fit(database, assessed(database));
        String listed = invoke(database, "repair", "list", "--tail", "5127").output();
        assertThat(listed).contains("RP-53-0001");
        assertThat(listed).contains("DR-2025-000001");
        assertThat(listed).contains(" lb ");
        assertThat(listed).containsPattern("1 fitted repairs weigh 0\\.\\d{3} lb");
    }

    @Test
    void aTailWithNoRepairsSaysSo() {
        Database database = stocked();
        String listed = invoke(database, "repair", "list", "--tail", "5127").output();
        assertThat(listed.strip()).isEqualTo("nothing fitted to 5127");
    }

    @Test
    void aTailThatIsNotOnTheRegisterCannotBeListed() {
        Database database = stocked();
        assertThat(invoke(database, "repair", "list", "--tail", "9999").exitCode())
                .isNotZero();
    }

    @Test
    void aRepairCanBeTakenOff() {
        Database database = stocked();
        fit(database, assessed(database));
        Ran answer = invoke(database, "repair", "remove", "53", "1", "--on", "2026-01-05");
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output()).contains("removed RP-53-0001");
    }

    @Test
    void aRemovedRepairDropsOffTheFittedList() {
        Database database = stocked();
        fit(database, assessed(database));
        invoke(database, "repair", "remove", "53", "1", "--on", "2026-01-05");
        assertThat(invoke(database, "repair", "list", "--tail", "5127").output())
                .contains("nothing fitted");
    }

    @Test
    void aRemovedRepairIsStillOnTheFullList() {
        Database database = stocked();
        fit(database, assessed(database));
        invoke(database, "repair", "remove", "53", "1", "--on", "2026-01-05");
        String listed =
                invoke(database, "repair", "list", "--tail", "5127", "--all").output();
        assertThat(listed).contains("2026-01-05");
    }

    @Test
    void removingOneThatIsNotOnFileIsRefused() {
        Database database = stocked();
        assertThat(invoke(database, "repair", "remove", "53", "9", "--on", "2026-01-05")
                        .exitCode())
                .isNotZero();
    }

    @Test
    void aRepairCannotComeOffBeforeItWentOn() {
        Database database = stocked();
        fit(database, assessed(database));
        assertThat(invoke(database, "repair", "remove", "53", "1", "--on", "2024-01-01")
                        .exitCode())
                .isNotZero();
    }

    @Test
    void theRepairOnFileCarriesTheCentreItWasFittedAt() {
        Database database = stocked();
        fit(database, assessed(database));
        Length along = UnitOfWork.apply(
                database,
                unit -> unit.repairs().get(new RepairId(53, 1)).centre().along());
        assertThat(along).isEqualTo(Sample.inch("468.000"));
    }

    @TempDir
    Path registerDir;

    @Test
    void theRepairRegisterIsWrittenToAFileWithTheListsOwnColumns() throws Exception {
        Database database = stocked();
        fit(database, assessed(database));
        Path written = registerDir.resolve("register.csv");
        Ran answer = invoke(database, "repair", "list", "--tail", "5127", "--csv", written.toString());
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output()).contains("wrote 1 rows to");
        List<String> lines = Files.readAllLines(written);
        assertThat(lines.get(0)).isEqualTo(String.join(",", Export.REPAIR_HEADER));
        assertThat(lines.get(1)).startsWith("RP-53-0001,").contains(",DR-2025-000001,53-40-01,");
        String listed = invoke(database, "repair", "list", "--tail", "5127").output();
        assertThat(listed).contains("inspected").contains("at FC");
    }

    @Test
    void aTailWithNoRepairsStillWritesTheHeader() throws Exception {
        Database database = stocked();
        Path written = registerDir.resolve("empty.csv");
        assertThat(invoke(database, "repair", "list", "--tail", "5127", "--csv", written.toString())
                        .output())
                .contains("wrote 0 rows");
        assertThat(Files.readAllLines(written)).containsExactly(String.join(",", Export.REPAIR_HEADER));
    }

    private static String raisedHole(Database database, int sequence, Opening opening, String below, int stringer) {
        FindingId reference = new FindingId(2025, sequence);
        UnitOfWork.run(database, unit -> {
            unit.findings()
                    .raiseFinding(Sample.cardWith()
                            .sequence(sequence)
                            .kind(DamageKind.HOLE)
                            .geometry(opening)
                            .at(Sample.cabinPointOn(stringer, below))
                            .build());
            unit.dispositions()
                    .save(DispositionRecord.raised(reference)
                            .movedTo(DispositionState.ASSESSED, LocalDate.of(2025, 3, 14), "a.okonkwo"));
        });
        return reference.toString();
    }

    @Test
    void aWorkedFastenerHoleIsOfferedAnOversizeBesideTheDoubler() {
        Database database = stocked();
        Opening worked = new Opening(Sample.inch("0.170"), Sample.inch("0.156"), false, true);
        String printed =
                draw(database, raisedHole(database, 5, worked, "0.500", 8)).output();
        assertThat(printed).contains("or instead of a doubler: ream -5 to -6");
    }

    @Test
    void aCutOutThatLeavesTheTornEdgesInIsRefused() {
        Database database = stocked();
        Opening torn = new Opening(Sample.inch("1.950"), Sample.inch("1.000"), true, false);
        Ran answer = draw(database, raisedHole(database, 6, torn, "4.000", 10));
        assertThat(answer.exitCode()).isNotZero();
        assertThat(answer.failure()).contains("at least 2.200 by 1.250");
    }
}
