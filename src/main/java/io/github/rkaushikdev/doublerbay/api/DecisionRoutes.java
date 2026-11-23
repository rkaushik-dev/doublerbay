package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The routes that grant, tighten and lift a deferral.
 *
 * <p>Granting is the one write on this service that changes what an aeroplane is allowed to do, so
 * it is the one guarded by the role rather than by the key. A mechanic can raise a card at three in
 * the morning and post a counter reading, and cannot decide that the fleet keeps flying with the
 * crack they just found.
 *
 * <p>The limit is not sent as four numbers. It arrives as the text the manual prints, which is what
 * the engineer writes on the sheet anyway, and it is parsed on this side of the wire. A client that
 * had to know that ninety days and three months are different clocks would get it wrong, and the
 * parse refuses what it cannot read rather than defaulting.
 */
@RestController
@Tag(name = "deferrals")
final class DecisionRoutes {

    private final Deps deps;

    DecisionRoutes(Deps deps) {
        this.deps = deps;
    }

    @PostMapping("/deferrals")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Action.DEFER)
    @Operation(summary = "Defer a card, writing the limit and the disposition move in one go.")
    DeferralOut grantDeferral(@Valid @RequestBody DeferralIn body) {
        FindingId against = Dependencies.referenceFrom(body.against());
        Deferral deferral = deps.apply(unit -> {
            Finding finding = unit.findings().latest(against);
            Usage atUsage = unit.aircraft().logFor(finding.serial()).at(body.granted());
            InspectionMethod method = Schemas.readEnum(
                    InspectionMethod.values(), InspectionMethod::value, body.method(), "way of inspecting");
            Interval engineerSet = body.limit()
                    .filter(text -> !text.isEmpty())
                    .map(Interval::parse)
                    .orElse(null);
            Deferral granted = Deferral.grant(
                    finding,
                    body.granted(),
                    atUsage,
                    body.by(),
                    method,
                    engineerSet,
                    body.nextCheck().orElse(null),
                    body.reason());
            unit.deferrals().grant(granted);
            DispositionRecord record = unit.dispositions().get(against);
            unit.dispositions()
                    .save(record.movedTo(DispositionState.DEFERRED, body.granted(), body.by(), body.reason()));
            return granted;
        });
        return deferralOut(deferral);
    }

    @GetMapping("/deferrals")
    @Operation(summary = "Every deferral still being flown against, oldest first.")
    Page<DeferralOut> live(
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "limit", required = false) Integer limit) {
        Window asked = Paging.window(offset, limit);
        return deps.apply(unit -> Paging.paged(unit.deferrals().live(), asked, DecisionRoutes::deferralOut));
    }

    @GetMapping("/deferrals/{reference}")
    @Operation(summary = "The live deferral against one card.")
    DeferralOut readDeferral(@PathVariable("reference") String reference) {
        return deps.apply(unit -> deferralOut(unit.deferrals().get(Dependencies.referenceFrom(reference))));
    }

    @PostMapping("/deferrals/{reference}/tighten")
    @Requires(Action.DEFER)
    @Operation(summary = "Cut a live deferral back to a shorter limit.")
    DeferralOut tighten(@PathVariable("reference") String reference, @Valid @RequestBody TightenIn body) {
        FindingId held = Dependencies.referenceFrom(reference);
        return deps.apply(unit -> {
            Deferral current = unit.deferrals().get(held);
            unit.deferrals().tighten(current.tightenedTo(Interval.parse(body.limit()), body.by(), body.reason()));
            unit.flush();
            return deferralOut(unit.deferrals().get(held));
        });
    }

    @PostMapping("/deferrals/{reference}/lift")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Requires(Action.DEFER)
    @Operation(summary = "Close a deferral because the damage under it has been dealt with.")
    void lift(@PathVariable("reference") String reference, @Valid @RequestBody LiftIn body) {
        deps.run(unit -> unit.deferrals().lift(Dependencies.referenceFrom(reference), body.on()));
    }

    /** One deferral as it goes back, with where its limit lands. */
    static DeferralOut deferralOut(Deferral deferral) {
        return new DeferralOut(
                deferral.against().toString(),
                deferral.granted(),
                deferral.by(),
                InspectionRoutes.usageOut(deferral.atUsage()),
                deferral.limit().toString(),
                DueOut.of(deferral.due()),
                deferral.reInspectWith().value(),
                deferral.reason());
    }
}
