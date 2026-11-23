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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chapter 53 of the V320 manual, allowable damage to fuselage skin.
 *
 * <p>Four subjects. Deformation at 53-10, material loss at 53-20, corrosion and lightning at 53-30,
 * and cracking and penetration at 53-40, which is the short one because the answer there is always a
 * repair.
 *
 * <p>The numbers tighten as you move inboard on the structure. Unpressurised tail cone skin gets the
 * loosest limits, ordinary pressurised barrel skin is half that, and anything sitting on a fastener
 * row or in a cutout corner has no allowable limit at all whatever it measures.
 *
 * <p>Revision 12 tightened the pressurised skin dent limits after a fleet campaign, which is why the
 * table is built per revision rather than once. Anything dispositioned before that revision came into
 * force was assessed against the looser numbers and stays that way.
 */
public final class Manual53 {

    /** The chapter this is. */
    public static final Chapter CHAPTER = Chapter.FUSELAGE;

    /** The allowable damage section the whole table is filed under. */
    public static final Reference ADL = Reference.parse("53-10");

    /** Dents and buckles. */
    public static final Reference DEFORMATION = Reference.parse("53-10-01");

    /** Scratches, gouges and nicks. */
    public static final Reference MATERIAL_LOSS = Reference.parse("53-20-01");

    /** Corrosion. */
    public static final Reference CORROSION_PAGE = Reference.parse("53-30-01");

    /** Lightning strike. */
    public static final Reference LIGHTNING_PAGE = Reference.parse("53-30-02");

    /** Cracks and penetration. */
    public static final Reference PENETRATION = Reference.parse("53-40-01");

    /** The revision that tightened pressurised skin dents. */
    public static final int DENT_CAMPAIGN_REVISION = 12;

    /** Every revision of the chapter on file, oldest first. */
    public static final List<Revision> REVISIONS = List.of(
            new Revision(9, LocalDate.of(2021, 11, 1), LocalDate.of(2022, 2, 1)),
            new Revision(10, LocalDate.of(2023, 3, 1), LocalDate.of(2023, 6, 1)),
            new Revision(11, LocalDate.of(2023, 12, 1), LocalDate.of(2024, 3, 1)),
            new Revision(12, LocalDate.of(2024, 9, 1), LocalDate.of(2024, 12, 1)),
            new Revision(13, LocalDate.of(2025, 8, 1), LocalDate.of(2025, 11, 1)));

    /** The pages the desk reads, at the revision each came into the book. */
    public static final List<Page> PAGES = List.of(
            new Page(DEFORMATION, "allowable damage, dents and buckles", 9),
            new Page(MATERIAL_LOSS, "allowable damage, scratches gouges and nicks", 9),
            new Page(CORROSION_PAGE, "allowable damage, corrosion", 9),
            new Page(LIGHTNING_PAGE, "allowable damage, lightning strike", 10),
            new Page(PENETRATION, "allowable damage, cracks and penetration", 9));

    /** The edge margin every limited row asks for, in diameters of the nearest fastener. */
    public static final BigDecimal TWO_DIAMETERS = new BigDecimal("2.0");

    /**
     * The share of the pressurised skin depth cap a score lying along the body may use. Hoop stress is
     * about twice the longitudinal stress in the barrel, and it acts across a longitudinal score.
     */
    public static final Ratio ACROSS_LOAD_SHARE = new Ratio("0.50");

    /** The taper a blend runs out at on barrel skin. */
    public static final Taper BLEND = new Taper(20);

    /**
     * The skins the pressurised material loss rows are written for. The belly holds cabin pressure
     * like the barrel sides do, and read against the any skin row it was getting twice the depth the
     * pressure boundary allows.
     */
    public static final List<PanelKind> PRESSURE_SKINS = List.of(PanelKind.PRESSURISED_SKIN, PanelKind.BELLY_SKIN);

    /** Where nothing is allowable whatever it measures. */
    public static final List<Region> NOTHING_ALLOWED_IN =
            List.of(Region.FASTENER_ROW, Region.CUTOUT_CORNER, Region.CHEM_MILL_STEP);

    /** How many revisions' tables are kept built at once. */
    private static final int KEPT = 8;

    private static final Map<Integer, LimitTable> BUILT = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, LimitTable> eldest) {
            return size() > KEPT;
        }
    };

    private Manual53() {}

    /** The chapter with its revision history. */
    public static Manual manual() {
        return new Manual(CHAPTER, REVISIONS);
    }

    /**
     * The whole allowable damage table for the barrel, at one revision.
     *
     * <p>Built once per revision and shared: asking twice for the same revision hands back the same
     * table, for the last eight revisions asked for.
     */
    public static LimitTable allowableDamage(int revision) {
        synchronized (BUILT) {
            return BUILT.computeIfAbsent(revision, Manual53::build);
        }
    }

    private static LimitTable build(int revision) {
        List<LimitRow> rows = new ArrayList<>();
        rows.addAll(dentRows(revision));
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

    /**
     * One kind of skin's dent limits as the page writes them: the depth and the sharpness before the
     * campaign revision and from it on. A skin the campaign did not touch carries the same both times,
     * and a null skin is the row for any skin the page names no row for.
     */
    private record Dents(
            PanelKind skin, String depth, String sharpness, String campaignDepth, String campaignSharpness) {

        Dents(PanelKind skin, String depth, String sharpness) {
            this(skin, depth, sharpness, depth, sharpness);
        }

        LimitRow row(int revision) {
            boolean campaign = revision >= DENT_CAMPAIGN_REVISION;
            return new LimitRow(
                    LimitKey.of(DamageKind.DENT, skin, null),
                    LimitSet.allowable()
                            .withMaxDepth(inches(campaign ? campaignDepth : depth))
                            .withMaxSharpness(new Ratio(campaign ? campaignSharpness : sharpness))
                            .withMinEdgeMarginDiameters(TWO_DIAMETERS),
                    DEFORMATION);
        }
    }

    /** The dent table, any skin first. The belly holds pressure and takes the pressurised numbers. */
    private static final List<Dents> DENTS = List.of(
            new Dents(null, "0.100", "0.10"),
            new Dents(PanelKind.PRESSURISED_SKIN, "0.080", "0.08", "0.060", "0.05"),
            new Dents(PanelKind.BELLY_SKIN, "0.080", "0.08", "0.060", "0.05"),
            new Dents(PanelKind.CROWN_SKIN, "0.080", "0.08"),
            new Dents(PanelKind.UNPRESSURISED_SKIN, "0.150", "0.15"));

    private static List<LimitRow> dentRows(int revision) {
        List<LimitRow> rows = new ArrayList<>();
        DENTS.forEach(dents -> rows.add(dents.row(revision)));
        rows.addAll(nothingIn(DamageKind.DENT, DEFORMATION));
        return rows;
    }

    /** One score kind's share of the thickness and floor on what is left, on open skin. */
    private record Score(DamageKind kind, String share, String residual) {}

    private static List<LimitRow> scoreRows() {
        List<LimitRow> rows = new ArrayList<>();
        for (Score score : List.of(
                new Score(DamageKind.SCRATCH, "0.10", "0.80"),
                new Score(DamageKind.GOUGE, "0.08", "0.82"),
                new Score(DamageKind.NICK, "0.10", "0.80"))) {
            rows.add(new LimitRow(
                    new LimitKey(score.kind()),
                    LimitSet.allowable()
                            .withMaxDepthFraction(new Ratio(score.share()))
                            .withMinResidualFraction(new Ratio(score.residual()))
                            .withMinEdgeMarginDiameters(TWO_DIAMETERS)
                            .withBlendTaper(BLEND),
                    MATERIAL_LOSS));
            for (PanelKind skin : PRESSURE_SKINS) {
                rows.add(new LimitRow(
                        new LimitKey(score.kind(), skin),
                        LimitSet.allowable()
                                .withMaxDepthFraction(new Ratio("0.05"))
                                .withAcrossLoadShare(ACROSS_LOAD_SHARE)
                                .withMinResidualFraction(new Ratio("0.85"))
                                .withMinEdgeMarginDiameters(TWO_DIAMETERS)
                                .withBlendTaper(BLEND),
                        MATERIAL_LOSS));
            }
            rows.addAll(nothingIn(score.kind(), MATERIAL_LOSS));
        }
        return rows;
    }

    private static List<LimitRow> corrosionRows() {
        List<LimitRow> rows = new ArrayList<>();
        rows.add(new LimitRow(
                new LimitKey(DamageKind.CORROSION),
                LimitSet.allowable()
                        .withMaxDepthFraction(new Ratio("0.10"))
                        .withMinResidualFraction(new Ratio("0.80"))
                        .withMaxArea(new BigDecimal("6.00"))
                        .withBlendTaper(new Taper(30)),
                CORROSION_PAGE));
        for (PanelKind skin : PRESSURE_SKINS) {
            rows.add(new LimitRow(
                    new LimitKey(DamageKind.CORROSION, skin),
                    LimitSet.allowable()
                            .withMaxDepthFraction(new Ratio("0.05"))
                            .withMinResidualFraction(new Ratio("0.85"))
                            .withMaxArea(new BigDecimal("4.00"))
                            .withBlendTaper(new Taper(30)),
                    CORROSION_PAGE));
        }
        rows.add(new LimitRow(
                new LimitKey(DamageKind.LIGHTNING_BURN),
                LimitSet.allowable()
                        .withMaxDepth(inches("0.010"))
                        .withMaxArea(new BigDecimal("2.00"))
                        .withBlendTaper(BLEND),
                LIGHTNING_PAGE));
        rows.addAll(nothingIn(DamageKind.CORROSION, CORROSION_PAGE));
        rows.addAll(nothingIn(DamageKind.LIGHTNING_BURN, LIGHTNING_PAGE));
        return rows;
    }

    private static List<LimitRow> penetrationRows() {
        return List.of(DamageKind.CRACK, DamageKind.HOLE, DamageKind.PUNCTURE, DamageKind.BUCKLE, DamageKind.DISBOND)
                .stream()
                .map(kind -> new LimitRow(new LimitKey(kind), LimitSet.nothingAllowable(), PENETRATION))
                .toList();
    }
}
