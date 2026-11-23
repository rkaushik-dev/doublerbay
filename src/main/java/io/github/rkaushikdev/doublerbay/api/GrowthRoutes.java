package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.report.Growth;
import io.github.rkaushikdev.doublerbay.report.Step;
import io.github.rkaushikdev.doublerbay.service.Repairs;
import io.github.rkaushikdev.doublerbay.store.StoreContext;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.tolerance.GrowthBasis;
import io.github.rkaushikdev.doublerbay.tolerance.Schedules;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The growth routes, which answer how fast a crack actually moved.
 *
 * <p>One card at a time and a fleet wide list of the fast ones. The fleet list is the one that
 * matters: a crack growing faster than the analysis said it would is the finding that changes an
 * inspection programme, and nobody goes looking for it card by card.
 *
 * <p>Only cracks come back. Everything else on this fleet is dispositioned on the measurement it has
 * now rather than on a trend, and offering a growth number for a dent would invite somebody to plan
 * against it.
 */
@RestController
@Tag(name = "growth")
final class GrowthRoutes {

    /** Above this, in inches per thousand cycles, somebody looks at the programme. */
    static final BigDecimal FAST = new BigDecimal("0.05");

    /** What a crack with no rate, or a rate of nothing, sorts as on a tail's list. */
    private static final BigDecimal NO_RATE = BigDecimal.valueOf(-1);

    private final Deps deps;

    GrowthRoutes(Deps deps) {
        this.deps = deps;
    }

    @GetMapping("/growth/{reference}")
    @Operation(summary = "How fast one crack has actually grown.")
    GrowthOut cardGrowth(@PathVariable("reference") String reference) {
        return deps.apply(unit -> growthOut(unit, growthFor(unit, Dependencies.referenceFrom(reference))));
    }

    @GetMapping("/growth")
    @Operation(
            summary = "Every crack on the fleet growing faster than the programme expects.",
            description = "A crack is listed when its worst measured rate is past the desk's fast line, or past"
                    + " the analysis for the skin it is in, which on the tail cone is the slower of the two.")
    FleetGrowthOut fleetGrowth(@RequestParam(name = "on") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        Tracked found = deps.apply(unit -> {
            List<Finding> tracked = unit.findings().ofKind(DamageKind.CRACK);
            List<Rated> rated = new ArrayList<>();
            for (Finding one : tracked) {
                Growth built;
                try {
                    built = growthFor(unit, one.reference());
                } catch (DoublerbayError untracked) {
                    continue;
                }
                Optional<BigDecimal> worst = built.worstRate();
                if (worst.isEmpty()) {
                    continue;
                }
                GrowthOut out = growthOut(unit, built);
                if (out.fast() || out.outgrowsTheBasis()) {
                    rated.add(new Rated(out, worst.get()));
                }
            }
            return new Tracked(tracked.size(), rated);
        });
        List<Rated> rated = new ArrayList<>(found.fast());
        rated.sort(Comparator.comparing(Rated::rate, Comparator.reverseOrder())
                .thenComparing(one -> one.growth().reference()));
        return new FleetGrowthOut(
                on, found.count(), rated.stream().map(Rated::growth).toList());
    }

    @GetMapping("/growth/aircraft/{serial}")
    @Operation(summary = "Every tracked crack on one aeroplane, fastest first.")
    List<GrowthOut> tailGrowth(@PathVariable("serial") String serial) {
        return deps.apply(unit -> {
            Serial held = Dependencies.tailOn(unit, serial);
            List<Growth> cracks = new ArrayList<>();
            for (Finding one : unit.findings().onTail(held)) {
                if (one.kind() == DamageKind.CRACK) {
                    cracks.add(growthFor(unit, one.reference()));
                }
            }
            cracks.sort(Comparator.comparing(GrowthRoutes::sortingRate, Comparator.reverseOrder())
                    .thenComparing(one -> one.reference().toString()));
            return cracks.stream().map(one -> growthOut(unit, one)).toList();
        });
    }

    /**
     * One card's growth off its revisions.
     *
     * <p>The kind is checked here rather than left to the first step. A card with one reading has no
     * steps, so a dent would come back as a perfectly valid growth of nothing, and a client would take
     * that as an answer.
     */
    static Growth growthFor(UnitOfWork unit, FindingId reference) {
        List<Finding> revisions = unit.findings().history(reference);
        if (revisions.isEmpty()) {
            throw new BadReading(reference + " has no revisions on file");
        }
        DamageKind kind = revisions.get(0).kind();
        if (kind != DamageKind.CRACK) {
            throw new BadReading(reference + " is a " + kind + ", which is not tracked for growth");
        }
        return Growth.of(revisions);
    }

    /** One stretch as it goes back, its rate printed with the sign of a rate too small to show. */
    static StepOut stepOut(Step step) {
        return new StepOut(
                Decimals.text(step.was().inches()),
                Decimals.text(step.now().inches()),
                Decimals.text(step.grewBy().inches()),
                step.cycles(),
                step.isMeasurable(),
                step.isMeasurable() ? Optional.of(step.rateText()) : Optional.empty());
    }

    /**
     * What the analysis says a crack grows in the skin under this card, read off where the card is
     * on the day it was last measured, and nothing for a kind of skin with no growth data.
     */
    Optional<GrowthBasis> basisUnder(UnitOfWork unit, FindingId reference) {
        Finding latest = unit.findings().latest(reference);
        TailContext tail = StoreContext.contextFor(unit, deps.catalogue(), latest.serial(), latest.foundOn());
        PanelKind kind = Placement.place(tail, latest).panelKind();
        return Optional.ofNullable(Schedules.BASIS.get(kind));
    }

    /** One crack's growth as it goes back, held against the analysis for the skin it is in. */
    GrowthOut growthOut(UnitOfWork unit, Growth built) {
        Optional<BigDecimal> worst = built.worstRate();
        Optional<GrowthBasis> basis = basisUnder(unit, built.reference());
        Optional<GrowthBasis> outgrown = basis.filter(built::outgrows);
        Optional<String> repeat = outgrown.flatMap(
                found -> Schedules.repeatFor(found.atMeasuredRate(worst.orElseThrow()), Repairs.DEFAULT_METHOD)
                        .map(cycles -> "every " + cycles));
        return new GrowthOut(
                built.reference().toString(),
                built.readings(),
                Decimals.text(built.total().inches()),
                built.hasGrown(),
                built.isATrend(),
                built.worstRateText(),
                worst.isPresent() && worst.get().compareTo(FAST) > 0,
                basis.map(GrowthBasis::toString),
                outgrown.isPresent(),
                repeat,
                built.steps().stream().map(GrowthRoutes::stepOut).toList());
    }

    /** The rate a tail's list sorts on: the worst rate, with none and a rate of nothing alike below every other. */
    private static BigDecimal sortingRate(Growth built) {
        return built.worstRate().filter(rate -> rate.signum() != 0).orElse(NO_RATE);
    }

    /** A crack as it goes back and its worst rate, kept together while the list is sorted. */
    private record Rated(GrowthOut growth, BigDecimal rate) {}

    /** How many cracks the fleet carries, and the fast ones among them. */
    private record Tracked(int count, List<Rated> fast) {}
}
