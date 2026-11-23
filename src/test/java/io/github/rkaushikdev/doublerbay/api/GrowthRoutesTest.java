package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GrowthRoutesTest {

    private static final String AGAIN = "2025-09-01";

    /** A database holding one tail, and a chief engineer's client against it. */
    private record Built(Database database, Client api) {}

    /** One database and a chief engineer's client, off the shared fixtures. */
    private static Built built() {
        Database database = Desk.newDatabase();
        Desk.enrolled(database);
        return new Built(database, Api.clientFor(database));
    }

    private static Crack crack(String length) {
        return new Crack(Sample.inch(length), new Angle(0));
    }

    private static String raiseCrack(Database database) {
        return raiseCrack(database, 1, "0.400");
    }

    private static String raiseCrack(Database database, int sequence) {
        return raiseCrack(database, sequence, "0.400");
    }

    private static String raiseCrack(Database database, int sequence, String length) {
        UnitOfWork.run(database, unit -> unit.findings()
                .raiseFinding(Sample.cardWith()
                        .sequence(sequence)
                        .kind(DamageKind.CRACK)
                        .geometry(crack(length))
                        .at(Sample.cabinPointOn(10))
                        .origin(DamageOrigin.FATIGUE)
                        .build()));
        return new FindingId(2025, sequence).toString();
    }

    private static String raiseDent(Database database) {
        return raiseDent(database, 9);
    }

    private static String raiseDent(Database database, int sequence) {
        UnitOfWork.run(database, unit -> unit.findings().raiseFinding(Sample.card(sequence, Sample.cabinPointOn(10))));
        return new FindingId(2025, sequence).toString();
    }

    private static Client.Answer measure(Client api, String reference) {
        return measure(api, reference, "0.500", 14800, AGAIN);
    }

    private static Client.Answer measure(Client api, String reference, String length, int cycles) {
        return measure(api, reference, length, cycles, AGAIN);
    }

    private static Client.Answer measure(Client api, String reference, String length, int cycles, String on) {
        return api.post(
                "/findings/" + reference + "/revisions",
                json(
                        "found_on", on,
                        "at_usage", json("cycles", cycles, "hours", (cycles * 2) + ".0"),
                        "measurements",
                                json(
                                        "length",
                                        length,
                                        "bearing",
                                        "0.0",
                                        "through_thickness",
                                        false,
                                        "stop_drilled",
                                        false,
                                        "tips_found",
                                        2)));
    }

    private static List<String> references(JsonNode rows) {
        List<String> held = new ArrayList<>();
        rows.forEach(one -> held.add(one.get("reference").asText()));
        return held;
    }

    @Test
    void oneReadingGivesNoSteps() {
        Built built = built();
        JsonNode body =
                built.api().get("/growth/" + raiseCrack(built.database())).json();
        assertThat(body.get("readings").asInt()).isEqualTo(1);
        assertThat(body.get("steps")).hasToString("[]");
        assertThat(body.get("has_grown").asBoolean()).isFalse();
    }

    @Test
    void twoReadingsGiveAStep() {
        Built built = built();
        String reference = raiseCrack(built.database());
        measure(built.api(), reference);
        JsonNode body = built.api().get("/growth/" + reference).json();
        assertThat(body.get("readings").asInt()).isEqualTo(2);
        assertThat(body.get("has_grown").asBoolean()).isTrue();
        assertThat(body.get("total").asText()).isEqualTo("0.100");
    }

    @Test
    void theStepCarriesItsRate() {
        Built built = built();
        String reference = raiseCrack(built.database());
        measure(built.api(), reference);
        JsonNode step =
                built.api().get("/growth/" + reference).json().get("steps").get(0);
        assertThat(step.get("measurable").asBoolean()).isTrue();
        assertThat(step.get("rate").isNull()).isFalse();
        assertThat(step.get("cycles").asInt()).isPositive();
    }

    @Test
    void aShortStepHasNoRate() {
        Built built = built();
        String reference = raiseCrack(built.database());
        measure(built.api(), reference, "0.500", 13910);
        JsonNode body = built.api().get("/growth/" + reference).json();
        assertThat(body.get("steps").get(0).get("measurable").asBoolean()).isFalse();
        assertThat(body.get("worst_rate").isNull()).isTrue();
    }

    @Test
    void twoReadingsAreNotATrend() {
        Built built = built();
        String reference = raiseCrack(built.database());
        measure(built.api(), reference);
        assertThat(built.api()
                        .get("/growth/" + reference)
                        .json()
                        .get("is_a_trend")
                        .asBoolean())
                .isFalse();
    }

    @Test
    void threeReadingsAreATrend() {
        Built built = built();
        String reference = raiseCrack(built.database());
        measure(built.api(), reference, "0.450", 14300, "2025-06-01");
        measure(built.api(), reference, "0.500", 14800, AGAIN);
        assertThat(built.api()
                        .get("/growth/" + reference)
                        .json()
                        .get("is_a_trend")
                        .asBoolean())
                .isTrue();
    }

    @Test
    void aSlowCrackIsNotFlaggedAsFast() {
        Built built = built();
        String reference = raiseCrack(built.database());
        measure(built.api(), reference, "0.401", 20000);
        assertThat(built.api().get("/growth/" + reference).json().get("fast").asBoolean())
                .isFalse();
    }

    @Test
    void aFastCrackIsFlagged() {
        Built built = built();
        String reference = raiseCrack(built.database());
        measure(built.api(), reference, "0.900", 14800);
        assertThat(built.api().get("/growth/" + reference).json().get("fast").asBoolean())
                .isTrue();
    }

    @Test
    void aDentIsNotTrackedForGrowth() {
        Built built = built();
        assertThat(built.api().get("/growth/" + raiseDent(built.database())).status())
                .isEqualTo(422);
    }

    @Test
    void aCardThatIsNotOnFileIsRefused() {
        Built built = built();
        assertThat(built.api().get("/growth/DR-2025-000099").status()).isEqualTo(404);
    }

    @Test
    void theFleetListCountsWhatIsTracked() {
        Built built = built();
        raiseCrack(built.database(), 1);
        raiseDent(built.database(), 2);
        JsonNode body = built.api().get("/growth", Map.of("on", "2025-09-01")).json();
        assertThat(body.get("tracked").asInt()).isEqualTo(1);
        assertThat(body.get("fast")).hasToString("[]");
    }

    @Test
    void aFastCrackLandsOnTheFleetList() {
        Built built = built();
        String reference = raiseCrack(built.database(), 1);
        measure(built.api(), reference, "0.900", 14800);
        JsonNode body = built.api().get("/growth", Map.of("on", "2025-09-01")).json();
        assertThat(body.get("fast")).hasSize(1);
        assertThat(body.get("fast").get(0).get("reference").asText()).isEqualTo("DR-2025-000001");
    }

    @Test
    void theFleetListPutsTheFastestFirst() {
        Built built = built();
        String slow = raiseCrack(built.database(), 1);
        String fast = raiseCrack(built.database(), 2);
        measure(built.api(), slow, "0.700", 14800);
        measure(built.api(), fast, "1.400", 14800);
        JsonNode body = built.api().get("/growth", Map.of("on", "2025-09-01")).json();
        assertThat(references(body.get("fast"))).containsExactly(fast, slow);
    }

    @Test
    void theFleetListNeedsADay() {
        Built built = built();
        assertThat(built.api().get("/growth").status()).isEqualTo(422);
    }

    @Test
    void aTailListsItsTrackedCracks() {
        Built built = built();
        raiseCrack(built.database(), 1);
        raiseDent(built.database(), 2);
        assertThat(built.api().get("/growth/aircraft/5127").json()).hasSize(1);
    }

    @Test
    void aTailWithNoCracksComesBackEmpty() {
        Built built = built();
        raiseDent(built.database(), 2);
        assertThat(built.api().get("/growth/aircraft/5127").json()).hasToString("[]");
    }

    @Test
    void aTailThatIsNotOnTheRegisterIsNotFound() {
        Built built = built();
        assertThat(built.api().get("/growth/aircraft/9999").status()).isEqualTo(404);
    }

    @Test
    void aTailListPutsTheFastestFirst() {
        Built built = built();
        String slow = raiseCrack(built.database(), 1);
        String fast = raiseCrack(built.database(), 2);
        measure(built.api(), slow, "0.500", 14800);
        measure(built.api(), fast, "1.400", 14800);
        assertThat(references(built.api().get("/growth/aircraft/5127").json())).containsExactly(fast, slow);
    }

    @Test
    void aReaderMayPullTheGrowth() {
        Built built = built();
        Client.Answer answer =
                built.api().get("/growth/" + raiseCrack(built.database()), Map.of(), Desk.headersFor("reader"));
        assertThat(answer.status()).isEqualTo(200);
    }

    @Test
    void aCrackIsHeldAgainstTheAnalysisForTheSkinItIsIn() {
        Built built = built();
        String reference = raiseCrack(built.database());
        measure(built.api(), reference, "0.475", 14800);
        JsonNode body = built.api().get("/growth/" + reference).json();
        assertThat(body.get("basis").asText()).isEqualTo("critical 6.000, 0.100 per 1000 FC");
        assertThat(body.get("fast").asBoolean()).isTrue();
        assertThat(body.get("outgrows_the_basis").asBoolean()).isFalse();
        assertThat(body.get("repeat_at_measured_rate").isNull()).isTrue();
    }

    @Test
    void aCrackGrowingFasterThanTheAnalysisSaysOutgrowsIt() {
        Built built = built();
        String reference = raiseCrack(built.database());
        measure(built.api(), reference, "0.900", 14800);
        JsonNode card = built.api().get("/growth/" + reference).json();
        assertThat(card.get("outgrows_the_basis").asBoolean()).isTrue();
        assertThat(card.get("repeat_at_measured_rate").asText()).isEqualTo("every 5350 FC");
        JsonNode fleet = built.api().get("/growth?on=2025-06-01").json();
        assertThat(fleet.get("fast").get(0).get("outgrows_the_basis").asBoolean())
                .isTrue();
        assertThat(built.api()
                        .get("/growth/aircraft/5127")
                        .json()
                        .get(0)
                        .get("basis")
                        .asText())
                .startsWith("critical 6.000");
    }

    @Test
    void aCrackWithOneReadingHasShownNothingAgainstTheAnalysis() {
        Built built = built();
        JsonNode body =
                built.api().get("/growth/" + raiseCrack(built.database())).json();
        assertThat(body.get("outgrows_the_basis").asBoolean()).isFalse();
    }

    @Test
    void aTailConeCrackUnderTheFastLineIsStillListedWhenItOutgrowsItsAnalysis() {
        Built built = built();
        UnitOfWork.run(built.database(), unit -> unit.findings()
                .raiseFinding(Sample.cardWith()
                        .sequence(3)
                        .kind(DamageKind.CRACK)
                        .geometry(crack("0.400"))
                        .at(Sample.cabinPointAtFrame(50, 10))
                        .origin(DamageOrigin.FATIGUE)
                        .build()));
        String reference = new FindingId(2025, 3).toString();
        measure(built.api(), reference, "0.440", 14800);
        JsonNode card = built.api().get("/growth/" + reference).json();
        assertThat(card.get("fast").asBoolean()).isFalse();
        assertThat(card.get("basis").asText()).isEqualTo("critical 8.000, 0.040 per 1000 FC");
        assertThat(card.get("outgrows_the_basis").asBoolean()).isTrue();
        JsonNode fleet = built.api().get("/growth?on=2025-06-01").json();
        assertThat(references(fleet.get("fast"))).containsExactly(reference);
    }
}
