package io.github.rkaushikdev.doublerbay.assess;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.grid.Chapter;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.grid.WingPoint;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Airframe;
import io.github.rkaushikdev.doublerbay.structure.FuselagePanel;
import io.github.rkaushikdev.doublerbay.structure.InstalledDoubler;
import io.github.rkaushikdev.doublerbay.structure.Joint;
import io.github.rkaushikdev.doublerbay.structure.Margin;
import io.github.rkaushikdev.doublerbay.structure.Material;
import io.github.rkaushikdev.doublerbay.structure.PanelBuild;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.Region;
import io.github.rkaushikdev.doublerbay.structure.Resolved;
import io.github.rkaushikdev.doublerbay.structure.WingBox;
import io.github.rkaushikdev.doublerbay.structure.WingBuild;
import io.github.rkaushikdev.doublerbay.structure.WingGrid;
import io.github.rkaushikdev.doublerbay.structure.WingMargin;
import io.github.rkaushikdev.doublerbay.structure.WingPanel;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Everything about where a damage is, taken off one resolved point: the six facts every limit is
 * read against.
 *
 * <p>By the time a rule runs it needs to know what kind of skin this is, what part of the panel the
 * damage sits in, how thick the metal is exactly there, how far it is from the nearest edge, what
 * size fastener that edge is if it is a row, and how big the panel is. Not one of those is on the
 * card, and every one of them comes out of a different place. Doing it in one pass is the point:
 * reading the thickness off the panel drawing and the region off the zone chart gets two answers
 * about two different places, and the mismatch is invisible because both look reasonable. Here they
 * come from one resolved point or the call fails.
 *
 * <p>The joint the damage landed on is kept rather than discarded. It was resolved anyway to work
 * out the fastener size, and the rules that only apply in a fastener row need the joint itself. The
 * nearest frame or stringer is kept for the same reason: a damage reaching over one may have reached
 * the member under the skin as well.
 */
public record Placement(
        PanelId part,
        PanelKind panelKind,
        Chapter chapter,
        Material material,
        Region region,
        Length thickness,
        Length edgeMargin,
        Length panelExtent,
        Offset centre,
        Optional<FastenerSize> nearestFastener,
        Optional<Joint> joint,
        Optional<Margin> member) {

    /** A placement on metal that has a thickness, on a panel that has an extent. */
    public Placement {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(panelKind, "panelKind");
        Objects.requireNonNull(chapter, "chapter");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(thickness, "thickness");
        Objects.requireNonNull(edgeMargin, "edgeMargin");
        Objects.requireNonNull(panelExtent, "panelExtent");
        Objects.requireNonNull(centre, "centre");
        Objects.requireNonNull(nearestFastener, "nearestFastener");
        Objects.requireNonNull(joint, "joint");
        Objects.requireNonNull(member, "member");
        if (thickness.mils() <= 0) {
            throw new BadReading(part + ": the skin at the damage has no thickness");
        }
        if (panelExtent.mils() <= 0) {
            throw new BadReading(part + ": the panel has no extent");
        }
    }

    /** A placement away from any fastener row, with no barrel member resolved. */
    public Placement(
            PanelId part,
            PanelKind panelKind,
            Chapter chapter,
            Material material,
            Region region,
            Length thickness,
            Length edgeMargin,
            Length panelExtent,
            Offset centre) {
        this(
                part,
                panelKind,
                chapter,
                material,
                region,
                thickness,
                edgeMargin,
                panelExtent,
                centre,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /**
     * The same placement with one fact changed. Every {@code withX} method goes through here, so a
     * fact added to the placement is added to the copy in one place.
     */
    private Placement with(Consumer<Draft> change) {
        Draft draft = new Draft(this);
        change.accept(draft);
        return draft.build();
    }

    /** The same placement, near a fastener of a size. */
    public Placement withNearestFastener(FastenerSize size) {
        return with(draft -> draft.nearestFastener = Optional.of(size));
    }

    /** The same placement, on a joint. */
    public Placement withJoint(Joint onto) {
        return with(draft -> draft.joint = Optional.of(onto));
    }

    /** The same placement in another region of the panel. */
    public Placement withRegion(Region where) {
        return with(draft -> draft.region = where);
    }

    /** The same placement on metal of another thickness. */
    public Placement withThickness(Length metal) {
        return with(draft -> draft.thickness = metal);
    }

    /** The same placement at another distance from the nearest edge. */
    public Placement withEdgeMargin(Length margin) {
        return with(draft -> draft.edgeMargin = margin);
    }

    /** The same placement, this far off the nearest frame or stringer. */
    public Placement withMember(Margin off) {
        return with(draft -> draft.member = Optional.of(off));
    }

    /**
     * Whether a damage centred here and reaching this far each way along and around crosses the
     * nearest frame or stringer, which it does when half its extent across the member reaches it.
     *
     * <p>A frame runs round the barrel, so it is reached by how far the damage runs along the body. A
     * stringer runs along the body, so it is reached by how far the damage runs round. Nothing is
     * claimed where no member was resolved, which is the wing.
     */
    public boolean reachesTheMember(Length along, Length around) {
        if (member.isEmpty()) {
            return false;
        }
        Length across =
                switch (member.get().member()) {
                    case Frame frame -> along;
                    case Stringer stringer -> around;
                };
        return across.isAtLeast(member.get().distance().times(2));
    }

    /** The facts of one placement while a copy of it is being changed, which the record cannot be. */
    private static final class Draft {
        private final Placement from;
        private Region region;
        private Length thickness;
        private Length edgeMargin;
        private Optional<FastenerSize> nearestFastener;
        private Optional<Joint> joint;
        private Optional<Margin> member;

        private Draft(Placement from) {
            this.from = from;
            region = from.region;
            thickness = from.thickness;
            edgeMargin = from.edgeMargin;
            nearestFastener = from.nearestFastener;
            joint = from.joint;
            member = from.member;
        }

        private Placement build() {
            return new Placement(
                    from.part,
                    from.panelKind,
                    from.chapter,
                    from.material,
                    region,
                    thickness,
                    edgeMargin,
                    from.panelExtent,
                    from.centre,
                    nearestFastener,
                    joint,
                    member);
        }
    }

    /** Resolve a card against the aeroplane it was raised on. */
    public static Placement place(TailContext tail, Finding finding) {
        if (!finding.serial().equals(tail.serial())) {
            throw new BadReading(finding.reference() + " is not a card on " + tail.serial());
        }
        return switch (finding.point()) {
            case FuselagePoint point -> fuselage(tail, point);
            case WingPoint point -> wing(tail, point);
        };
    }

    /** Whether the damage is on the wing rather than the fuselage. */
    public boolean isOnTheWing() {
        return chapter == Chapter.WINGS;
    }

    /** {@code 5310-142L open field, 0.040 thick}. */
    @Override
    public String toString() {
        return part + " " + region + ", " + thickness.inches().toPlainString() + " thick";
    }

    private static Placement fuselage(TailContext tail, FuselagePoint point) {
        Airframe barrel = tail.barrel();
        Resolved resolved = barrel.resolve(point);
        PanelBuild build = resolved.build();
        FuselagePanel panel = build.panel();

        Optional<Joint> joint = build.jointOn(resolved.margin().member());
        Optional<FastenerSize> fastener = joint.filter(found -> !found.rows().isEmpty())
                .map(found -> found.rowNearest(resolved.margin().distance()).size());

        Length along = barrel.grid()
                .stationOf(panel.aft())
                .at()
                .minus(barrel.grid().stationOf(panel.forward()).at());
        Length around =
                barrel.grid().aroundOf(panel.lower()).minus(barrel.grid().aroundOf(panel.upper()));

        return new Placement(
                panel.part(),
                panel.kind(),
                panel.chapter(),
                panel.material(),
                resolved.spot().region(),
                resolved.spot().thickness(),
                resolved.margins().margin(),
                Length.max(along, around),
                barrel.grid().absolute(point),
                fastener,
                joint,
                Optional.of(resolved.margin()));
    }

    private static Placement wing(TailContext tail, WingPoint point) {
        WingBox box = tail.wingBox();
        WingGrid grid = box.gridFor(point.side());
        WingBuild build = box.panelAt(point);
        WingPanel panel = build.panel();

        Offset here = grid.absolute(point);
        WingMargin margin = grid.marginOf(point);
        Length thickness = build.thickness().at(margin.distance());

        Optional<InstalledDoubler> doubler = build.doublerOver(here);
        Region region;
        if (doubler.isPresent()) {
            region = Region.UNDER_DOUBLER;
            thickness = thickness.plus(doubler.get().gauge());
        } else if (build.thickness().onTheStep(margin.distance())) {
            region = Region.CHEM_MILL_STEP;
        } else if (!build.thickness().isFlat()
                && margin.distance().isAtMost(build.thickness().landWidth())) {
            region = Region.LAND;
        } else {
            region = Region.OPEN_FIELD;
        }

        Length span = grid.stationOf(panel.outboard())
                .at()
                .minus(grid.stationOf(panel.inboard()).at());

        return new Placement(
                panel.part(),
                panel.kind(),
                panel.chapter(),
                panel.material(),
                region,
                thickness,
                margin.distance(),
                span,
                here);
    }
}
