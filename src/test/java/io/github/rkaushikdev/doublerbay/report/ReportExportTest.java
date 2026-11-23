package io.github.rkaushikdev.doublerbay.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.fleet.Forecast;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.repair.Schemes;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportExportTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final Serial MSN = new Serial("5127");

    /**
     * A written file read back the way a spreadsheet reads it: rows end at a line break, fields split
     * on commas, and a quoted field keeps its commas, line breaks and doubled quotes.
     */
    private static List<List<String>> readBack(String written) {
        List<List<String>> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean started = false;
        for (int at = 0; at < written.length(); at++) {
            char one = written.charAt(at);
            if (quoted) {
                if (one == '"' && at + 1 < written.length() && written.charAt(at + 1) == '"') {
                    field.append('"');
                    at++;
                } else if (one == '"') {
                    quoted = false;
                } else {
                    field.append(one);
                }
            } else if (one == '"') {
                quoted = true;
                started = true;
            } else if (one == ',') {
                fields.add(field.toString());
                field.setLength(0);
                started = true;
            } else if (one == '\r' || one == '\n') {
                if (one == '\r' && at + 1 < written.length() && written.charAt(at + 1) == '\n') {
                    at++;
                }
                if (started || field.length() > 0) {
                    fields.add(field.toString());
                }
                rows.add(List.copyOf(fields));
                fields.clear();
                field.setLength(0);
                started = false;
            } else {
                field.append(one);
                started = true;
            }
        }
        if (started || field.length() > 0) {
            fields.add(field.toString());
            rows.add(List.copyOf(fields));
        }
        return rows;
    }

    private static Database stocked() {
        return Desk.stocked();
    }

    private static void defer(Database database) {
        FindingId reference = new FindingId(2025, 1);
        UnitOfWork.run(database, unit -> {
            unit.findings().raiseFinding(Sample.card(1));
            unit.dispositions()
                    .save(DispositionRecord.raised(reference).movedTo(DispositionState.ASSESSED, DAY, "a.okonkwo"));
        });
        UnitOfWork.run(database, unit -> {
            unit.deferrals()
                    .grant(Deferral.grant(
                            reference,
                            DAY,
                            Sample.flown(13800, 24200),
                            "j.mercer",
                            InspectionMethod.DVI,
                            Interval.parse("1 FC"),
                            null,
                            ""));
            DispositionRecord held = unit.dispositions().get(reference);
            unit.dispositions().save(held.movedTo(DispositionState.DEFERRED, DAY, "j.mercer"));
        });
    }

    private static List<DueItem> due() {
        Deferral deferral = Deferral.grant(
                new FindingId(2025, 41),
                DAY,
                Sample.flown(13800, 24200),
                "j.mercer",
                InspectionMethod.DVI,
                Interval.parse("500 FC"),
                null,
                "");
        Forecast rate = new Forecast(Sample.flyingLog(), new BigDecimal("4"), new BigDecimal("7"));
        return DueList.of(List.of(deferral), MSN, Sample.flown(13800, 24200), rate, DAY);
    }

    private static Finding crack(String length, int at, int revision) {
        return Sample.card(1, DamageKind.CRACK, new Crack(Sample.inch(length), new Angle(0)), DamageOrigin.FATIGUE)
                .withRevision(revision)
                .withAtUsage(Sample.flown(at, at * 2L));
    }

    private static Growth growth(int cycles) {
        return Growth.of(List.of(crack("0.400", 13800, 1), crack("0.500", cycles, 2)));
    }

    private static Growth growth() {
        return growth(14800);
    }

    @Test
    void aFileCarriesItsHeader() {
        assertThat(readBack(Export.asCsv(List.of("a", "b"), List.of())).get(0)).containsExactly("a", "b");
    }

    @Test
    void aFileWithNoHeaderIsRefused() {
        assertThatThrownBy(() -> Export.asCsv(List.of(), List.of()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has a header on it");
    }

    @Test
    void aRowOfTheWrongWidthIsRefusedByPosition() {
        assertThatThrownBy(() -> Export.asCsv(List.of("a", "b"), List.of(List.of("x", "y"), List.of("z"))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("row 3 has 1 columns");
    }

    @Test
    void aValueWithACommaInItIsQuoted() {
        String written = Export.asCsv(List.of("a"), List.of(List.of("one, two")));
        assertThat(readBack(written).get(1)).containsExactly("one, two");
    }

    @Test
    void aValueWithAQuoteInItSurvives() {
        String written = Export.asCsv(List.of("a"), List.of(List.of("he said \"no\"")));
        assertThat(readBack(written).get(1)).containsExactly("he said \"no\"");
    }

    @Test
    void theRowsAreCountedWithoutTheHeader() {
        assertThat(Export.lineCount(Export.asCsv(List.of("a"), List.of(List.of("x"), List.of("y")))))
                .isEqualTo(2);
        assertThat(Export.lineCount(Export.asCsv(List.of("a"), List.of()))).isZero();
    }

    @Test
    void aDueListExportsItsColumns() {
        List<DueItem> items = due();
        List<List<String>> written = readBack(Export.dueAsCsv(items));
        assertThat(written.get(0)).isEqualTo(Export.DUE_HEADER);
        assertThat(written.get(1).get(0)).isEqualTo("5127");
        assertThat(written.get(1).get(1)).isEqualTo("DR-2025-000041");
    }

    @Test
    void anEmptyDueListIsAHeaderAndNothingElse() {
        assertThat(Export.lineCount(Export.dueAsCsv(List.of()))).isZero();
    }

    @Test
    void aDueLineSaysWhetherItIsOver() {
        List<String> line = readBack(Export.dueAsCsv(due())).get(1);
        assertThat(line.get(line.size() - 1)).isIn("yes", "no");
    }

    @Test
    void aWorkPackExportsTheBlockAsAColumn() {
        Database database = stocked();
        defer(database);
        WorkPack pack = UnitOfWork.apply(
                database, unit -> WorkPacks.build(unit, MSN, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 8), 400));
        List<List<String>> written = readBack(Export.packAsCsv(pack));
        assertThat(written.get(0)).isEqualTo(Export.PACK_HEADER);
        assertThat(written.get(1).get(0)).isEqualTo("deferral");
    }

    @Test
    void anEmptyPackExportsAHeaderAndNothingElse() {
        Database database = stocked();
        WorkPack pack = UnitOfWork.apply(
                database, unit -> WorkPacks.build(unit, MSN, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 8)));
        assertThat(Export.lineCount(Export.packAsCsv(pack))).isZero();
    }

    @Test
    void growthExportsOneRowPerCard() {
        List<List<String>> written = readBack(Export.growthAsCsv(List.of(growth())));
        assertThat(written.get(0)).isEqualTo(Export.GROWTH_HEADER);
        assertThat(written.get(1).get(1)).isEqualTo("2");
    }

    @Test
    void growthWithNoMeasurableRateLeavesTheColumnBlank() {
        List<List<String>> written = readBack(Export.growthAsCsv(List.of(growth(13810))));
        assertThat(written.get(1).get(3)).isEmpty();
    }

    @Test
    void growthSaysWhetherItIsATrend() {
        List<String> line = readBack(Export.growthAsCsv(List.of(growth()))).get(1);
        assertThat(line.get(line.size() - 1)).isEqualTo("no");
    }

    private static String text(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Test
    void theDueCommandWritesAFile(@TempDir Path tmp) {
        Database database = stocked();
        defer(database);
        Path path = tmp.resolve("due.csv");
        Ran answer = Cli.invoke(database, "report", "due", "--on", "2025-03-14", "--csv", path.toString());
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("wrote 1 rows");
        assertThat(readBack(text(path)).get(0)).isEqualTo(Export.DUE_HEADER);
    }

    @Test
    void theDueCommandStillPrintsWhenNoFileIsAskedFor() {
        Database database = stocked();
        defer(database);
        Ran answer = Cli.invoke(database, "report", "due", "--on", "2025-03-14");
        assertThat(answer.output()).contains("DR-2025-000001");
    }

    @Test
    void thePackCommandWritesAFile(@TempDir Path tmp) {
        Database database = stocked();
        defer(database);
        Path path = tmp.resolve("pack.csv");
        Ran answer = Cli.invoke(
                database,
                "check",
                "pack",
                "--tail",
                "5127",
                "--opens",
                "2025-04-01",
                "--closes",
                "2025-04-08",
                "--within",
                "400",
                "--csv",
                path.toString());
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("wrote 1 rows");
        assertThat(readBack(text(path)).get(1).get(0)).isEqualTo("deferral");
    }

    @Test
    void thePackCommandStillPrintsWhenNoFileIsAskedFor() {
        Database database = stocked();
        Ran answer = Cli.invoke(
                database, "check", "pack", "--tail", "5127", "--opens", "2025-04-01", "--closes", "2025-04-08");
        assertThat(answer.output()).contains("nothing for this check");
    }

    @Test
    void aRepairRegisterRowCarriesThePageTheCommitmentAndTheCountersItWentOnAt() {
        Placement placed = Placement.place(Sample.tailContext(), Sample.card(Sample.cabinPointOn(10, "4.000")));
        RepairRecord fitted = new RepairRecord(
                new RepairId(53, 1),
                placed.part(),
                new FindingId(2025, 1),
                Schemes.selectScheme(Sample.inch("2.000"), Sample.inch("2.000"), placed, Reference.parse("53-40-01")),
                placed.centre(),
                LocalDate.of(2025, 4, 2),
                Sample.flown(13900, 24400));
        RepairRecord off = fitted.removedOn(LocalDate.of(2025, 9, 1));
        List<List<String>> rows = Export.repairRows(List.of(fitted, off));
        assertThat(rows.get(0)).hasSize(Export.REPAIR_HEADER.size());
        assertThat(rows.get(0).subList(0, 6))
                .containsExactly("RP-53-0001", placed.part().toString(), "DR-2025-000001", "53-40-01", "A", "no");
        assertThat(rows.get(0).subList(7, 10)).containsExactly("2025-04-02", "13900", "");
        assertThat(rows.get(1).get(9)).isEqualTo("2025-09-01");
        assertThat(Export.lineCount(Export.repairsAsCsv(List.of(fitted, off)))).isEqualTo(2);
    }
}
