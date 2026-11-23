package io.github.rkaushikdev.doublerbay.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.service.Intake;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CorrosionRoutesTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final LocalDate LATER = LocalDate.of(2026, 3, 14);

    /** A database holding one tail, and a chief engineer's client against it. */
    private record Built(Database database, Client api) {}

    /** One database and a chief engineer's client, off the shared fixtures. */
    private static Built built() {
        Database database = Desk.newDatabase();
        Desk.enrolled(database);
        return new Built(database, Api.clientFor(database));
    }

    private static Corrosion rust(String depth) {
        return new Corrosion(Sample.inch("2.000"), Sample.inch("1.000"), Sample.inch(depth));
    }

    /** The parts of a rust card a test varies, with the defaults every other test takes. */
    private static final class Rust {
        private int sequence = 1;
        private LocalDate found = DAY;
        private boolean urgent;
        private String depth = "0.030";

        Rust sequence(int value) {
            sequence = value;
            return this;
        }

        Rust found(LocalDate value) {
            found = value;
            return this;
        }

        Rust urgent() {
            urgent = true;
            return this;
        }

        Rust depth(String value) {
            depth = value;
            return this;
        }

        Intake raise(Database database) {
            Finding finding = Sample.cardWith()
                    .sequence(sequence)
                    .kind(DamageKind.CORROSION)
                    .geometry(rust(depth))
                    .at(Sample.cabinPoint(15, "10.000", 6, "4.000"))
                    .found(found)
                    .origin(DamageOrigin.ENVIRONMENT)
                    .build();
            return UnitOfWork.apply(
                    database, unit -> Intake.takeIn(unit, Book.v320(), new TypeCatalogue(), finding, urgent));
        }
    }

    private static Rust rust() {
        return new Rust();
    }

    private static Map<String, String> window(String since, String until) {
        return Map.of("since", since, "until", until);
    }

    @Test
    void aTailWithNoCorrosionComesBackEmpty() {
        Built built = built();
        assertThat(built.api().get("/corrosion/aircraft/5127").json()).hasToString("[]");
    }

    @Test
    void aGradedFindingComesBackOnTheTail() {
        Built built = built();
        rust().raise(built.database());
        JsonNode body = built.api().get("/corrosion/aircraft/5127").json();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("level").asInt()).isEqualTo(1);
        assertThat(body.get(0).get("part").asText()).startsWith("53");
    }

    @Test
    void aTailThatIsNotOnTheRegisterIsNotFound() {
        Built built = built();
        assertThat(built.api().get("/corrosion/aircraft/9999").status()).isEqualTo(404);
    }

    @Test
    void aLevelCanBeAskedForAcrossTheFleet() {
        Built built = built();
        rust().sequence(1).raise(built.database());
        rust().sequence(2).found(LATER).raise(built.database());
        assertThat(built.api().get("/corrosion/level/2").json()).hasSize(1);
    }

    @Test
    void aLevelThatIsNotOneIsRefused() {
        Built built = built();
        Client.Answer answer = built.api().get("/corrosion/level/9");
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("not a corrosion level");
    }

    @Test
    void nothingIsUnreportedBeforeALevelThree() {
        Built built = built();
        rust().raise(built.database());
        assertThat(built.api().get("/corrosion/unreported").json()).hasToString("[]");
    }

    @Test
    void aLevelThreeLandsOnTheUnreportedList() {
        Built built = built();
        rust().urgent().raise(built.database());
        JsonNode body = built.api().get("/corrosion/unreported").json();
        assertThat(body.get(0).get("must_be_reported").asBoolean()).isTrue();
        assertThat(body.get(0).get("report_by").asText()).isEqualTo("2025-03-17");
    }

    @Test
    void markingOneReportedClearsTheList() {
        Built built = built();
        rust().urgent().raise(built.database());
        Client.Answer answer = built.api().post("/corrosion/DR-2025-000001/reported", Map.of("on", "2025-03-15"));
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json()).hasToString("[]");
    }

    @Test
    void reportingOneThatIsNotOnTheRegisterIsNotFound() {
        Built built = built();
        Client.Answer answer = built.api().post("/corrosion/DR-2025-000099/reported", Map.of("on", "2025-03-15"));
        assertThat(answer.status()).isEqualTo(404);
    }

    @Test
    void aFindingCannotBeReportedBeforeItWasFound() {
        Built built = built();
        rust().urgent().raise(built.database());
        Client.Answer answer = built.api().post("/corrosion/DR-2025-000001/reported", Map.of("on", "2024-01-01"));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void aMechanicMayNotMarkOneReported() {
        Built built = built();
        rust().urgent().raise(built.database());
        Client.Answer answer = built.api()
                .post("/corrosion/DR-2025-000001/reported", Map.of("on", "2025-03-15"), Desk.headersFor("mechanic"));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void theProgrammeReportSaysItIsCoping() {
        Built built = built();
        rust().depth("0.002").raise(built.database());
        JsonNode body = built.api()
                .get("/corrosion/aircraft/5127/programme", window("2020-01-01", "2026-12-31"))
                .json();
        assertThat(body.get("coping").asBoolean()).isTrue();
        assertThat(body.get("total").asInt()).isEqualTo(1);
        assertThat(body.get("escalation_rate").asText()).isEqualTo("0.000");
    }

    @Test
    void theProgrammeReportNamesItsDenominator() {
        Built built = built();
        JsonNode body = built.api()
                .get("/corrosion/aircraft/5127/programme", window("2020-01-01", "2026-12-31"))
                .json();
        assertThat(body.get("denominator").asText()).contains("not panels inspected");
    }

    @Test
    void aRepeatShowsUpOnTheProgrammeReport() {
        Built built = built();
        rust().sequence(1).raise(built.database());
        rust().sequence(2).found(LATER).raise(built.database());
        JsonNode body = built.api()
                .get("/corrosion/aircraft/5127/programme", window("2020-01-01", "2026-12-31"))
                .json();
        assertThat(body.get("repeats").get(0).get("times").asInt()).isEqualTo(2);
        assertThat(body.get("repeats").get(0).get("persistent").asBoolean()).isFalse();
        assertThat(body.get("coping").asBoolean()).isFalse();
    }

    @Test
    void theWindowCutsTheProgrammeReport() {
        Built built = built();
        rust().raise(built.database());
        JsonNode body = built.api()
                .get("/corrosion/aircraft/5127/programme", window("2026-01-01", "2026-12-31"))
                .json();
        assertThat(body.get("total").asInt()).isZero();
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRefused() {
        Built built = built();
        Client.Answer answer =
                built.api().get("/corrosion/aircraft/5127/programme", window("2026-12-31", "2020-01-01"));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void theProgrammeReportOnATailThatIsNotThereIsNotFound() {
        Built built = built();
        Client.Answer answer =
                built.api().get("/corrosion/aircraft/9999/programme", window("2020-01-01", "2026-12-31"));
        assertThat(answer.status()).isEqualTo(404);
    }

    @Test
    void aReaderMayPullTheProgrammeReport() {
        Built built = built();
        Client.Answer answer = built.api()
                .get(
                        "/corrosion/aircraft/5127/programme",
                        window("2020-01-01", "2026-12-31"),
                        Desk.headersFor("reader"));
        assertThat(answer.status()).isEqualTo(200);
    }
}
