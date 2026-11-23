package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One line off a material callout, with the numbers the desk needs from it: alloy, temper and
 * cladding, and the one of those that changes a disposition.
 *
 * <p>What actually decides things on a daily basis is the cladding. Clad sheet carries a thin layer
 * of nearly pure aluminium rolled onto each face purely to corrode instead of the alloy underneath.
 * It is five percent of the sheet on each side for thin gauges and less on heavy ones. A blend out
 * that stops inside the cladding is a cosmetic job. The same blend a few thousandths deeper is
 * through into bare alloy and needs a protective finish, a fresh corrosion entry and in some places a
 * repeat inspection. So the cladding thickness is not a material property here, it is a limit that
 * other limits are measured against.
 */
public record Material(String alloy, Temper temper, boolean clad, int ultimateKsi, int yieldKsi) {

    /**
     * Below this gauge the mills clad at five percent a side, above it at two and a half. The step
     * is in the material spec and it is not a rounding of anything.
     */
    public static final Length CLAD_STEP = Length.ofInches("0.062");

    /** The cladding a side on a gauge up to the step. */
    public static final BigDecimal CLAD_THIN = new BigDecimal("0.05");

    /** The cladding a side on a gauge past the step. */
    public static final BigDecimal CLAD_HEAVY = new BigDecimal("0.025");

    /** Every material this desk carries, keyed on the callout the panel drawing writes. */
    public static final Map<String, Material> CATALOGUE = catalogue(List.of(
            new Material("2024", Temper.T3, true, 64, 47),
            new Material("2024", Temper.T3, false, 70, 50),
            new Material("2024", Temper.T351, false, 68, 47),
            new Material("2024", Temper.T42, true, 59, 38),
            new Material("7075", Temper.T6, true, 76, 67),
            new Material("7075", Temper.T6, false, 83, 73),
            new Material("7050", Temper.T7451, false, 76, 67),
            new Material("2024", Temper.ANNEALED, true, 26, 11)));

    /**
     * Bearing strength over ultimate, at the edge distance of one and a half diameters the desk takes
     * for every fastener. The handbook figure at two diameters is higher, and a repair laid out at the
     * preferred two and a half is never read at the lower one, so this is the conservative side.
     */
    public static final BigDecimal BEARING_RATIO = new BigDecimal("1.5");

    /** A material with a four digit alloy that does not yield above its ultimate. */
    public Material {
        Objects.requireNonNull(alloy, "alloy");
        Objects.requireNonNull(temper, "temper");
        if (!alloy.codePoints().allMatch(Character::isDigit) || alloy.codePointCount(0, alloy.length()) != 4) {
            throw new BadReading(Literal.of(alloy) + " is not an alloy designation");
        }
        if (yieldKsi > ultimateKsi) {
            throw new BadReading(callout(alloy, temper, clad) + " yields above its ultimate");
        }
    }

    private static Map<String, Material> catalogue(List<Material> entries) {
        Map<String, Material> held = new LinkedHashMap<>();
        for (Material entry : entries) {
            held.put(entry.toString(), entry);
        }
        return Collections.unmodifiableMap(held);
    }

    private static String callout(String alloy, Temper temper, boolean clad) {
        return alloy + "-" + temper + (clad ? " CLAD" : "");
    }

    /**
     * Look a callout up the way it is written on the panel drawing, regardless of case.
     *
     * @throws NotOnFile when the desk does not carry it
     */
    public static Material lookup(String callout) {
        Material found = CATALOGUE.get(Text.upper(Text.strip(callout)));
        if (found == null) {
            throw new NotOnFile(Literal.of(callout) + " is not a material this desk carries");
        }
        return found;
    }

    /** Whether the desk carries a callout. */
    public static boolean isCarried(String callout) {
        return CATALOGUE.containsKey(Text.upper(Text.strip(callout)));
    }

    /**
     * The alloy series.
     *
     * @throws BadReading when the series is not one this desk carries
     */
    public Family family() {
        char head = alloy.charAt(0);
        if (head == '2') {
            return Family.TWO_THOUSAND;
        }
        if (head == '7') {
            return Family.SEVEN_THOUSAND;
        }
        throw new BadReading(alloy + " is not a series this desk carries");
    }

    /** What the sheet takes in bearing before a fastener hole elongates, in ksi, whole ksi cut down. */
    public int bearingKsi() {
        return Decimals.times(BigDecimal.valueOf(ultimateKsi), BEARING_RATIO).intValue();
    }

    /** Whether the alloy is from the tough series. */
    public boolean isDamageTolerant() {
        return family().isDamageTolerant();
    }

    /** How deep the protective layer runs on one face of a sheet of this gauge. */
    public Length cladThickness(Length nominal) {
        if (!clad) {
            return new Length(0);
        }
        BigDecimal fraction = nominal.isAtMost(CLAD_STEP) ? CLAD_THIN : CLAD_HEAVY;
        return nominal.times(fraction);
    }

    /** The alloy left once both faces of cladding are taken off. */
    public Length bareBelow(Length nominal) {
        return nominal.minus(cladThickness(nominal).times(2));
    }

    /** Whether a cut of that depth has reached bare alloy. */
    public boolean isThroughClad(Length nominal, Length depth) {
        return depth.isGreaterThan(cladThickness(nominal));
    }

    /** {@code 2024-T3 CLAD}, the way the drawing writes it. */
    @Override
    public String toString() {
        return callout(alloy, temper, clad);
    }
}
