package io.github.rkaushikdev.doublerbay.v320;

import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.grid.Chapter;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.manual.LimitKey;
import io.github.rkaushikdev.doublerbay.manual.LimitRow;
import io.github.rkaushikdev.doublerbay.manual.LimitSet;
import io.github.rkaushikdev.doublerbay.manual.LimitTable;
import io.github.rkaushikdev.doublerbay.manual.Manual;
import io.github.rkaushikdev.doublerbay.manual.Page;
import io.github.rkaushikdev.doublerbay.manual.Revision;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Ratio;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.Region;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Chapter 57 of the V320 manual, allowable damage to wing skin.
 *
 * <p>The chapter is laid out like chapter 53 and the numbers are not close to it, for one reason: the
 * lower surface spends the flight in tension. A scratch on the underside of a wing is sitting in the
 * stress field that grows cracks, and the book gives it roughly half of what the same scratch gets on
 * top and a quarter of what it gets on a fuselage panel.
 *
 * <p>The other difference is thickness. Wing plate runs from a quarter of an inch at the root down to
 * a tenth at the tip, so a fractional limit written against the thickness at the damage moves a long
 * way across one panel. The absolute caps are there to stop the fractional ones from becoming
 * generous on the heavy plate inboard.
 */
public final class Manual57 {

    /** The chapter this is. */
    public static final Chapter CHAPTER = Chapter.WINGS;

    /** The allowable damage section the whole table is filed under. */
    public static final Reference ADL = Reference.parse("57-10");

    /** Wing skin dents. */
    public static final Reference DEFORMATION = Reference.parse("57-10-01");

    /** Wing skin material loss. */
    public static final Reference MATERIAL_LOSS = Reference.parse("57-20-01");

    /** Wing skin corrosion, and lightning strike with it. */
    public static final Reference CORROSION_PAGE = Reference.parse("57-30-01");

    /** Wing skin cracks and penetration. */
    public static final Reference PENETRATION = Reference.parse("57-40-01");

    /** Every revision of the chapter on file, oldest first. */
    public static final List<Revision> REVISIONS = List.of(
            new Revision(7, LocalDate.of(2021, 6, 1), LocalDate.of(2021, 9, 1)),
            new Revision(8, LocalDate.of(2022, 10, 1), LocalDate.of(2023, 1, 1)),
            new Revision(9, LocalDate.of(2024, 4, 1), LocalDate.of(2024, 7, 1)),
            new Revision(10, LocalDate.of(2025, 5, 1), LocalDate.of(2025, 8, 1)));

    /** The pages the desk reads, at the revision each came into the book. */
    public static final List<Page> PAGES = List.of(
            new Page(DEFORMATION, "allowable damage, wing skin dents", 7),
            new Page(MATERIAL_LOSS, "allowable damage, wing skin material loss", 7),
            new Page(CORROSION_PAGE, "allowable damage, wing skin corrosion", 7),
            new Page(PENETRATION, "allowable damage, wing skin cracks and penetration", 7));

    /** The edge margin every limited row asks for, in diameters of the nearest fastener. */
    public static final BigDecimal TWO_AND_A_HALF_DIAMETERS = new BigDecimal("2.5");

    /** The taper a blend runs out at on wing skin. */
    public static final Taper BLEND = new Taper(30);

    /**
     * The share of the lower surface depth cap a score lying chordwise may use. The lower skin carries
     * the wing's bending as spanwise tension, and a chordwise score lies straight across it.
     */
    public static final String CHORDWISE_SHARE = "0.50";

    /** Where nothing is allowable whatever it measures. */
    public static final List<Region> NOTHING_ALLOWED_IN =
            List.of(Region.FASTENER_ROW, Region.CUTOUT_CORNER, Region.CHEM_MILL_STEP);

    /**
     * Depth caps and residual floors, by surface, upper first. The lower surface is the tension one
     * and it gets half of what the upper gets.
     */
    public static final Map<PanelKind, SurfaceLimits> SURFACE_LIMITS = surfaceLimits();

    /** How many revisions' tables are kept built at once. */
    private static final int KEPT = 8;

    private static final Map<Integer, LimitTable> BUILT = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, LimitTable> eldest) {
            return size() > KEPT;
        }
    };

    /**
     * One surface's score limits as the page writes them: the depth as a share of the plate, the
     * floor on what is left as a share of the plate, the absolute depth cap in inches, and the share
     * of that cap a score lying chordwise may use where the surface states one.
     */
    public record SurfaceLimits(String share, String residual, String cap, Optional<String> acrossTheLoad) {

        /** The numbers, as decimal text. */
        public SurfaceLimits {
            Objects.requireNonNull(share, "share");
            Objects.requireNonNull(residual, "residual");
            Objects.requireNonNull(cap, "cap");
            Objects.requireNonNull(acrossTheLoad, "acrossTheLoad");
        }

        /** A surface that treats a score the same whichever way it lies. */
        public SurfaceLimits(String share, String residual, String cap) {
            this(share, residual, cap, Optional.empty());
        }

        /** The limits for a score on this surface, held to the share across the load where there is one. */
        LimitSet scoreLimits() {
            LimitSet limits = LimitSet.allowable()
                    .withMaxDepth(inches(cap))
                    .withMaxDepthFraction(new Ratio(share))
                    .withMinResidualFraction(new Ratio(residual))
                    .withMinEdgeMarginDiameters(TWO_AND_A_HALF_DIAMETERS)
                    .withBlendTaper(BLEND);
            return acrossTheLoad
                    .map(text -> limits.withAcrossLoadShare(new Ratio(text)))
                    .orElse(limits);
        }
    }

    private Manual57() {}

    private static Map<PanelKind, SurfaceLimits> surfaceLimits() {
        Map<PanelKind, SurfaceLimits> held = new LinkedHashMap<>();
        held.put(PanelKind.WING_UPPER, new SurfaceLimits("0.05", "0.88", "0.010"));
        held.put(PanelKind.WING_LOWER, new SurfaceLimits("0.025", "0.94", "0.005", Optional.of(CHORDWISE_SHARE)));
        return Collections.unmodifiableMap(held);
    }

    /** The chapter with its revision history. */
    public static Manual manual() {
        return new Manual(CHAPTER, REVISIONS);
    }

    /**
     * The whole allowable damage table for the wing, at one revision.
     *
     * <p>Built once per revision and shared: asking twice for the same revision hands back the same
     * table, for the last eight revisions asked for.
     */
    public static LimitTable allowableDamage(int revision) {
        synchronized (BUILT) {
            return BUILT.computeIfAbsent(revision, Manual57::build);
        }
    }

    private static LimitTable build(int revision) {
        List<LimitRow> rows = new ArrayList<>();
        rows.addAll(dentRows());
        rows.addAll(scoreRows());
        rows.addAll(corrosionRows());
        rows.addAll(penetrationRows());
        return new LimitTable(ADL, revision, rows);
    }

    private static Length inches(String text) {
        return Length.ofInches(text);
    }

    private static List<LimitRow> nothingIn(DamageKind kind, Reference page) {
        return NOTHING_ALLOWED_IN.stream()
                .map(region -> new LimitRow(new LimitKey(kind, region), LimitSet.nothingAllowable(), page))
                .toList();
    }

    private static List<LimitRow> dentRows() {
        List<LimitRow> rows = new ArrayList<>();
        rows.add(new LimitRow(
                new LimitKey(DamageKind.DENT),
                LimitSet.allowable()
                        .withMaxDepth(inches("0.040"))
                        .withMaxSharpness(new Ratio("0.04"))
                        .withMinEdgeMarginDiameters(TWO_AND_A_HALF_DIAMETERS),
                DEFORMATION));
        rows.add(new LimitRow(
                new LimitKey(DamageKind.DENT, PanelKind.WING_LOWER), LimitSet.nothingAllowable(), DEFORMATION));
        rows.addAll(nothingIn(DamageKind.DENT, DEFORMATION));
        return rows;
    }

    private static List<LimitRow> scoreRows() {
        List<LimitRow> rows = new ArrayList<>();
        for (DamageKind kind : List.of(DamageKind.SCRATCH, DamageKind.GOUGE, DamageKind.NICK)) {
            SURFACE_LIMITS.forEach((surface, limits) ->
                    rows.add(new LimitRow(new LimitKey(kind, surface), limits.scoreLimits(), MATERIAL_LOSS)));
            rows.add(new LimitRow(
                    new LimitKey(kind),
                    LimitSet.allowable()
                            .withMaxDepth(inches("0.005"))
                            .withMaxDepthFraction(new Ratio("0.025"))
                            .withMinResidualFraction(new Ratio("0.94"))
                            .withMinEdgeMarginDiameters(TWO_AND_A_HALF_DIAMETERS)
                            .withBlendTaper(BLEND),
                    MATERIAL_LOSS));
            rows.addAll(nothingIn(kind, MATERIAL_LOSS));
        }
        return rows;
    }

    private static List<LimitRow> corrosionRows() {
        List<LimitRow> rows = new ArrayList<>();
        rows.add(new LimitRow(
                new LimitKey(DamageKind.CORROSION, PanelKind.WING_UPPER),
                LimitSet.allowable()
                        .withMaxDepthFraction(new Ratio("0.05"))
                        .withMinResidualFraction(new Ratio("0.88"))
                        .withMaxArea(new BigDecimal("3.00"))
                        .withBlendTaper(BLEND),
                CORROSION_PAGE));
        rows.add(new LimitRow(
                new LimitKey(DamageKind.CORROSION, PanelKind.WING_LOWER),
                LimitSet.allowable()
                        .withMaxDepthFraction(new Ratio("0.025"))
                        .withMinResidualFraction(new Ratio("0.94"))
                        .withMaxArea(new BigDecimal("1.50"))
                        .withBlendTaper(BLEND),
                CORROSION_PAGE));
        rows.add(new LimitRow(
                new LimitKey(DamageKind.CORROSION),
                LimitSet.allowable()
                        .withMaxDepthFraction(new Ratio("0.025"))
                        .withMinResidualFraction(new Ratio("0.94"))
                        .withMaxArea(new BigDecimal("1.50"))
                        .withBlendTaper(BLEND),
                CORROSION_PAGE));
        rows.add(new LimitRow(
                new LimitKey(DamageKind.LIGHTNING_BURN),
                LimitSet.allowable()
                        .withMaxDepth(inches("0.006"))
                        .withMaxArea(new BigDecimal("1.00"))
                        .withBlendTaper(BLEND),
                CORROSION_PAGE));
        for (DamageKind kind : List.of(DamageKind.CORROSION, DamageKind.LIGHTNING_BURN)) {
            rows.addAll(nothingIn(kind, CORROSION_PAGE));
        }
        return rows;
    }

    private static List<LimitRow> penetrationRows() {
        return List.of(DamageKind.CRACK, DamageKind.HOLE, DamageKind.PUNCTURE, DamageKind.BUCKLE, DamageKind.DISBOND)
                .stream()
                .map(kind -> new LimitRow(new LimitKey(kind), LimitSet.nothingAllowable(), PENETRATION))
                .toList();
    }
}
