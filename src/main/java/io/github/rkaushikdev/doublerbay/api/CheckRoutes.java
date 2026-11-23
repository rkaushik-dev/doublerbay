package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.report.PackLine;
import io.github.rkaushikdev.doublerbay.report.WorkPack;
import io.github.rkaushikdev.doublerbay.report.WorkPacks;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The work pack route, which is the one planning actually integrates with.
 *
 * <p>Everything else on this service answers a question about one record. This answers a question
 * about one aeroplane and one week, and it is the only route that reaches across three registers to
 * do it. That is deliberate: the join belongs on this side of the wire, because a planning system
 * that had to make it would make it slightly differently every release.
 *
 * <p>The pack comes back in three lists rather than one, for the same reason the command line prints
 * it in three blocks. The consumer is going to route the inspections to the shop and the deferrals to
 * the engineer, and flattening them means the consumer has to unflatten them.
 */
@RestController
@Tag(name = "checks")
final class CheckRoutes {

    /**
     * The furthest ahead a check will reach for work. A year of pulled forward tasks is not a check,
     * it is a heavy visit somebody has not planned yet.
     */
    static final int MAX_PULL_FORWARD_DAYS = 365;

    private final Deps deps;

    CheckRoutes(Deps deps) {
        this.deps = deps;
    }

    @GetMapping("/checks/{serial}/pack")
    @Operation(summary = "Everything one tail owes the desk between two days.")
    PackOut workPack(
            @PathVariable("serial") String serial,
            @RequestParam(name = "opens") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate opens,
            @RequestParam(name = "closes") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closes,
            @RequestParam(name = "within", required = false) Integer within) {
        OptionalInt reach = checkedPull(within);
        return deps.apply(
                unit -> packOut(WorkPacks.build(unit, Dependencies.tailOn(unit, serial), opens, closes, reach)));
    }

    /** Refuse a reach this desk would not call a check; nothing asked is the check's own length. */
    static OptionalInt checkedPull(Integer within) {
        if (within == null) {
            return OptionalInt.empty();
        }
        if (within < 0) {
            throw new BadReading("a check reaches forward, not back");
        }
        if (within > MAX_PULL_FORWARD_DAYS) {
            throw new BadReading("a check does not pull work in from past " + MAX_PULL_FORWARD_DAYS + " days");
        }
        return OptionalInt.of(within);
    }

    /** One line as it goes back. */
    static PackLineOut lineOut(PackLine line) {
        return new PackLineOut(line.what(), line.reference(), line.detail(), line.overrun());
    }

    /** The whole pack as it goes back, in its three blocks. */
    static PackOut packOut(WorkPack pack) {
        return new PackOut(
                pack.serial().number(),
                pack.opens(),
                pack.closes(),
                pack.days(),
                pack.total(),
                pack.overrun().size(),
                lines(pack.deferrals()),
                lines(pack.inspections()),
                lines(pack.waiting()),
                lines(pack.stripping()),
                WorkPacks.referencesOn(pack));
    }

    private static List<PackLineOut> lines(List<PackLine> block) {
        return block.stream().map(CheckRoutes::lineOut).toList();
    }
}
