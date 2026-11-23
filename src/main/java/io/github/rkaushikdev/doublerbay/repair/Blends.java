package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.manual.CleanupRule;
import io.github.rkaushikdev.doublerbay.manual.LimitSet;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import java.util.Optional;

/**
 * The blend out, written as an instruction somebody can take to the aeroplane.
 *
 * <p>The plan is worked out from the damage, the place it sits and the cleanup rule for its kind, and
 * then held against the page's limits to say whether grinding it out actually gets the damage inside
 * the book.
 */
public final class Blends {

    private Blends() {}

    /**
     * Work out the cleanup for one damage, or refuse to.
     *
     * @throws Refused when the damage has no depth to blend, a blend is not allowed where it sits, or
     *     the cut would go through the skin
     */
    public static BlendPlan planBlend(Finding finding, Placement placement, CleanupRule cleanup, LimitSet limits) {
        Optional<Length> depth = finding.geometry().limitDepth();
        if (depth.isEmpty()) {
            throw new Refused("nothing to blend", "a " + finding.kind() + " is not measured with a depth");
        }
        if (!cleanup.allowedIn(placement.region())) {
            throw new Refused("no blend here", "a blend is not allowed " + placement.region());
        }

        Length cut = cleanup.cutDepth(depth.get());
        Length residual = placement.thickness().minus(cut);
        if (residual.mils() <= 0) {
            throw new Refused(
                    "nothing left",
                    "a " + cut.inches().toPlainString() + " cut goes through "
                            + placement.thickness().inches().toPlainString());
        }

        Footprint covered = finding.footprint(placement.centre());
        Length clad = placement.material().cladThickness(placement.thickness());
        boolean breaches = cut.isGreaterThan(clad);

        return new BlendPlan(
                cut,
                cleanup.blendExtent(covered.along(), cut),
                cleanup.blendExtent(covered.around(), cut),
                cleanup.taper(),
                residual,
                breaches,
                breaches && cleanup.finishRequiredThroughClad(),
                cleanup.minEndRadius());
    }

    /** Whether grinding it out actually gets the damage inside the book. */
    public static boolean blendWouldClear(Finding finding, Placement placement, CleanupRule cleanup, LimitSet limits) {
        BlendPlan plan;
        try {
            plan = planBlend(finding, placement, cleanup, limits);
        } catch (Refused refused) {
            return false;
        }
        if (!plan.leavesEnough(limits, placement.thickness())) {
            return false;
        }
        if (!plan.withinArea(limits)) {
            return false;
        }
        return cleanup.withinPanelShare(plan.longerSide(), placement.panelExtent());
    }
}
