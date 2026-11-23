package io.github.rkaushikdev.doublerbay.tolerance;

import io.github.rkaushikdev.doublerbay.ids.PanelId;
import java.util.Objects;

/**
 * What the programme knows about this place, which the metal does not say.
 *
 * <p>Widespread is about the structure rather than the size of the patch: corrosion that has run
 * past a frame or a stringer onto the next element is on two structural elements at once, which the
 * programme reads differently from the same area on one.
 */
public record CorrosionContext(
        PanelId part,
        boolean withinLimits,
        boolean foundHereBefore,
        boolean urgentAirworthinessConcern,
        boolean widespread) {

    /** A panel and what the programme knows about it. */
    public CorrosionContext {
        Objects.requireNonNull(part, "part");
    }

    /** A panel and what the programme knows about it, corrosion on one element only. */
    public CorrosionContext(
            PanelId part, boolean withinLimits, boolean foundHereBefore, boolean urgentAirworthinessConcern) {
        this(part, withinLimits, foundHereBefore, urgentAirworthinessConcern, false);
    }

    /** A panel and what the programme knows about it, with no urgent airworthiness concern raised. */
    public CorrosionContext(PanelId part, boolean withinLimits, boolean foundHereBefore) {
        this(part, withinLimits, foundHereBefore, false, false);
    }

    /**
     * {@code 5310-143L, first time here} or {@code 5310-143L, seen here before}, with {@code ,
     * widespread} after it when the corrosion runs onto the next element.
     */
    @Override
    public String toString() {
        String history = foundHereBefore ? "seen here before" : "first time here";
        return part + ", " + history + (widespread ? ", widespread" : "");
    }
}
