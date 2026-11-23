package io.github.rkaushikdev.doublerbay.assess;

import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Scoring;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Joint;
import io.github.rkaushikdev.doublerbay.structure.JointKind;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The rules that only bite when the damage is near a fastener row.
 *
 * <p>Away from a joint a scratch is a scratch. In a fastener row it is a scratch in the most highly
 * loaded strip of skin on the aeroplane, sitting in the stress field of a hole, and the same
 * measurement means something entirely different.
 *
 * <p>Three rules for a riveted joint, and none of them are about size. Whether the damage sits inside the edge margin,
 * because a cut between the hole and the panel edge has taken the material that was carrying the load
 * past the hole. Whether it runs along the row, because a crack parallel to a rivet line links holes
 * and a crack across one does not. And whether the joint is fatigue critical, because a longitudinal
 * lap joint is where the fleet's cracks actually come from.
 *
 * <p>A bonded joint has no rows for those three to read, and its own rule is about the bond line.
 *
 * <p>These are additions rather than replacements. A damage in a joint still has to pass every
 * ordinary limit, and these rules can only make the answer worse.
 */
public final class Joints {

    /** How near a row counts as being in it, in fastener diameters. */
    public static final BigDecimal IN_THE_ROW = new BigDecimal("2.0");

    /** A cut this far off the row's own line, in degrees, still counts as running along it. */
    public static final int ALONG_WITHIN_DEGREES = 20;

    /** How far off the member a bonded joint's bond line runs, which is the band the bond rule covers. */
    public static final Length BOND_LINE = Length.ofInches("1.000");

    /** The joint rules, in the order they run. */
    public static final List<Function<JointCase, Optional<Check>>> JOINT_CHECKS = List.of(
            Joints::insideMarginCheck, Joints::alongTheRowCheck, Joints::fatigueCriticalCheck, Joints::bondLineCheck);

    private Joints() {}

    /** Which way the damage runs, for the kinds that have a direction. */
    public static Optional<Angle> bearingOf(Finding finding) {
        return switch (finding.geometry()) {
            case Crack crack -> Optional.of(crack.bearing());
            case Scoring scoring -> Optional.of(scoring.bearing());
            default -> Optional.empty();
        };
    }

    /**
     * Whether the damage lies along the row rather than across it.
     *
     * <p>A longitudinal joint runs along the body, so a damage at or near zero bearing is running with
     * the rivet line. Nothing is claimed for a damage with no direction, which is why this can answer
     * empty.
     */
    public static Optional<Boolean> runsAlong(Finding finding, Joint joint) {
        Optional<Angle> bearing = bearingOf(finding);
        if (bearing.isEmpty()) {
            return Optional.empty();
        }
        int line = joint.isLongitudinal() ? 0 : 900;
        int off = Math.abs(bearing.get().tenths() - line);
        return Optional.of(Math.min(off, 1800 - off) <= ALONG_WITHIN_DEGREES * 10);
    }

    /** A damage between the fastener and the panel edge has taken load path. */
    public static Optional<Check> insideMarginCheck(JointCase held) {
        if (!held.isInTheRow()) {
            return Optional.empty();
        }
        String written = held.toTheRow().inches().toPlainString() + " in from the nearest row";
        return Rules.flag(
                "joint edge margin",
                held.margin().isGreaterThan(held.toTheRow()),
                "clear of the row, " + written,
                "inside the fastener row, " + written,
                Disposition.REPAIRABLE);
    }

    /** A cut running along a rivet line links holes, and one across it does not. */
    public static Optional<Check> alongTheRowCheck(JointCase held) {
        Optional<Boolean> along = runsAlong(held.subject().finding(), held.joint());
        if (along.isEmpty() || !held.isInTheRow()) {
            return Optional.empty();
        }
        return Rules.flag(
                "along the row",
                along.get(),
                "runs across the rivet line rather than with it",
                "runs with the rivet line on " + held.joint().kind(),
                Disposition.REPAIRABLE);
    }

    /** A damage in a fatigue critical joint is never left as it stands. */
    public static Optional<Check> fatigueCriticalCheck(JointCase held) {
        if (!held.isInTheRow()) {
            return Optional.empty();
        }
        return Rules.flag(
                "fatigue critical",
                held.joint().isFatigueCritical(),
                held.joint().kind() + " is not a fatigue critical joint",
                held.joint().kind() + " carries the pressurisation cycle",
                Disposition.REWORKABLE);
    }

    /**
     * A damage on the bond line of a bonded joint may have broken the bond under it.
     *
     * <p>A bonded joint has no rows, so none of the fastener rules reach it, and the bond is not
     * something anybody can see from outside. Damage inside the bond line has loaded an adhesive joint
     * the book has no repair for, and it goes to the manufacturer with a request to tap test the joint
     * either side of it.
     */
    public static Optional<Check> bondLineCheck(JointCase held) {
        if (held.joint().kind() != JointKind.BONDED) {
            return Optional.empty();
        }
        String where = held.margin().inches().toPlainString() + " in off "
                + held.joint().follows();
        return Rules.flag(
                "bond line",
                held.margin().isAtMost(BOND_LINE),
                "clear of the bond line, " + where,
                "on the bond line of the " + held.joint().kind() + ", " + where,
                Disposition.OUT_OF_LIMITS);
    }

    /** Every joint rule that has something to say about this damage, none when it is on no joint. */
    public static List<Check> checksFor(Case held, Optional<Joint> joint) {
        return joint.map(onto -> checksFor(held, onto)).orElse(List.of());
    }

    /** Every joint rule that has something to say about this damage on this joint. */
    public static List<Check> checksFor(Case held, Joint joint) {
        JointCase onTheJoint = new JointCase(held, joint);
        return JOINT_CHECKS.stream()
                .map(check -> check.apply(onTheJoint))
                .flatMap(Optional::stream)
                .toList();
    }
}
