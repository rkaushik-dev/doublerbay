package io.github.rkaushikdev.doublerbay.service;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.assess.Assessor;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.store.StoreContext;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionContext;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionEntry;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Taking a card in, which is more than one write and always has been, and what it produced.
 *
 * <p>Raising a damage card writes the card and opens its disposition. A corrosion card is also a
 * corrosion programme finding, and the programme grades it at the moment it is raised, against the
 * history of that panel on that aeroplane.
 *
 * <p>Doing the grading later does not work. The level depends on whether this place has corroded
 * before, and "before" means before this finding, so a batch job run at the end of the month grades
 * six findings against each other in whatever order it happens to read them. The answer has to be
 * computed once, in the unit of work that takes the card.
 *
 * <p>The route and the command line both come through here. Neither of them knows that a corrosion
 * card writes three rows and a dent writes two, and neither of them should.
 *
 * @param reference the card that was taken in
 * @param graded its programme entry, for a corrosion card
 */
public record Intake(FindingId reference, Optional<CorrosionEntry> graded) {

    public Intake {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(graded, "graded");
    }

    /** A card that did not go on the programme. */
    public Intake(FindingId reference) {
        this(reference, Optional.empty());
    }

    /** Whether the card went on the corrosion programme. */
    public boolean wentOnTheProgramme() {
        return graded.isPresent();
    }

    /** Whether the card has to be reported to the authority. */
    public boolean mustBeReported() {
        return graded.map(CorrosionEntry::mustBeReported).orElse(false);
    }

    /** When a reportable finding has to be out, if this one is reportable. */
    public Optional<LocalDate> reportBy() {
        return graded.flatMap(CorrosionEntry::reportBy);
    }

    /** {@code raised DR-2025-000001}, with the level after it for a graded card. */
    @Override
    public String toString() {
        return graded.map(entry -> "raised " + reference + ", " + entry.level()).orElseGet(() -> "raised " + reference);
    }

    /** The aeroplane as it stood on the day the card was raised. */
    public static TailContext tailContext(UnitOfWork unit, TypeCatalogue catalogue, Finding finding) {
        return StoreContext.contextFor(unit, catalogue, finding.serial(), finding.foundOn());
    }

    /**
     * Whether the book lets this stand as it is, which is a level one input.
     *
     * <p>A missing page counts as outside limits. The programme cannot claim it is coping with
     * something it has no allowable for.
     */
    public static boolean withinLimits(Book book, TailContext tail, Finding finding) {
        return Assessor.assess(book, tail, finding).verdict().isAllowable();
    }

    /** Write the card and its disposition, with nothing judged an urgent airworthiness concern. */
    public static Intake takeIn(UnitOfWork unit, Book book, TypeCatalogue catalogue, Finding finding) {
        return takeIn(unit, book, catalogue, finding, false);
    }

    /** Write the card, its disposition, and its programme entry if it has one. */
    public static Intake takeIn(UnitOfWork unit, Book book, TypeCatalogue catalogue, Finding finding, boolean urgent) {
        unit.findings().raiseFinding(finding);
        unit.dispositions().save(DispositionRecord.raised(finding.reference()));
        if (finding.kind() != DamageKind.CORROSION) {
            if (urgent) {
                throw new BadReading("only a corrosion finding is graded as an airworthiness concern");
            }
            return new Intake(finding.reference());
        }

        TailContext tail = tailContext(unit, catalogue, finding);
        Placement placement = Placement.place(tail, finding);
        if (!(finding.geometry() instanceof Corrosion geometry)) {
            throw new BadReading(finding.reference() + " is filed as corrosion without corrosion measurements");
        }
        CorrosionEntry entry = CorrosionEntry.grade(
                geometry,
                new CorrosionContext(
                        placement.part(),
                        withinLimits(book, tail, finding),
                        unit.corrosion().hasFormHere(finding.serial(), placement.part(), finding.foundOn()),
                        urgent,
                        placement.reachesTheMember(geometry.along(), geometry.around())),
                finding.foundOn());
        unit.corrosion().record(entry, finding.serial(), finding.reference());
        return new Intake(finding.reference(), Optional.of(entry));
    }

    /** The next card number for the year a card was found in. */
    public static FindingId nextReference(UnitOfWork unit, LocalDate on) {
        return unit.findings().nextReference(on.getYear());
    }
}
