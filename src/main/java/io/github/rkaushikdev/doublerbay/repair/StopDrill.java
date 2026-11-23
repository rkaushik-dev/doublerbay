package io.github.rkaushikdev.doublerbay.repair;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import java.util.Objects;

/**
 * Drilling out both tips of a crack so it stops, which is what holds it until the repair goes on.
 *
 * <p>A crack tip is as sharp a stress raiser as there is, and a round hole drilled on it replaces the
 * tip with a radius. The hole is centred on the tip so half of it runs past where the crack ended,
 * which is what catches the part of the crack too fine to see. The crack after drilling is therefore
 * longer than the crack before it by one whole diameter, and that is the length the doubler covers.
 *
 * <p>Each hole is looked at with eddy current once it is drilled, because a tip that was not where the
 * inspector thought it was will show as a crack running out of the far side of the hole.
 * {@link StopDrills#drill} works one out.
 */
public record StopDrill(Length diameter, Length crackLength, Length drilledLength, InspectionMethod checkedWith) {

    /** A drill with a size, on a crack with a length, the drilled length one diameter longer. */
    public StopDrill {
        Objects.requireNonNull(diameter, "diameter");
        Objects.requireNonNull(crackLength, "crackLength");
        Objects.requireNonNull(drilledLength, "drilledLength");
        Objects.requireNonNull(checkedWith, "checkedWith");
        if (diameter.mils() <= 0) {
            throw new BadReading("a stop drill has a size");
        }
        if (!drilledLength.equals(crackLength.plus(diameter))) {
            throw new BadReading("a crack drilled at both tips is one diameter longer than it was");
        }
    }

    /** The two holes. */
    public int holes() {
        return 2;
    }

    /** {@code stop drill 0.098 at both tips, 0.848 drilled, high frequency eddy current round each hole}. */
    @Override
    public String toString() {
        return "stop drill " + diameter.inches().toPlainString() + " at both tips, "
                + drilledLength.inches().toPlainString() + " drilled, " + checkedWith + " round each hole";
    }
}
