package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Opening;
import io.github.rkaushikdev.doublerbay.grid.JointMember;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.FastenerRow;
import io.github.rkaushikdev.doublerbay.structure.Margin;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.Region;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Choosing a repair, and the category it lands in.
 *
 * <p>The category is decided by where the repair is rather than how well it is made, and the form by
 * what the skin there will put up with: an external patch stands proud of the skin, which the
 * aerodynamicists will not have forward of the wing on the upper surfaces, so those go flush and take
 * twice as long. A repair through a pressure boundary is sealed.
 */
public final class Schemes {

    /**
     * Panels where a patch standing proud of the skin is not acceptable, because of what it does to the
     * airflow before it reaches the wing.
     */
    public static final Set<PanelKind> FLUSH_ONLY =
            Collections.unmodifiableSet(EnumSet.of(PanelKind.CROWN_SKIN, PanelKind.WING_UPPER, PanelKind.WING_LOWER));

    /** Kinds of panel where a repair carries a repeat inspection whatever it is made of. */
    public static final Set<PanelKind> INSPECTED_KINDS = Collections.unmodifiableSet(EnumSet.of(PanelKind.WING_LOWER));

    /** Regions of a panel where a repair carries a repeat inspection whatever it is made of. */
    public static final Set<Region> INSPECTED_REGIONS =
            Collections.unmodifiableSet(EnumSet.of(Region.FASTENER_ROW, Region.JOINT_BAND, Region.CUTOUT_CORNER));

    /** Rows each side a repair through a pressure boundary carries, so one row can fail and it holds. */
    public static final int PRESSURISED_ROWS = 2;

    /** What the shop reaches for on skin unless the drawing says otherwise. */
    public static final FastenerSize STANDARD_FASTENER = new FastenerSize(5);

    private Schemes() {}

    /** External unless the panel will not have something standing proud of it. */
    public static RepairForm formFor(Placement placement) {
        return FLUSH_ONLY.contains(placement.panelKind()) ? RepairForm.FLUSH : RepairForm.EXTERNAL;
    }

    /** A unless something about the place says otherwise. */
    public static RepairCategory categoryFor(Placement placement) {
        return categoryFor(placement, false);
    }

    /** A unless something about the place says otherwise, C if it is a stopgap. */
    public static RepairCategory categoryFor(Placement placement, boolean temporary) {
        if (temporary) {
            return RepairCategory.C;
        }
        if (INSPECTED_KINDS.contains(placement.panelKind())) {
            return RepairCategory.B;
        }
        if (INSPECTED_REGIONS.contains(placement.region())) {
            return RepairCategory.B;
        }
        return RepairCategory.A;
    }

    /** The fewest rows each side the place will accept, two through a pressure boundary and one elsewhere. */
    public static int leastRowsFor(Placement placement) {
        return placement.panelKind().isPressurised() ? PRESSURISED_ROWS : 1;
    }

    /**
     * The frame or stringer a doubler reaches over, which the drawing has to deal with, if it reaches
     * one.
     *
     * <p>A doubler is flat and a frame or stringer under the skin is not, so a patch that runs over
     * one is either joggled over its flange or picks the member's own fasteners up into its pattern.
     * Either way it is no longer a doubler on skin and the drawing says so. A doubler is centred on
     * the damage, so it reaches the nearest member when half its extent across the member does.
     */
    public static Optional<JointMember> memberUnder(DoublerPlan doubler, Placement placement) {
        if (!placement.reachesTheMember(doubler.along(), doubler.around())) {
            return Optional.empty();
        }
        return placement.member().map(Margin::member);
    }

    /** How far a torn edge is cut back to clean metal before a doubler is sized for the hole. */
    public static final Length TRIM_BACK = Length.ofInches("0.125");

    /** The fastener a drawing names by its dash number, or the standard one when it names none. */
    public static FastenerSize fastenerOrStandard(Integer dash) {
        return dash != null && dash != 0 ? new FastenerSize(dash) : STANDARD_FASTENER;
    }

    /**
     * Refuse a cut out smaller than the opening on the card once its torn edges are trimmed back.
     *
     * <p>A doubler is sized off the cut out, and a cut out drawn to the torn opening leaves the tears
     * in the skin under the patch, every one of them a crack starter. Nothing is checked for a card
     * that is not an opening.
     *
     * @throws Refused when the cut out does not reach clean metal
     */
    public static void checkCutOut(Finding card, Length along, Length around) {
        if (!(card.geometry() instanceof Opening opening)) {
            return;
        }
        Opening clean = opening.needsTrimmingBeforeRepair() ? opening.trimmed(TRIM_BACK) : opening;
        if (along.isLessThan(clean.along()) || around.isLessThan(clean.around())) {
            throw new Refused(
                    "cut out too small",
                    "the cut out has to reach clean metal, at least "
                            + clean.along().inches().toPlainString() + " by "
                            + clean.around().inches().toPlainString());
        }
    }

    /**
     * The oversize a worked fastener hole in a row can take instead of a doubler, if it can take one.
     *
     * <p>The row the hole is in says which fastener was fitted and how far the hole sits from the
     * sheet edge. Nothing is offered for a card that is not a worked fastener hole in a riveted row.
     */
    public static Optional<Oversize> oversizeFor(Finding card, Placement placement) {
        if (!(card.geometry() instanceof Opening opening)
                || !opening.wasAFastenerHole()
                || placement.joint().isEmpty()
                || placement.joint().get().rows().isEmpty()) {
            return Optional.empty();
        }
        FastenerRow row = placement.joint().get().rowNearest(placement.edgeMargin());
        return Oversizes.oversizeFor(opening, row.size(), row.offset());
    }

    /** A repair through a pressure boundary has to hold pressure when it is done. */
    public static boolean needsSealing(Placement placement) {
        return placement.panelKind().isPressurised();
    }

    /** The permanent repair for a hole of a given size at a given place, on the standard fastener. */
    public static RepairScheme selectScheme(Length along, Length around, Placement placement, Reference reference) {
        return selectScheme(along, around, placement, reference, STANDARD_FASTENER, false);
    }

    /** The repair for a hole at a place on the standard fastener, a stopgap if asked. */
    public static RepairScheme selectScheme(
            Length along, Length around, Placement placement, Reference reference, boolean temporary) {
        return selectScheme(along, around, placement, reference, STANDARD_FASTENER, temporary);
    }

    /** The permanent repair for a hole of a given size at a given place, on a fastener of a size. */
    public static RepairScheme selectScheme(
            Length along, Length around, Placement placement, Reference reference, FastenerSize size) {
        return selectScheme(along, around, placement, reference, size, false);
    }

    /**
     * Work out the repair for a hole of a given size at a given place.
     *
     * <p>A flush repair takes the skin thickness at the damage as its filler. A null size is the
     * standard fastener.
     */
    public static RepairScheme selectScheme(
            Length along,
            Length around,
            Placement placement,
            Reference reference,
            FastenerSize size,
            boolean temporary) {
        FastenerSize fastener = size != null ? size : STANDARD_FASTENER;
        RepairForm form = formFor(placement);
        DoublerPlan doubler = Doublers.sizeDoubler(
                along, around, placement.thickness(), placement.material(), fastener, leastRowsFor(placement));
        return RepairScheme.of(
                form,
                categoryFor(placement, temporary),
                doubler,
                needsSealing(placement),
                reference,
                form == RepairForm.FLUSH ? placement.thickness() : null);
    }
}
