package io.github.rkaushikdev.doublerbay.assess;

import io.github.rkaushikdev.doublerbay.damage.Cluster;
import io.github.rkaushikdev.doublerbay.damage.Clusters;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Placed;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.WingPoint;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.manual.CleanupRule;
import io.github.rkaushikdev.doublerbay.manual.CleanupRules;
import io.github.rkaushikdev.doublerbay.manual.CountLimits;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Every card on one panel, assessed together, which is not the same as assessing the cards on it one
 * at a time.
 *
 * <p>Four of the book's rules only exist once every card on the panel is on the table at once. Two
 * damages inside a clear separation are one damage. A bay holds so many and no more. A panel holds so
 * many and no more. And the blends on one panel take their share of it between them, so two blends
 * that each take a fifth of a panel take two fifths. A desk working card by card passes all four
 * without ever seeing them.
 *
 * <p>The order matters and it is the order the book states. Cluster first, because clustering
 * changes how many damages there are, and a panel with eight cards on it may be over the panel count
 * at eight and inside it at five once three of them have been taken together. Then count the bays,
 * then count the panel, then add up the blends.
 *
 * <p>What comes out is the per card answer raised where a panel rule bit it, with the panel rule in
 * that card's own trail. A card that was allowable on its own and is not once its neighbours are
 * counted should say so on its own face.
 */
public record PanelAssessment(PanelId part, List<Assessment> assessments, List<Cluster> groups) {

    /** The cards on one panel and the groups the book takes them in. */
    public PanelAssessment {
        Objects.requireNonNull(part, "part");
        assessments = List.copyOf(assessments);
        groups = List.copyOf(groups);
    }

    /** How many cards are on the panel. */
    public int cardCount() {
        return assessments.size();
    }

    /** How many damages the book counts here, which is groups and not cards. */
    public int damageCount() {
        return groups.size();
    }

    /** The worst answer on the panel, allowable when there is nothing on it. */
    public Disposition disposition() {
        return Assessor.worstDisposition(assessments);
    }

    /**
     * The assessment of one card on the panel.
     *
     * @throws NoSuchElementException when the card is not on the panel
     */
    public Assessment forCard(FindingId reference) {
        for (Assessment one : assessments) {
            if (one.finding().reference().equals(reference)) {
                return one;
            }
        }
        throw new NoSuchElementException(reference.toString());
    }

    /** {@code 5310-142L: 3 cards in 1 groups, allowable as it stands}. */
    @Override
    public String toString() {
        return part + ": " + cardCount() + " cards in " + damageCount() + " groups, " + disposition();
    }

    /**
     * Assess a set of cards that all sit on one panel.
     *
     * @throws IllegalArgumentException when there are no cards, or a card is on another panel
     */
    public static PanelAssessment assessPanel(Book book, TailContext tail, List<Finding> findings) {
        if (findings.isEmpty()) {
            throw new IllegalArgumentException("a panel assessment needs at least one card");
        }

        PanelId part = null;
        Map<FindingId, Assessment> answers = new LinkedHashMap<>();
        List<Placed> placed = new ArrayList<>();
        Map<FindingId, String> bays = new LinkedHashMap<>();

        for (Finding finding : findings) {
            Assessment one = Assessor.assess(book, tail, finding);
            if (part == null) {
                part = one.placement().part();
            } else if (!one.placement().part().equals(part)) {
                throw new IllegalArgumentException(finding.reference() + " is not on " + part);
            }
            answers.put(finding.reference(), one);
            placed.add(new Placed(finding, one.placement().centre()));
            bays.put(finding.reference(), bayKey(tail, finding));
        }

        List<Cluster> groups = Clusters.clusters(placed, book.interaction().separation());
        CountLimits counts = book.interaction().counts();

        clusterReasons(answers, groups, counts);
        bayReasons(answers, perBay(bays), bays, counts);
        panelReasons(answers, part, groups.size(), counts);
        blendReasons(answers, part, book.cleanup());

        List<Assessment> ordered = findings.stream()
                .map(finding -> answers.get(finding.reference()))
                .toList();
        return new PanelAssessment(part, ordered, groups);
    }

    /**
     * Split a tail's open cards by panel and assess each panel as a whole.
     *
     * <p>The panels come back ordered on their part numbers written out field by field as text: the
     * chapter, then the section, then the sequence, then the hand, each compared as written, so
     * section 10 comes before section 5.
     */
    public static List<PanelAssessment> assessByPanel(Book book, TailContext tail, List<Finding> findings) {
        Map<PanelId, List<Finding>> grouped = new LinkedHashMap<>();
        for (Finding finding : findings) {
            PanelId part = Assessor.assess(book, tail, finding).placement().part();
            grouped.computeIfAbsent(part, ignored -> new ArrayList<>()).add(finding);
        }
        return grouped.entrySet().stream()
                .sorted(Comparator.comparing(entry -> writtenOut(entry.getKey())))
                .map(entry -> assessPanel(book, tail, entry.getValue()))
                .toList();
    }

    /** A part number written out field by field, which is the order panels are listed in. */
    private static String writtenOut(PanelId part) {
        return "PanelId(chapter=" + part.chapter() + ", section=" + part.section() + ", sequence=" + part.sequence()
                + ", hand='" + part.hand() + "')";
    }

    private static String bayKey(TailContext tail, Finding finding) {
        return switch (finding.point()) {
            case FuselagePoint point -> tail.barrel().bayAt(point).toString();
            case WingPoint point ->
                tail.wingBox().gridFor(point.side()).bayOf(point).toString();
        };
    }

    private static Map<String, Integer> perBay(Map<FindingId, String> bays) {
        Map<String, Integer> counted = new LinkedHashMap<>();
        bays.values().forEach(bay -> counted.merge(bay, 1, Integer::sum));
        return counted;
    }

    /** Put a panel rule into a card's own trail, raising the card to a repair when it was over. */
    private static Assessment counted(Assessment one, String rule, String detail, boolean over) {
        return raiseWith(one, new Reason(rule, detail, !over), over ? Disposition.REPAIRABLE : Disposition.ALLOWABLE);
    }

    /** Put a panel rule into a card's own trail, and raise its answer if it bit. */
    private static Assessment raiseWith(Assessment one, Reason reason, Disposition to) {
        Verdict verdict = one.verdict().withReason(reason);
        if (to.worseThan(verdict.disposition())) {
            verdict = verdict.raisedTo(to);
        }
        return one.withVerdict(verdict);
    }

    private static void clusterReasons(Map<FindingId, Assessment> answers, List<Cluster> groups, CountLimits counts) {
        for (Cluster group : groups) {
            if (group.isSingle()) {
                continue;
            }
            String detail = "assessed with " + (group.size() - 1) + " other damage inside the clear separation";
            boolean over = counts.clusterExceeded(group.size());
            for (Placed member : group.members()) {
                answers.computeIfPresent(
                        member.finding().reference(), (reference, one) -> counted(one, "group", detail, over));
            }
        }
    }

    private static void bayReasons(
            Map<FindingId, Assessment> answers,
            Map<String, Integer> perBay,
            Map<FindingId, String> bays,
            CountLimits counts) {
        if (counts.perBay().isEmpty()) {
            return;
        }
        int limit = counts.perBay().get();
        for (Map.Entry<FindingId, String> entry : bays.entrySet()) {
            int found = perBay.get(entry.getValue());
            String detail = found + " in " + entry.getValue() + " against a limit of " + limit;
            answers.computeIfPresent(
                    entry.getKey(), (reference, one) -> counted(one, "bay count", detail, counts.bayExceeded(found)));
        }
    }

    private static void panelReasons(
            Map<FindingId, Assessment> answers, PanelId part, int damages, CountLimits counts) {
        if (counts.perPanel().isEmpty()) {
            return;
        }
        boolean over = counts.panelExceeded(damages);
        String detail = damages + " on " + part + " against a limit of "
                + counts.perPanel().get();
        answers.replaceAll((reference, one) -> counted(one, "panel count", detail, over));
    }

    /**
     * Add up what the blends on the panel take of it, once there is more than one.
     *
     * <p>Each card's own blend share check reads its blend against the panel alone. Here the blends
     * waiting to be done on the panel are added together, and the share the tightest of their rules
     * allows is what they are held to between them. Over it, every one of them goes to a repair: which
     * blend to swap for a doubler is an engineer's call, not the order the cards were raised in.
     */
    private static void blendReasons(Map<FindingId, Assessment> answers, PanelId part, CleanupRules cleanups) {
        List<Assessment> blends = answers.values().stream()
                .filter(one -> one.disposition() == Disposition.REWORKABLE)
                .toList();
        if (blends.size() < 2) {
            return;
        }
        Length panel = blends.get(0).placement().panelExtent();
        Length covered = new Length(0);
        BigDecimal allowed = BigDecimal.ONE;
        for (Assessment one : blends) {
            CleanupRule rule = cleanups.forKind(one.finding().kind());
            Length depth = one.finding().governingDepth();
            Length extent = one.finding().footprint(one.placement().centre()).longerSide();
            covered = covered.plus(rule.blendExtent(extent, depth));
            allowed = allowed.min(rule.maxBlendFraction().orElse(BigDecimal.ONE));
        }
        BigDecimal share = Decimals.over(BigDecimal.valueOf(covered.mils()), BigDecimal.valueOf(panel.mils()));
        boolean over = share.compareTo(allowed) > 0;
        String detail =
                blends.size() + " blends take " + Decimals.quantize(share, 2).toPlainString() + " of " + part
                        + " against " + Decimals.quantize(allowed, 2).toPlainString();
        for (Assessment one : blends) {
            answers.computeIfPresent(
                    one.finding().reference(), (reference, held) -> counted(held, "panel blends", detail, over));
        }
    }
}
