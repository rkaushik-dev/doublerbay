package io.github.rkaushikdev.doublerbay.measure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A shank diameter, carried as the dash number it is called out by.
 *
 * <p>Nothing on a repair drawing is dimensioned in inches when it can be dimensioned in D. Edge
 * distance is two D, pitch is four D, row spacing is four D, so the rule survives a change of
 * fastener size. Sizes are dash numbers, which are thirty seconds of an inch: a dash five is 0.156
 * to the nearest thousandth and 0.15625 in truth, and the third place is where that rounding is
 * pinned down once.
 */
public record FastenerSize(int dash) implements Comparable<FastenerSize> {

    /** A dash number counts thirty seconds of an inch. */
    public static final BigDecimal THIRTY_SECONDS = BigDecimal.valueOf(32);

    /**
     * What the shop actually puts through a skin. Anything outside this is a machined fitting and
     * not a repair fastener.
     */
    public static final List<Integer> STOCKED_DASHES = List.of(3, 4, 5, 6, 8, 10, 12);

    /** The least edge distance a fastener may sit at, in D. */
    public static final BigDecimal MIN_EDGE_DISTANCE_D = new BigDecimal("2.0");

    /** The edge distance a drawing aims for, in D. */
    public static final BigDecimal PREFERRED_EDGE_DISTANCE_D = new BigDecimal("2.5");

    /** The closest two fasteners in a row may sit, in D. */
    public static final BigDecimal MIN_PITCH_D = new BigDecimal("4.0");

    /** The closest two rows may sit, in D. */
    public static final BigDecimal MIN_ROW_SPACING_D = new BigDecimal("4.0");

    private static final Pattern DASH = Pattern.compile("-?([0-9]{1,2})");

    /** A size of at least dash one. */
    public FastenerSize {
        if (dash < 1) {
            throw new BadReading("a dash number counts thirty seconds, got " + dash);
        }
    }

    /** A callout with or without its dash: {@code -5}, {@code 5}. */
    public static FastenerSize parse(String text) {
        Matcher match = DASH.matcher(Text.strip(text));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a dash number");
        }
        return new FastenerSize(Integer.parseInt(match.group(1)));
    }

    /** Every stocked size, smallest first. */
    public static List<FastenerSize> stocked() {
        return STOCKED_DASHES.stream().map(FastenerSize::new).toList();
    }

    /** Whether the shop stocks a size. */
    public static boolean isStocked(FastenerSize size) {
        return STOCKED_DASHES.contains(size.dash());
    }

    /**
     * The oversize the shop goes to when a hole has been worked. A hole that has been reamed out
     * or has an elongated bore is not repaired by putting the same fastener back in it.
     */
    public static FastenerSize nextSizeUp(FastenerSize size) {
        return sizesUp(size).stream()
                .findFirst()
                .orElseThrow(() -> new BadReading(size + " is the largest stocked fastener"));
    }

    /** Every stocked size bigger than this one, smallest first, none past the largest. */
    public static List<FastenerSize> sizesUp(FastenerSize size) {
        return stocked().stream().filter(one -> one.compareTo(size) > 0).toList();
    }

    /** The nominal shank, rounded to the thousandth the drawing carries. */
    public Length diameter() {
        BigDecimal exact = Decimals.times(exactDiameter(), Length.MILS_PER_INCH);
        return new Length(Decimals.toInt(exact));
    }

    /** The shank before rounding, for anything that accumulates. */
    public BigDecimal exactDiameter() {
        return Decimals.over(BigDecimal.valueOf(dash), THIRTY_SECONDS);
    }

    /** A distance called out in D, turned into a length. */
    public Length diameters(BigDecimal count) {
        BigDecimal exact = Decimals.times(Decimals.times(exactDiameter(), count), Length.MILS_PER_INCH);
        return new Length(Decimals.toInt(exact));
    }

    /** A distance called out in D, written as decimal text. */
    public Length diameters(String count) {
        return diameters(Decimals.parse(count));
    }

    /** A distance of a whole number of D. */
    public Length diameters(long count) {
        return diameters(BigDecimal.valueOf(count));
    }

    /** A measured distance read back in D, to two places, so it can meet a rule written in D. */
    public BigDecimal inDiameters(Length distance) {
        BigDecimal inches = Decimals.over(BigDecimal.valueOf(distance.mils()), Length.MILS_PER_INCH);
        return Decimals.quantize(Decimals.over(inches, exactDiameter()), 2);
    }

    /** The least edge distance for this size. */
    public Length minEdgeDistance() {
        return diameters(MIN_EDGE_DISTANCE_D);
    }

    /** The edge distance a drawing aims for with this size. */
    public Length preferredEdgeDistance() {
        return diameters(PREFERRED_EDGE_DISTANCE_D);
    }

    /** The closest pitch for this size. */
    public Length minPitch() {
        return diameters(MIN_PITCH_D);
    }

    /** The closest row spacing for this size. */
    public Length minRowSpacing() {
        return diameters(MIN_ROW_SPACING_D);
    }

    @Override
    public int compareTo(FastenerSize other) {
        return Integer.compare(dash, other.dash);
    }

    /** {@code -5}, the way the size is called out. */
    @Override
    public String toString() {
        return "-" + dash;
    }
}
