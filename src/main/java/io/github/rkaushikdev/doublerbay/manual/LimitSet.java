package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Ratio;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One page's worth of limits, resolved against a thickness when it is read.
 *
 * <p>The numbers on a limit page, and the fact that half of them are not numbers. A limit page
 * rarely says a damage may be five thousandths deep. It says five thousandths or ten percent of the
 * skin thickness, whichever is less, and it says the remaining thickness must not fall below eighty
 * percent, and it says edge margin is at least two diameters. Every one of those depends on something
 * that is not on the page: the thickness where the damage actually is, and the size of the fastener
 * nearest to it.
 *
 * <p>That is why a limit set is a set of rules rather than a row of numbers, and why resolving it
 * takes arguments. The rule for combining an absolute cap with a fractional one is that the tighter
 * of the two wins, and for a floor it is the higher of the two. Getting that backwards clears damage
 * the manual does not.
 *
 * <p>Start from {@link #allowable()} or {@link #nothingAllowable()} and add each limit the page
 * states with its {@code withX} method. Two limit sets are equal when every limit is the same
 * amount, however many places an area or a count of diameters was written to.
 */
public record LimitSet(
        boolean allowed,
        Optional<Length> maxDepth,
        Optional<Ratio> maxDepthFraction,
        Optional<Ratio> hoopLoadedShare,
        Optional<Length> maxLength,
        Optional<BigDecimal> maxArea,
        Optional<Ratio> maxSharpness,
        Optional<Length> minResidual,
        Optional<Ratio> minResidualFraction,
        Optional<Length> minEdgeMargin,
        Optional<BigDecimal> minEdgeMarginDiameters,
        Optional<Taper> blendTaper) {

    /** A limit set that carries numbers only when it allows something, and sensible ones. */
    public LimitSet {
        Objects.requireNonNull(maxDepth, "maxDepth");
        Objects.requireNonNull(maxDepthFraction, "maxDepthFraction");
        Objects.requireNonNull(hoopLoadedShare, "hoopLoadedShare");
        Objects.requireNonNull(maxLength, "maxLength");
        Objects.requireNonNull(maxArea, "maxArea");
        Objects.requireNonNull(maxSharpness, "maxSharpness");
        Objects.requireNonNull(minResidual, "minResidual");
        Objects.requireNonNull(minResidualFraction, "minResidualFraction");
        Objects.requireNonNull(minEdgeMargin, "minEdgeMargin");
        Objects.requireNonNull(minEdgeMarginDiameters, "minEdgeMarginDiameters");
        Objects.requireNonNull(blendTaper, "blendTaper");
        if (!allowed
                && anyPresent(
                        maxDepth,
                        maxDepthFraction,
                        maxLength,
                        maxArea,
                        maxSharpness,
                        minResidual,
                        minResidualFraction)) {
            throw new BadReading("a limit set that allows nothing does not carry limits");
        }
        if (hoopLoadedShare.isPresent()
                && (hoopLoadedShare.get().value().signum() <= 0
                        || hoopLoadedShare.get().value().compareTo(BigDecimal.ONE) > 0)) {
            throw new BadReading("a hoop loaded share is a part of the depth cap");
        }
        if (hoopLoadedShare.isPresent() && maxDepth.isEmpty() && maxDepthFraction.isEmpty()) {
            throw new BadReading("a hoop loaded share needs a depth cap to be a share of");
        }
        if (maxArea.isPresent() && maxArea.get().signum() <= 0) {
            throw new BadReading("an area limit is an area");
        }
        if (minEdgeMarginDiameters.isPresent() && minEdgeMarginDiameters.get().signum() <= 0) {
            throw new BadReading("an edge margin in diameters is a distance");
        }
    }

    /** A page that allows the damage and, until a limit is added, caps nothing. */
    public static LimitSet allowable() {
        return new Draft(true).build();
    }

    /** A page that says this damage is never acceptable as it stands. */
    public static LimitSet nothingAllowable() {
        return new Draft(false).build();
    }

    private static boolean anyPresent(Optional<?>... values) {
        for (Optional<?> value : values) {
            if (value.isPresent()) {
                return true;
            }
        }
        return false;
    }

    private boolean carriesANumber() {
        return anyPresent(
                maxDepth, maxDepthFraction, maxLength, maxArea, maxSharpness, minResidual, minResidualFraction);
    }

    /**
     * The same limits with one of them changed. Every {@code withX} method goes through here, so a
     * limit added to the page is added to the copy in one place.
     */
    private LimitSet with(Consumer<Draft> change) {
        Draft draft = new Draft(this);
        change.accept(draft);
        return draft.build();
    }

    /** The same limits with an absolute depth cap. */
    public LimitSet withMaxDepth(Length depth) {
        return with(draft -> draft.maxDepth = Optional.of(depth));
    }

    /** The same limits with a depth cap written as a share of the thickness at the damage. */
    public LimitSet withMaxDepthFraction(Ratio fraction) {
        return with(draft -> draft.maxDepthFraction = Optional.of(fraction));
    }

    /** The same limits with a share of the depth cap a score lying along the body may use. */
    public LimitSet withHoopLoadedShare(Ratio share) {
        return with(draft -> draft.hoopLoadedShare = Optional.of(share));
    }

    /** The same limits with a length cap. */
    public LimitSet withMaxLength(Length length) {
        return with(draft -> draft.maxLength = Optional.of(length));
    }

    /** The same limits with an area cap, in square inches. */
    public LimitSet withMaxArea(BigDecimal area) {
        return with(draft -> draft.maxArea = Optional.of(area));
    }

    /** The same limits with a cap on how sharp a dent may be. */
    public LimitSet withMaxSharpness(Ratio sharpness) {
        return with(draft -> draft.maxSharpness = Optional.of(sharpness));
    }

    /** The same limits with an absolute floor on the metal left. */
    public LimitSet withMinResidual(Length residual) {
        return with(draft -> draft.minResidual = Optional.of(residual));
    }

    /** The same limits with a floor on the metal left, as a share of the thickness. */
    public LimitSet withMinResidualFraction(Ratio fraction) {
        return with(draft -> draft.minResidualFraction = Optional.of(fraction));
    }

    /** The same limits with an edge margin in inches. */
    public LimitSet withMinEdgeMargin(Length margin) {
        return with(draft -> draft.minEdgeMargin = Optional.of(margin));
    }

    /** The same limits with an edge margin in diameters of the nearest fastener. */
    public LimitSet withMinEdgeMarginDiameters(BigDecimal diameters) {
        return with(draft -> draft.minEdgeMarginDiameters = Optional.of(diameters));
    }

    /** The same limits with the taper a blend has to run out at. */
    public LimitSet withBlendTaper(Taper taper) {
        return with(draft -> draft.blendTaper = Optional.of(taper));
    }

    /** The limits of one set while a copy of it is being changed, which the record cannot be. */
    private static final class Draft {
        private final boolean allowed;
        private Optional<Length> maxDepth = Optional.empty();
        private Optional<Ratio> maxDepthFraction = Optional.empty();
        private Optional<Ratio> hoopLoadedShare = Optional.empty();
        private Optional<Length> maxLength = Optional.empty();
        private Optional<BigDecimal> maxArea = Optional.empty();
        private Optional<Ratio> maxSharpness = Optional.empty();
        private Optional<Length> minResidual = Optional.empty();
        private Optional<Ratio> minResidualFraction = Optional.empty();
        private Optional<Length> minEdgeMargin = Optional.empty();
        private Optional<BigDecimal> minEdgeMarginDiameters = Optional.empty();
        private Optional<Taper> blendTaper = Optional.empty();

        private Draft(boolean allowed) {
            this.allowed = allowed;
        }

        private Draft(LimitSet from) {
            this(from.allowed);
            maxDepth = from.maxDepth;
            maxDepthFraction = from.maxDepthFraction;
            hoopLoadedShare = from.hoopLoadedShare;
            maxLength = from.maxLength;
            maxArea = from.maxArea;
            maxSharpness = from.maxSharpness;
            minResidual = from.minResidual;
            minResidualFraction = from.minResidualFraction;
            minEdgeMargin = from.minEdgeMargin;
            minEdgeMarginDiameters = from.minEdgeMarginDiameters;
            blendTaper = from.blendTaper;
        }

        private LimitSet build() {
            return new LimitSet(
                    allowed,
                    maxDepth,
                    maxDepthFraction,
                    hoopLoadedShare,
                    maxLength,
                    maxArea,
                    maxSharpness,
                    minResidual,
                    minResidualFraction,
                    minEdgeMargin,
                    minEdgeMarginDiameters,
                    blendTaper);
        }
    }

    /** A page that allows something but caps nothing, which the audit looks for. */
    public boolean isOpen() {
        return allowed && !carriesANumber();
    }

    /** The deepest the damage may be, taking the tighter of the two forms. */
    public Optional<Length> depthCap(Length thickness) {
        List<Length> caps = new ArrayList<>();
        maxDepth.ifPresent(caps::add);
        fractionOf(maxDepthFraction, thickness).ifPresent(caps::add);
        return caps.stream().reduce(Length::min);
    }

    /**
     * The deepest a score may be, which for one lying along the body is the share of the cap the page
     * allows it, to the nearest thousandth with a tie going to the even one.
     *
     * <p>Hoop stress in a pressurised barrel is roughly twice the longitudinal stress, and it acts
     * across a score that lies along the body. The page that states a share means the same score at the
     * same depth is fine running round the barrel and too deep running along it.
     */
    public Optional<Length> depthCap(Length thickness, boolean hoopLoaded) {
        Optional<Length> cap = depthCap(thickness);
        if (!hoopLoaded || hoopLoadedShare.isEmpty()) {
            return cap;
        }
        return cap.map(full -> hoopLoadedShare.get().of(full));
    }

    /** The least metal that may be left, taking the higher of the two forms. */
    public Optional<Length> residualFloor(Length thickness) {
        List<Length> floors = new ArrayList<>();
        minResidual.ifPresent(floors::add);
        fractionOf(minResidualFraction, thickness).ifPresent(floors::add);
        return floors.stream().reduce(Length::max);
    }

    /** The least distance to an edge, for a page that writes it in inches if it writes it at all. */
    public Optional<Length> edgeMarginFloor() {
        return edgeMarginFloor(Optional.empty());
    }

    /** The least distance to an edge, in inches or in diameters of the nearest fastener. */
    public Optional<Length> edgeMarginFloor(FastenerSize size) {
        return edgeMarginFloor(Optional.of(size));
    }

    /**
     * The least distance to an edge, in inches or in diameters of the nearest fastener, which may not
     * be known.
     *
     * @throws BadReading when the page writes it in diameters and there is no fastener to read it by
     */
    public Optional<Length> edgeMarginFloor(Optional<FastenerSize> size) {
        List<Length> floors = new ArrayList<>();
        minEdgeMargin.ifPresent(floors::add);
        if (minEdgeMarginDiameters.isPresent()) {
            if (size.isEmpty()) {
                throw new BadReading("an edge margin in diameters needs a fastener size to read it");
            }
            floors.add(size.get().diameters(minEdgeMarginDiameters.get()));
        }
        return floors.stream().reduce(Length::max);
    }

    /** What the depth cap leaves behind, which the residual floor has to agree with. */
    public Optional<Length> impliedResidual(Length thickness) {
        return depthCap(thickness).map(thickness::minus);
    }

    private static Optional<Length> fractionOf(Optional<Ratio> ratio, Length thickness) {
        return ratio.map(share -> share.of(thickness));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LimitSet that
                && allowed == that.allowed
                && maxDepth.equals(that.maxDepth)
                && maxDepthFraction.equals(that.maxDepthFraction)
                && hoopLoadedShare.equals(that.hoopLoadedShare)
                && maxLength.equals(that.maxLength)
                && sameAmount(maxArea, that.maxArea)
                && maxSharpness.equals(that.maxSharpness)
                && minResidual.equals(that.minResidual)
                && minResidualFraction.equals(that.minResidualFraction)
                && minEdgeMargin.equals(that.minEdgeMargin)
                && sameAmount(minEdgeMarginDiameters, that.minEdgeMarginDiameters)
                && blendTaper.equals(that.blendTaper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                allowed,
                maxDepth,
                maxDepthFraction,
                hoopLoadedShare,
                maxLength,
                maxArea.map(BigDecimal::stripTrailingZeros),
                maxSharpness,
                minResidual,
                minResidualFraction,
                minEdgeMargin,
                minEdgeMarginDiameters.map(BigDecimal::stripTrailingZeros),
                blendTaper);
    }

    private static boolean sameAmount(Optional<BigDecimal> one, Optional<BigDecimal> other) {
        if (one.isEmpty() || other.isEmpty()) {
            return one.isEmpty() && other.isEmpty();
        }
        return one.get().compareTo(other.get()) == 0;
    }

    /**
     * {@code depth 0.005, sharpness 2.00%}: the depth caps, length, area, sharpness and residual
     * share, or {@code nothing allowable}, or {@code no limits stated}.
     */
    @Override
    public String toString() {
        if (!allowed) {
            return "nothing allowable";
        }
        List<String> parts = new ArrayList<>();
        maxDepth.ifPresent(depth -> parts.add("depth " + depth.inches().toPlainString()));
        maxDepthFraction.ifPresent(fraction -> parts.add("depth " + fraction));
        hoopLoadedShare.ifPresent(share -> parts.add("along the body " + share + " of that"));
        maxLength.ifPresent(length -> parts.add("length " + length.inches().toPlainString()));
        maxArea.ifPresent(area -> parts.add("area " + Decimals.text(area)));
        maxSharpness.ifPresent(sharpness -> parts.add("sharpness " + sharpness));
        minResidualFraction.ifPresent(fraction -> parts.add("residual " + fraction));
        return parts.isEmpty() ? "no limits stated" : String.join(", ", parts);
    }
}
