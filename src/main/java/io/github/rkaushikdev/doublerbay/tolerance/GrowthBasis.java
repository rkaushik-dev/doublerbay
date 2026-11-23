package io.github.rkaushikdev.doublerbay.tolerance;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * What a crack does in one kind of structure: how long it can get before the structure stops carrying
 * its load, and how fast it grows on the way there.
 */
public record GrowthBasis(Length critical, Length growthPerThousand) {

    /** A basis with a critical length and a crack that grows. */
    public GrowthBasis {
        Objects.requireNonNull(critical, "critical");
        Objects.requireNonNull(growthPerThousand, "growthPerThousand");
        if (critical.mils() <= 0) {
            throw new BadReading("a critical crack length is a length");
        }
        if (growthPerThousand.mils() <= 0) {
            throw new BadReading("a crack that does not grow needs no inspection");
        }
    }

    /** What the analysis says a crack here grows, in inches per thousand cycles. */
    public BigDecimal inchesPerThousand() {
        return growthPerThousand.inches();
    }

    /**
     * Flying between a crack being findable and being critical.
     *
     * @throws BadReading when a crack that size is already at or past critical
     */
    public Cycles growthCycles(Length fromSize) {
        if (fromSize.isAtLeast(critical)) {
            throw new BadReading(fromSize.inches().toPlainString() + " is already at or past the critical "
                    + critical.inches().toPlainString());
        }
        Length run = critical.minus(fromSize);
        BigDecimal cycles = Decimals.times(
                Decimals.over(BigDecimal.valueOf(run.mils()), BigDecimal.valueOf(growthPerThousand.mils())), 1000);
        return new Cycles(Decimals.toInt(cycles));
    }

    /** {@code critical 6.000, 0.100 per 1000 FC}. */
    @Override
    public String toString() {
        return "critical " + critical.inches().toPlainString() + ", "
                + growthPerThousand.inches().toPlainString() + " per 1000 FC";
    }
}
