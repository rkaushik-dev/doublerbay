package io.github.rkaushikdev.doublerbay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.damage.Dent;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliMeasureTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final String AGAIN = "2025-09-01";
    private static final FindingId AGAINST = new FindingId(2025, 1);

    @TempDir
    Path tmp;

    private static Ran invoke(Database database, String... args) {
        return Cli.invoke(database, args);
    }

    private static Database stocked() {
        return Desk.stocked();
    }

    private static String raised(Database database) {
        UnitOfWork.run(database, unit -> {
            unit.findings().raiseFinding(Sample.card(1, Sample.cabinPointOn(10, "4.000")));
            unit.dispositions()
                    .save(DispositionRecord.raised(AGAINST).movedTo(DispositionState.ASSESSED, DAY, "a.okonkwo"));
        });
        return AGAINST.toString();
    }

    private static void deferred(Database database, String reference) {
        invoke(database, "defer", "grant", reference, "--by", "j.mercer", "--on", "2025-03-14", "--limit", "500 FC");
    }

    private String numbers(String depth) {
        Map<String, Object> measured = new LinkedHashMap<>();
        measured.put("depth", depth);
        measured.put("along", "3.000");
        measured.put("around", "2.000");
        measured.put("sharp_bottom", false);
        measured.put("cracked", false);
        return Cli.written(tmp, "again.json", Map.of("measurements", measured)).toString();
    }

    private Ran measure(Database database, String reference) {
        return measure(database, reference, "0.025", AGAIN);
    }

    private Ran measure(Database database, String reference, String depth) {
        return measure(database, reference, depth, AGAIN);
    }

    private Ran measure(Database database, String reference, String depth, String on) {
        return invoke(database, "cards", "measure", reference, numbers(depth), "--on", on);
    }

    @Test
    void aCardCanBeMeasuredAgain() {
        Database database = stocked();
        Ran answer = measure(database, raised(database));
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("revision 2");
    }

    @Test
    void theNewNumbersLandOnTheCard() {
        Database database = stocked();
        measure(database, raised(database));
        Dent dent = UnitOfWork.apply(
                database, unit -> (Dent) unit.findings().latest(AGAINST).geometry());
        assertThat(dent.depth()).isEqualTo(Sample.inch("0.025"));
    }

    @Test
    void theFirstReadingIsStillThere() {
        Database database = stocked();
        measure(database, raised(database));
        int revisions = UnitOfWork.apply(
                database, unit -> unit.findings().history(AGAINST).size());
        assertThat(revisions).isEqualTo(2);
    }

    @Test
    void aDocumentWithNoMeasurementsIsRefused() {
        Database database = stocked();
        String reference = raised(database);
        Path path = Cli.writtenText(tmp, "empty.json", "{}");
        Ran answer = invoke(database, "cards", "measure", reference, path.toString(), "--on", AGAIN);
        assertThat(answer.exitCode()).isNotZero();
        assertThat(answer.failure()).contains("no measurements block");
    }

    @Test
    void aDocumentThatIsAListIsRefused() {
        Database database = stocked();
        String reference = raised(database);
        Path path = Cli.writtenText(tmp, "list.json", "[]");
        Ran answer = invoke(database, "cards", "measure", reference, path.toString(), "--on", AGAIN);
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void aCardThatIsNotOnFileCannotBeMeasured() {
        Database database = stocked();
        Ran answer = measure(database, "DR-2025-000099");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void aDayBeforeItWasFoundIsRefused() {
        Database database = stocked();
        Ran answer = measure(database, raised(database), "0.025", "2024-01-01");
        assertThat(answer.exitCode()).isNotZero();
        assertThat(answer.failure()).contains("before it was found");
    }

    @Test
    void aMeasurementThatIsNotOneIsRefused() {
        Database database = stocked();
        Ran answer = measure(database, raised(database), "deep");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void aDamageThatHasNotMovedSaysOnlyItsAnswer() {
        Database database = stocked();
        String printed = measure(database, raised(database), "0.020").output();
        assertThat(printed).doesNotContain(" to ");
    }

    @Test
    void aDamageThatGrewNamesBothAnswers() {
        Database database = stocked();
        String printed = measure(database, raised(database), "0.400").output();
        assertThat(printed).contains(" to ");
    }

    @Test
    void aDeferredCardThatGrewPrintsTheWarning() {
        Database database = stocked();
        String reference = raised(database);
        deferred(database, reference);
        String printed = measure(database, reference, "0.400").output();
        assertThat(printed).contains("tell an engineer");
    }

    @Test
    void aDeferredCardThatDidNotGrowPrintsNoWarning() {
        Database database = stocked();
        String reference = raised(database);
        deferred(database, reference);
        String printed = measure(database, reference, "0.020").output();
        assertThat(printed).doesNotContain("tell an engineer");
    }

    @Test
    void aCardThatWasNeverDeferredPrintsNoWarning() {
        Database database = stocked();
        String printed = measure(database, raised(database), "0.400").output();
        assertThat(printed).doesNotContain("tell an engineer");
    }

    @Test
    void theHistoryCommandShowsBothRevisions() {
        Database database = stocked();
        String reference = raised(database);
        measure(database, reference);
        String printed = invoke(database, "cards", "history", reference).output();
        assertThat(printed.strip().split("\n", -1)).hasSize(3);
    }
}
