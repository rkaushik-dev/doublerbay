package io.github.rkaushikdev.doublerbay.api;

import java.util.List;
import java.util.Optional;

/**
 * How fast one crack has actually grown, as it goes back.
 *
 * @param reference the card
 * @param readings how many times it was measured
 * @param total how much it grew from the first reading to the latest
 * @param hasGrown whether it grew at all
 * @param isATrend whether there are enough readings to call it a trend
 * @param worstRate the fastest measured stretch, when one is long enough to measure
 * @param fast whether that is fast enough for somebody to look at the programme
 * @param basis what the analysis says a crack grows where this one is, when there is data for it
 * @param outgrowsTheBasis whether the worst measured stretch is quicker than that
 * @param steps every stretch between two readings, oldest first
 */
public record GrowthOut(
        String reference,
        int readings,
        String total,
        boolean hasGrown,
        boolean isATrend,
        Optional<String> worstRate,
        boolean fast,
        Optional<String> basis,
        boolean outgrowsTheBasis,
        List<StepOut> steps) {

    public GrowthOut {
        steps = List.copyOf(steps);
    }
}
