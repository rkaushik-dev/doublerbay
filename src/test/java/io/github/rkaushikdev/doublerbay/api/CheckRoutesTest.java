package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CheckRoutesTest {

    private static final String FITTED = "2025-04-02";
    private static final String OPENS = "2025-04-01";
    private static final String CLOSES = "2025-04-08";
    private static final Serial MSN = new Serial("5127");
    private static final String QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do";

    /** A database holding one tail, and a chief engineer's client against it. */
    private record Built(Database database, Client api) {}

    private static UtilisationLog logWithTwoReadings() {
        UtilisationLog opened = UtilisationLog.opened(MSN, LocalDate.of(2016, 4, 12));
        opened = opened.posted(new Snapshot(LocalDate.of(2024, 3, 14), Sample.flown(12000, 21000)));
        return opened.posted(new Snapshot(LocalDate.of(2025, 3, 1), Sample.flown(13800, 24200)));
    }

    private static Built built() {
        Database database = Desk.newDatabase();
        UnitOfWork.run(database, unit -> {
            unit.aircraft().add(Sample.aircraft());
            unit.aircraft().openLog(logWithTwoReadings());
        });
        return new Built(database, Api.clientFor(database));
    }

    /** One card on a fastener row, raised and looked up. */
    private static String assessed(Database database) {
        return assessed(database, 1);
    }

    private static String assessed(Database database, int sequence) {
        return Desk.assessedCard(database, Sample.card(sequence, Sample.cabinPointOn(10)))
                .toString();
    }

    private static Client.Answer defer(Client api, String against) {
        return defer(api, against, "1 FC", "2025-03-14");
    }

    private static Client.Answer defer(Client api, String against, String limit) {
        return defer(api, against, limit, "2025-03-14");
    }

    private static Client.Answer defer(Client api, String against, String limit, String granted) {
        return api.post("/deferrals", json("against", against, "granted", granted, "by", "j.mercer", "limit", limit));
    }

    private static Client.Answer fit(Client api, String against) {
        return api.post(
                "/repairs",
                json(
                        "against", against,
                        "sequence", 1,
                        "along", "2.000",
                        "around", "2.000",
                        "centre_along", "468.000",
                        "centre_around", "91.000",
                        "embodied", FITTED,
                        "by", "s.brandt",
                        "temporary", true));
    }

    private static Client.Answer raiseOrder(Client api, String against) {
        return api.post(
                "/orders", json("against", against, "question", QUESTION, "raised", "2025-03-14", "by", "a.okonkwo"));
    }

    private static Client.Answer pack(Client api) {
        return pack(api, "5127", OPENS, CLOSES);
    }

    private static Client.Answer pack(Client api, String serial, String opens, String closes, Object... rest) {
        Map<String, Object> params = new LinkedHashMap<>(json("opens", opens, "closes", closes));
        params.putAll(json(rest));
        return api.get("/checks/" + serial + "/pack", params);
    }

    private static Client.Answer packWithin(Client api, int within) {
        return pack(api, "5127", OPENS, CLOSES, "within", within);
    }

    @Test
    void anEmptyPackComesBackEmpty() {
        Built built = built();
        JsonNode body = pack(built.api()).json();
        assertThat(body.get("total").asInt()).isZero();
        assertThat(body.get("deferrals")).hasToString("[]");
        assertThat(body.get("references")).hasToString("[]");
    }

    @Test
    void thePackCarriesItsOwnSpan() {
        Built built = built();
        JsonNode body = pack(built.api()).json();
        assertThat(body.get("days").asInt()).isEqualTo(8);
        assertThat(body.get("opens").asText()).isEqualTo(OPENS);
    }

    @Test
    void aDeferralFallingDueLandsOnThePack() {
        Built built = built();
        defer(built.api(), assessed(built.database()));
        JsonNode body = pack(built.api()).json();
        assertThat(body.get("deferrals")).hasSize(1);
        assertThat(body.get("deferrals").get(0).get("reference").asText()).isEqualTo("DR-2025-000001");
        assertThat(body.get("references")).hasToString("[\"DR-2025-000001\"]");
    }

    @Test
    void aDeferralAlreadyOverIsCountedAsOverrun() {
        Built built = built();
        defer(built.api(), assessed(built.database()), "1 DY", "2025-03-15");
        JsonNode body = pack(built.api()).json();
        assertThat(body.get("overrun").asInt()).isEqualTo(1);
        assertThat(body.get("deferrals").get(0).get("overrun").asBoolean()).isTrue();
    }

    @Test
    void anOrderOutLandsUnderWaiting() {
        Built built = built();
        raiseOrder(built.api(), assessed(built.database()));
        JsonNode body = pack(built.api()).json();
        assertThat(body.get("waiting")).hasSize(1);
        assertThat(body.get("waiting").get(0).get("overrun").asBoolean()).isFalse();
    }

    @Test
    void aRepairPutsNothingOnAPackBeforeItsThreshold() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        assertThat(pack(built.api()).json().get("inspections")).hasToString("[]");
        assertThat(pack(built.api()).json().get("stripping")).hasToString("[]");
    }

    @Test
    void aRepairPastItsThresholdLandsOnALaterPack() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        JsonNode body = pack(built.api(), "5127", "2030-01-01", "2030-01-08").json();
        assertThat(body.get("inspections")).hasSize(1);
        assertThat(body.get("inspections").get(0).get("reference").asText()).isEqualTo("RP-53-0001");
        assertThat(body.get("stripping")).hasSize(1);
        assertThat(body.get("stripping").get(0).get("detail").asText())
                .isEqualTo("paint off for high frequency eddy current on RP-53-0001");
        assertThat(body.get("total").asInt()).isEqualTo(1);
    }

    @Test
    void thePackCanPullWorkInFromAhead() {
        Built built = built();
        defer(built.api(), assessed(built.database()), "500 FC");
        assertThat(pack(built.api()).json().get("deferrals")).hasToString("[]");
        assertThat(packWithin(built.api(), 200).json().get("deferrals")).hasSize(1);
    }

    @Test
    void aReachThatRunsBackwardsIsRefused() {
        Built built = built();
        Client.Answer answer = packWithin(built.api(), -1);
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("reaches forward");
    }

    @Test
    void aReachPastAYearIsRefused() {
        Built built = built();
        Client.Answer answer = packWithin(built.api(), 400);
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("does not pull work in");
    }

    @Test
    void aCheckThatClosesBeforeItOpensIsRefused() {
        Built built = built();
        Client.Answer answer = pack(built.api(), "5127", CLOSES, OPENS);
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void aTailThatIsNotOnTheRegisterIsNotFound() {
        Built built = built();
        assertThat(pack(built.api(), "9999", OPENS, CLOSES).status()).isEqualTo(404);
    }

    @Test
    void theDaysAreRequired() {
        Built built = built();
        assertThat(built.api().get("/checks/5127/pack").status()).isEqualTo(422);
    }

    @Test
    void aReaderMayPullAPack() {
        Built built = built();
        Client.Answer answer = built.api()
                .get("/checks/5127/pack", Map.of("opens", OPENS, "closes", CLOSES), Desk.headersFor("reader"));
        assertThat(answer.status()).isEqualTo(200);
    }

    @Test
    void aPackWithTwoKindsOfWorkKeepsThemApart() {
        Built built = built();
        defer(built.api(), assessed(built.database(), 1));
        raiseOrder(built.api(), assessed(built.database(), 2));
        JsonNode body = pack(built.api()).json();
        assertThat(body.get("deferrals")).hasSize(1);
        assertThat(body.get("waiting")).hasSize(1);
        assertThat(body.get("total").asInt()).isEqualTo(2);
    }
}
