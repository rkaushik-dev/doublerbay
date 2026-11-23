package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.repair.DoublerPlan;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.repair.RepairScheme;
import io.github.rkaushikdev.doublerbay.repair.Schemes;
import io.github.rkaushikdev.doublerbay.service.Embodiment;
import io.github.rkaushikdev.doublerbay.service.Repairs;
import io.github.rkaushikdev.doublerbay.store.StoreContext;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The routes that draw a repair, fit it, and take it off again.
 *
 * <p>A repair is not sent in as a drawing. The caller says how big the hole is and where, and the
 * service works out the doubler, because the sizing rules are the whole point of having a service. A
 * client that could post its own gauge and fastener pitch would be a client that could post a repair
 * the manual does not allow, and the desk would find out at the audit.
 *
 * <p>So the request is small: which card, how big, and whether it is temporary. The response is the
 * whole drawing, because that is what goes on the sheet the shop works from.
 */
@RestController
@Tag(name = "repairs")
final class RepairRoutes {

    private final Deps deps;

    RepairRoutes(Deps deps) {
        this.deps = deps;
    }

    @PostMapping("/repairs")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Action.DISPOSITION)
    @Operation(summary = "Work out the repair for a hole, fit it, and move the card behind it.")
    EmbodiedOut drawAndFit(@Valid @RequestBody EmbodyIn body) {
        FindingId against = Dependencies.referenceFrom(body.against());
        return deps.apply(unit -> {
            Finding finding = unit.findings().latest(against);
            TailContext tail = StoreContext.contextFor(unit, deps.catalogue(), finding.serial(), body.embodied());
            Placement placement = Placement.place(tail, finding);
            Length along = Schemas.inches(body.along(), "repair length");
            Length around = Schemas.inches(body.around(), "repair width");
            Reference page = Reference.parse(body.page());
            Schemes.checkCutOut(finding, along, around);
            RepairScheme scheme = Schemes.selectScheme(
                    along, around, placement, page, Schemes.fastenerOrStandard(body.fastenerDash()), body.temporary());
            RepairId reference = new RepairId(placement.chapter().value(), body.sequence());
            Length centreAlong = Schemas.inches(body.centreAlong(), "offset");
            Length centreAround = Schemas.inches(body.centreAround(), "offset");
            RepairRecord record = new RepairRecord(
                    reference,
                    placement.part(),
                    against,
                    scheme,
                    new Offset(centreAlong, centreAround),
                    body.embodied(),
                    tail.flown(),
                    Optional.empty());
            Embodiment answer = Repairs.embody(unit, record, finding.serial(), body.by(), body.embodied());
            return new EmbodiedOut(
                    repairOut(record),
                    answer.state().value(),
                    answer.deferralLifted(),
                    answer.clashes().stream()
                            .map(pair ->
                                    List.of(pair.one().toString(), pair.other().toString()))
                            .toList());
        });
    }

    @GetMapping("/repairs/aircraft/{serial}")
    @Operation(summary = "Every repair on one aeroplane, still fitted or ever fitted.")
    Page<RepairOut> onTail(
            @PathVariable("serial") String serial,
            @RequestParam(name = "fitted_only", defaultValue = "true") boolean fittedOnly,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "limit", required = false) Integer limit) {
        Window asked = Paging.window(offset, limit);
        return deps.apply(unit -> Paging.paged(
                unit.repairs().onTail(Dependencies.tailOn(unit, serial), fittedOnly), asked, RepairRoutes::repairOut));
    }

    @GetMapping("/repairs/{chapter}/{sequence}")
    @Operation(summary = "One repair, with its whole drawing.")
    RepairOut readRepair(@PathVariable("chapter") int chapter, @PathVariable("sequence") int sequence) {
        return deps.apply(unit -> repairOut(unit.repairs().get(new RepairId(chapter, sequence))));
    }

    @PostMapping("/repairs/{chapter}/{sequence}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Requires(Action.DISPOSITION)
    @Operation(summary = "Take a repair off, which does not reopen the card it answered.")
    void takeOff(
            @PathVariable("chapter") int chapter,
            @PathVariable("sequence") int sequence,
            @Valid @RequestBody RemoveIn body) {
        deps.run(unit -> Repairs.remove(unit, new RepairId(chapter, sequence), body.on()));
    }

    /** The drawing as it goes back, in inches. */
    static DoublerOut doublerOut(RepairRecord record) {
        DoublerPlan plan = record.scheme().doubler();
        return new DoublerOut(
                plan.material().toString(),
                Decimals.text(plan.gauge().inches()),
                Decimals.text(plan.along().inches()),
                Decimals.text(plan.around().inches()),
                plan.size().dash(),
                plan.fastenerCount(),
                plan.rowsEachSide(),
                plan.perRow(),
                Decimals.text(plan.pitch().inches()),
                Decimals.text(plan.rowSpacing().inches()),
                Decimals.text(plan.edgeDistance().inches()));
    }

    /** One repair as it goes back, with its whole drawing. */
    static RepairOut repairOut(RepairRecord record) {
        RepairScheme scheme = record.scheme();
        return new RepairOut(
                record.reference().toString(),
                record.part().toString(),
                record.against().toString(),
                scheme.form().value(),
                scheme.category().value(),
                scheme.sealed(),
                scheme.reference().toString(),
                scheme.filler().map(one -> Decimals.text(one.inches())),
                Decimals.text(record.centre().along().inches()),
                Decimals.text(record.centre().around().inches()),
                record.embodied(),
                record.removed(),
                record.needsInspection(),
                doublerOut(record));
    }
}
