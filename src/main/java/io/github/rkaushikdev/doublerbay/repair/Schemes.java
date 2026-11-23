package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.Region;
import java.util.Collections;
import java.util.EnumSet;
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
