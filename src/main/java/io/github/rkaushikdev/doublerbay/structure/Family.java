package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.util.Literal;
import java.math.BigDecimal;

/**
 * The alloy series, which is what the toughness argument turns on.
 *
 * <p>Two thousand series is the tough one and seven thousand series is the strong one, and the
 * manual treats them differently because a crack in 7075 runs further before anybody sees it.
 */
public enum Family {
    TWO_THOUSAND("2xxx", "0.100"),
    SEVEN_THOUSAND("7xxx", "0.101");

    private final String value;
    private final BigDecimal density;

    Family(String value, String density) {
        this.value = value;
        this.density = new BigDecimal(density);
    }

    /** What a cubic inch of the series weighs, in pounds, off the handbook. */
    public BigDecimal density() {
        return density;
    }

    /** The series this designation names. */
    public static Family fromValue(String value) {
        for (Family family : values()) {
            if (family.value.equals(value)) {
                return family;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid Family");
    }

    /** The series designation, {@code 2xxx}. */
    public String value() {
        return value;
    }

    /** Whether the series is the tough one a crack grows slowly in. */
    public boolean isDamageTolerant() {
        return this == TWO_THOUSAND;
    }

    /** {@code 7xxx}. */
    @Override
    public String toString() {
        return value;
    }
}
