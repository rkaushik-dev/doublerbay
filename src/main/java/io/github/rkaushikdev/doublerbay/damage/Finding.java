package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.fleet.DiscoverySource;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Point;
import io.github.rkaushikdev.doublerbay.grid.WingPoint;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The damage report itself, which is the card the whole desk works off.
 *
 * <p>A finding is a number, a tail, a kind, the measurements that kind needs, where it is, when it
 * was found and by whom, and what the counters read at the time. The last of those is the one people
 * leave off, and without it the repeat inspection has nothing to count from.
 *
 * <p>The kind and the measurements have to agree. A dent card carrying a crack length is a form
 * filled in wrong, and it is caught here rather than three modules later where the error reads as a
 * missing limit table. The same goes for the cause: a corrosion finding raised off a ground handling
 * report is either the wrong kind or the wrong report, and either way somebody should look at it
 * before an engineer does.
 *
 * <p>A finding is a value. Re measuring it or superseding it hands back a new revision and leaves the
 * one it was asked of as it was.
 */
public record Finding(
        FindingId reference,
        Serial serial,
        DamageKind kind,
        Geometry geometry,
        Point point,
        LocalDate foundOn,
        DiscoverySource source,
        DamageOrigin origin,
        Usage atUsage,
        int revision,
        Optional<FindingId> supersededBy,
        String note) {

    /**
     * Which measurement set each kind is reported with. One entry per kind, and the absence of an
     * entry is a kind nobody has written a form for yet.
     */
    private static final Map<DamageKind, Class<? extends Geometry>> GEOMETRY_FOR = geometryFor();

    /** A card whose measurements fit its kind, whose cause fits both, and which starts at revision one. */
    public Finding {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(foundOn, "foundOn");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(atUsage, "atUsage");
        Objects.requireNonNull(supersededBy, "supersededBy");
        Objects.requireNonNull(note, "note");
        Class<? extends Geometry> expected = GEOMETRY_FOR.get(kind);
        if (expected == null) {
            throw new BadReading("there is no report form for a " + kind);
        }
        if (!expected.isInstance(geometry)) {
            throw new BadReading(
                    "a " + kind + " is reported as " + Text.lower(expected.getSimpleName()) + " measurements");
        }
        DamageKind.checkPairing(kind, origin);
        if (source.carriesAnEvent() && !origin.isAnEvent()) {
            throw new BadReading("a " + source + " names something that happened, and " + origin + " is not");
        }
        if (revision < 1) {
            throw new BadReading("a finding starts at revision one");
        }
    }

    /** A card as first raised: revision one, open, and no note on it. */
    public Finding(
            FindingId reference,
            Serial serial,
            DamageKind kind,
            Geometry geometry,
            Point point,
            LocalDate foundOn,
            DiscoverySource source,
            DamageOrigin origin,
            Usage atUsage) {
        this(reference, serial, kind, geometry, point, foundOn, source, origin, atUsage, 1, Optional.empty(), "");
    }

    /** A card from what the records hold; a null supersession is a card still open. */
    public static Finding of(
            FindingId reference,
            Serial serial,
            DamageKind kind,
            Geometry geometry,
            Point point,
            LocalDate foundOn,
            DiscoverySource source,
            DamageOrigin origin,
            Usage atUsage,
            int revision,
            FindingId supersededByOrNull,
            String note) {
        return new Finding(
                reference,
                serial,
                kind,
                geometry,
                point,
                foundOn,
                source,
                origin,
                atUsage,
                revision,
                Optional.ofNullable(supersededByOrNull),
                note);
    }

    private static Map<DamageKind, Class<? extends Geometry>> geometryFor() {
        Map<DamageKind, Class<? extends Geometry>> forms = new EnumMap<>(DamageKind.class);
        forms.put(DamageKind.DENT, Dent.class);
        forms.put(DamageKind.SCRATCH, Scoring.class);
        forms.put(DamageKind.GOUGE, Scoring.class);
        forms.put(DamageKind.NICK, Scoring.class);
        forms.put(DamageKind.CRACK, Crack.class);
        forms.put(DamageKind.CORROSION, Corrosion.class);
        forms.put(DamageKind.LIGHTNING_BURN, LightningBurn.class);
        forms.put(DamageKind.HOLE, Opening.class);
        forms.put(DamageKind.PUNCTURE, Opening.class);
        forms.put(DamageKind.BUCKLE, Buckle.class);
        forms.put(DamageKind.DISBOND, Disbond.class);
        return Collections.unmodifiableMap(forms);
    }

    /** Whether nothing has superseded this card. */
    public boolean isOpen() {
        return supersededBy.isEmpty();
    }

    /** Whether the kind has no allowable limit at any size. */
    public boolean isNeverAllowable() {
        return kind.isNeverAllowable();
    }

    /** Whether the damage is on a wing rather than the barrel. */
    public boolean isOnTheWing() {
        return point instanceof WingPoint;
    }

    /** The skin the damage covers, whatever kind it is. */
    public Footprint footprint(Offset centre) {
        return geometry.footprint(centre);
    }

    /** The depth the limit tables read, asked of the measurements themselves. */
    public Length governingDepth() {
        return geometry.limitDepth().orElseThrow(() -> new BadReading("a " + kind + " is not measured with a depth"));
    }

    /** The same damage looked at again, which is a new revision of one record. */
    public Finding reMeasured(Geometry measured, LocalDate on, Usage at) {
        if (!GEOMETRY_FOR.get(kind).isInstance(measured)) {
            throw new BadReading("a " + kind + " is not re measured as something else");
        }
        if (on.isBefore(foundOn)) {
            throw new BadReading("a re measurement does not happen before the finding");
        }
        return with(draft -> {
            draft.geometry = measured;
            draft.foundOn = on;
            draft.atUsage = at;
            draft.revision = revision + 1;
        });
    }

    /**
     * The crack after both its tips were stop drilled with a drill of this size, which is a new
     * revision measured one diameter longer, with the drilling written on the card.
     *
     * @throws BadReading for a card that is not a crack, or a crack that cannot be drilled
     */
    public Finding stopDrilled(Length diameter, LocalDate on, Usage at) {
        if (!(geometry instanceof Crack crack)) {
            throw new BadReading("only a crack is stop drilled, and " + reference + " is a " + kind);
        }
        String drilled = "stop drilled " + diameter.inches().toPlainString() + " at both tips";
        return reMeasured(crack.drilled(diameter), on, at).withNote(note.isEmpty() ? drilled : note + "; " + drilled);
    }

    /** Closed out because another record now describes this damage. */
    public Finding superseded(FindingId by) {
        if (by.equals(reference)) {
            throw new BadReading(reference + " cannot supersede itself");
        }
        return withSupersededBy(by);
    }

    /** The same card at another revision number. */
    public Finding withRevision(int replaced) {
        return with(draft -> draft.revision = replaced);
    }

    /** The same card with the counters read at another time. */
    public Finding withAtUsage(Usage replaced) {
        return with(draft -> draft.atUsage = replaced);
    }

    /** The same card, closed by another one, with no check on what closed it. */
    public Finding withSupersededBy(FindingId by) {
        return with(draft -> draft.supersededBy = Optional.of(by));
    }

    /** The same card with another note on it. */
    public Finding withNote(String replaced) {
        return with(draft -> draft.note = replaced);
    }

    /**
     * The same card with something changed. Every copy goes through here, so the card is checked in
     * full on the way out whatever was changed.
     */
    private Finding with(Consumer<Draft> change) {
        Draft draft = new Draft(this);
        change.accept(draft);
        return draft.build();
    }

    /** The parts of a card a new revision or a close out can change, while they are being changed. */
    private static final class Draft {
        private final Finding from;
        private Geometry geometry;
        private LocalDate foundOn;
        private Usage atUsage;
        private int revision;
        private Optional<FindingId> supersededBy;
        private String note;

        private Draft(Finding from) {
            this.from = from;
            geometry = from.geometry;
            foundOn = from.foundOn;
            atUsage = from.atUsage;
            revision = from.revision;
            supersededBy = from.supersededBy;
            note = from.note;
        }

        private Finding build() {
            return new Finding(
                    from.reference,
                    from.serial,
                    from.kind,
                    geometry,
                    from.point,
                    foundOn,
                    from.source,
                    from.origin,
                    atUsage,
                    revision,
                    supersededBy,
                    note);
        }
    }

    /** {@code DR-2025-004182 MSN 5127 dent at FR41 ...}, with {@code rev 2} after the first revision. */
    @Override
    public String toString() {
        String head = reference + " " + serial + " " + kind + " at " + point;
        return revision == 1 ? head : head + " rev " + revision;
    }
}
