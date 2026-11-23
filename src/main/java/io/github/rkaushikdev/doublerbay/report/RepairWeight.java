package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * What the repairs fitted to one tail weigh between them, against the point the tail is weighed again.
 *
 * <p>One doubler is a few ounces and nobody amends a weight and balance report for it. Forty of them
 * over fifteen years are not, and they sit where the arm is long, in the tail cone and round the aft
 * door. The desk keeps the running total so the weighing is planned before the basic empty weight is
 * out by more than the schedule allows, rather than found at the next weighing.
 *
 * <p>Only fitted repairs count. A doubler taken off took its weight with it.
 */
public record RepairWeight(int fitted, BigDecimal pounds) {

    /** Fitted repairs adding up to this many pounds call for the tail to be weighed again. */
    public static final BigDecimal WEIGH_AGAIN_AT = new BigDecimal("10.000");

    /** A count of repairs and what they weigh. */
    public RepairWeight {
        Objects.requireNonNull(pounds, "pounds");
        if (fitted < 0 || pounds.signum() < 0) {
            throw new BadReading("a repair weight counts what is there");
        }
    }

    /** Add up the plates of the repairs still fitted, out of a tail's repair register. */
    public static RepairWeight of(List<RepairRecord> repairs) {
        List<RepairRecord> on = repairs.stream().filter(RepairRecord::isFitted).toList();
        BigDecimal total =
                on.stream().map(one -> one.scheme().doubler().weight()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RepairWeight(on.size(), Decimals.quantize(total, 3));
    }

    /** Whether the repairs add up to enough that the tail is weighed again. */
    public boolean needsWeighing() {
        return pounds.compareTo(WEIGH_AGAIN_AT) >= 0;
    }

    /** {@code 3 fitted repairs weigh 1.234 lb}, with {@code , weigh the tail again} once it is due. */
    @Override
    public String toString() {
        String head = fitted + " fitted repairs weigh " + pounds.toPlainString() + " lb";
        return needsWeighing() ? head + ", weigh the tail again" : head;
    }
}
