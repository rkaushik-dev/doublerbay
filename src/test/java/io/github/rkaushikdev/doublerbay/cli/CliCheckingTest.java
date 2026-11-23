package io.github.rkaushikdev.doublerbay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.report.PackLine;
import io.github.rkaushikdev.doublerbay.report.WorkPack;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CliCheckingTest {

    private static final String FITTED = "2025-04-02";
    private static final String OPENS = "2025-04-01";
    private static final String CLOSES = "2025-04-08";
    private static final Serial MSN = new Serial("5127");

    private static Ran invoke(Database database, String... args) {
        return Cli.invoke(database, args);
    }

    private static UtilisationLog logWithTwoReadings() {
        UtilisationLog opened = UtilisationLog.opened(MSN, LocalDate.of(2016, 4, 12));
        opened = opened.posted(new Snapshot(LocalDate.of(2024, 3, 14), Sample.flown(12000, 21000)));
        return opened.posted(new Snapshot(LocalDate.of(2025, 3, 1), Sample.flown(13800, 24200)));
    }

    private static Database stocked() {
        Database database = Desk.newDatabase();
        UnitOfWork.run(database, unit -> {
            unit.aircraft().add(Sample.aircraft());
            unit.aircraft().openLog(logWithTwoReadings());
        });
        return database;
    }

    /** One card on a fastener row, raised and looked up. */
    private static String assessed(Database database) {
        return Desk.assessedCard(database, Sample.card(1, Sample.cabinPointOn(10)))
                .toString();
    }

    private static Ran fit(Database database, String reference) {
        return invoke(
                database,
                "repair",
                "fit",
                reference,
                "--sequence",
                "1",
                "--along",
                "2.000",
                "--around",
                "2.000",
                "--centre-along",
                "468.000",
                "--centre-around",
                "91.000",
                "--on",
                FITTED,
                "--by",
                "s.brandt",
                "--temporary");
    }

    private static Ran defer(Database database, String reference) {
        return defer(database, reference, "1 FC");
    }

    private static Ran defer(Database database, String reference, String limit) {
        return invoke(
                database, "defer", "grant", reference, "--by", "j.mercer", "--on", "2025-03-14", "--limit", limit);
    }

    private static Ran packOf(Database database, String... extra) {
        List<String> args =
                new ArrayList<>(List.of("check", "pack", "--tail", "5127", "--opens", OPENS, "--closes", CLOSES));
        args.addAll(List.of(extra));
        return invoke(database, args.toArray(String[]::new));
    }

    private static Ran signedOff(Database database, String sequence) {
        return invoke(
                database,
                "check",
                "done",
                "53",
                sequence,
                "--on",
                "2026-01-05",
                "--cycles",
                "15000",
                "--hours",
                "26000");
    }

    @Test
    void anEmptyPackSaysSo() {
        assertThat(packOf(stocked()).output()).contains("nothing for this check");
    }

    @Test
    void aPackPrintsItsBlocks() {
        Database database = stocked();
        defer(database, assessed(database));
        String printed = packOf(database).output();
        assertThat(printed).contains("DEFERRALS");
        assertThat(printed).contains("DR-2025-000001");
    }

    @Test
    void aPackMarksWhatIsAlreadyOver() {
        Database database = stocked();
        String reference = assessed(database);
        invoke(database, "defer", "grant", reference, "--by", "j.mercer", "--on", "2025-03-15", "--limit", "1 DY");
        assertThat(packOf(database).output()).contains("OVER");
    }

    @Test
    void aPackCanPullWorkInFromAhead() {
        Database database = stocked();
        defer(database, assessed(database), "500 FC");
        assertThat(packOf(database).output()).contains("nothing for this check");
        assertThat(packOf(database, "--within", "400").output()).contains("DEFERRALS");
    }

    @Test
    void aPackOnATailThatIsNotThereIsRefused() {
        Database database = stocked();
        Ran answer = invoke(database, "check", "pack", "--tail", "9999", "--opens", OPENS, "--closes", CLOSES);
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void aCheckThatClosesBeforeItOpensIsRefused() {
        Database database = stocked();
        Ran answer = invoke(database, "check", "pack", "--tail", "5127", "--opens", CLOSES, "--closes", OPENS);
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void anEmptyProgrammeSaysSo() {
        String printed =
                invoke(stocked(), "check", "programme", "--tail", "5127").output();
        assertThat(printed.strip()).isEqualTo("nothing on the programme for 5127");
    }

    @Test
    void aFittedRepairShowsOnTheProgramme() {
        Database database = stocked();
        fit(database, assessed(database));
        String printed =
                invoke(database, "check", "programme", "--tail", "5127").output();
        assertThat(printed).contains("RP-53-0001");
        assertThat(printed).contains("once only");
        assertThat(printed).contains("never");
    }

    @Test
    void theProgrammeCanBeCutToWhatIsNeverDone() {
        Database database = stocked();
        fit(database, assessed(database));
        String printed = invoke(database, "check", "programme", "--tail", "5127", "--never-done")
                .output();
        assertThat(printed).contains("RP-53-0001");
    }

    @Test
    void aProgrammeOnATailThatIsNotThereIsRefused() {
        assertThat(invoke(stocked(), "check", "programme", "--tail", "9999").exitCode())
                .isNotZero();
    }

    @Test
    void nothingIsDueTheDayTheRepairGoesOn() {
        Database database = stocked();
        fit(database, assessed(database));
        String printed = invoke(database, "check", "due", "--tail", "5127", "--on", FITTED)
                .output();
        assertThat(printed.strip()).isEqualTo("nothing due on 5127");
    }

    @Test
    void aTaskPastItsThresholdIsDue() {
        Database database = stocked();
        fit(database, assessed(database));
        String printed = invoke(database, "check", "due", "--tail", "5127", "--on", "2030-01-01")
                .output();
        assertThat(printed).contains("RP-53-0001");
    }

    @Test
    void aTaskCanBeSignedOff() {
        Database database = stocked();
        fit(database, assessed(database));
        Ran answer = signedOff(database, "1");
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("signed off RP-53-0001");
    }

    @Test
    void aOneOffPrintsNoNextDue() {
        Database database = stocked();
        fit(database, assessed(database));
        assertThat(signedOff(database, "1").output()).doesNotContain("next due");
    }

    @Test
    void aSignedOffTaskCarriesItsReading() {
        Database database = stocked();
        fit(database, assessed(database));
        signedOff(database, "1");
        int cycles = UnitOfWork.apply(database, unit -> unit.inspections()
                .get(new RepairId(53, 1))
                .atLastDone()
                .orElseThrow()
                .cycles()
                .count());
        assertThat(cycles).isEqualTo(15000);
    }

    @Test
    void signingOffOneThatIsNotThereIsRefused() {
        Database database = stocked();
        fit(database, assessed(database));
        assertThat(signedOff(database, "9").exitCode()).isNotZero();
    }

    @Test
    void thePackRowsCarryAHeadingPerBlock() {
        WorkPack pack = new WorkPack(
                MSN,
                LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 8),
                List.of(new PackLine("deferral", "DR-2025-000001", "detailed visual")),
                List.of(new PackLine("inspection", "RP-53-0001", "eddy current")),
                List.of());
        List<List<String>> rows = Checking.packRows(pack);
        assertThat(rows.get(0).get(0)).isEqualTo("DEFERRALS");
        assertThat(rows).contains(List.of("", "", ""));
        assertThat(rows).anySatisfy(row -> assertThat(row.get(0)).isEqualTo("INSPECTIONS"));
    }

    @Test
    void anEmptyPackHasNoRows() {
        WorkPack pack =
                new WorkPack(MSN, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 8), List.of(), List.of(), List.of());
        assertThat(Checking.packRows(pack)).isEmpty();
    }

    @Test
    void thePaintOffComesAfterTheInspectionsItIsFor() {
        WorkPack pack = new WorkPack(
                MSN,
                LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 8),
                List.of(),
                List.of(new PackLine("inspection", "RP-53-0001", "high frequency eddy current")),
                List.of(),
                List.of(new PackLine("strip", "5310-143L", "paint off for high frequency eddy current on RP-53-0001")));
        List<List<String>> rows = Checking.packRows(pack);
        assertThat(rows.get(0).get(0)).isEqualTo("INSPECTIONS");
        assertThat(rows).contains(List.of("PAINT OFF FIRST", "", ""));
        assertThat(rows.get(rows.size() - 1))
                .containsExactly("", "5310-143L", "paint off for high frequency eddy current on RP-53-0001");
    }
}
