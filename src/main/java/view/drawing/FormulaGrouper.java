package view.drawing;

import entity.Stroke;
import view.answer.AnswerLayout;
import view.answer.EqualsSpot;
import view.answer.Formula;
import view.answer.Fractions;
import view.answer.Scripts;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Decides which loose strokes belong together as one formula when the writer pauses: a row of ink,
 * limits stacked over or under it, both halves of a fraction, a lone "=" after a formula.
 *
 * <p>Works only on the {@link InkModel} (Single Responsibility): no Swing, no painting, no input.
 */
public final class FormulaGrouper {
    private final InkModel ink;

    public FormulaGrouper(InkModel ink) {
        this.ink = Objects.requireNonNull(ink, "ink");
    }

    // groups pool w/ earlier pauses' unanswered guesses near it, a pause mid fraction cant split it
    public void regroup(Stroke last, List<Stroke> pool) {
        double h = Math.max(AnswerLayout.strokeHeight(pool), olderRowHeight(pool));
        List<Formula> regroup = ink.book().takeGuessesNear(boundsOf(pool), h);
        if (!regroup.isEmpty()) {
            Set<Stroke> mine = new HashSet<>(pool);
            pool = new ArrayList<>();
            for (Stroke s : ink.strokes()) {
                if (!ink.book().isBound(s) && (mine.contains(s) || inAny(regroup, s))) {
                    pool.add(s);
                }
            }
            h = Math.max(AnswerLayout.strokeHeight(pool), olderRowHeight(pool));
        }
        bindOrKeep(looseGroup(last, h), regroup);
        for (Stroke s : pool) {
            if (!ink.book().isBound(s)) {
                bindOrKeep(looseGroup(s, h), regroup);
            }
        }
    }

    private static boolean inAny(List<Formula> formulas, Stroke s) {
        for (Formula f : formulas) {
            if (f.getStrokes().contains(s)) {
                return true;
            }
        }
        return false;
    }

    // came out the same as before the pause = that formula back w/ its id + revision, else a new one
    private void bindOrKeep(Set<Stroke> group, List<Formula> regroup) {
        for (Formula f : regroup) {
            if (f.getStrokes().equals(group)) {
                ink.book().putBack(f);
                return;
            }
        }
        bindGroup(group);
    }

    // undo can leave a whole formula loose: its ink on this round's row sizes it too, 0 if none
    private double olderRowHeight(List<Stroke> round) {
        Rectangle box = boundsOf(round);
        List<Stroke> row = new ArrayList<>(round);
        for (Stroke s : ink.strokes()) {
            Rectangle b = ink.book().isBound(s) || round.contains(s) ? null : boundsOf(List.of(s));
            if (b != null && b.getMinY() <= box.getMaxY() && b.getMaxY() >= box.getMinY()) {
                row.add(s);
            }
        }
        return row.size() == round.size() ? 0 : AnswerLayout.strokeHeight(row);
    }

    // an "=" on its own this round joins the formula just left of it, even far off or bigger than its ink
    public void joinLoneEquals(List<Stroke> round) {
        List<Stroke> fresh = new ArrayList<>();
        for (Stroke s : round) {
            if (ink.strokes().contains(s) && ink.book().isBound(s) && !fresh.contains(s)) {
                fresh.add(s);
            }
        }
        for (int i = 0; i < fresh.size(); i++) {
            for (int j = i + 1; j < fresh.size(); j++) {
                Stroke a = fresh.get(i);
                Stroke b = fresh.get(j);
                if (!EqualsSpot.isEqualsPair(List.of(a, b)) || !ink.book().holdsOnly(a, b)) {
                    continue;
                }
                Formula left = ink.book().leftOfPair(a, b);
                if (left != null) {
                    Set<Stroke> both = new LinkedHashSet<>(left.getStrokes());
                    both.add(a);
                    both.add(b);
                    ink.book().addGuess(both, ink.nextRevision());
                }
            }
        }
    }

    // limits written after the pause join the formula they sit on, judged as a whole row; else a new one
    private Formula bindGroup(Set<Stroke> group) {
        for (Formula f : ink.book().all()) {
            if (Scripts.stackedOn(f.getStrokes(), group)) {  // one way only, old formula is never the new ink's limit
                Set<Stroke> both = new LinkedHashSet<>(f.getStrokes());
                both.addAll(group);
                return ink.book().addGuess(both, ink.nextRevision());
            }
        }
        return ink.book().addGuess(group, ink.nextRevision());
    }

    // seed's row, plus any limits stacked over/under it (or the row it's a limit of), or a fraction's other half
    private Set<Stroke> looseGroup(Stroke seed, double h) {
        Set<Stroke> group = looseRow(seed, h, Set.of());
        List<Stroke> loose = new ArrayList<>();  // top, bar + bottom can each be their own row
        for (Stroke s : ink.strokes()) {
            if (!ink.book().isBound(s)) {
                loose.add(s);
            }
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Stroke s : ink.strokes()) {
                if (group.contains(s) || ink.book().isBound(s)) {
                    continue;
                }
                Set<Stroke> other = looseRow(s, h, group);
                // beside the whole group too, a fraction pulled in from the seed's row can reach ink the row couldnt
                if (AnswerLayout.sameNewFormula(boundsOf(group), h, boundsOf(List.of(s)))
                        || Scripts.stackedOn(group, other) || Scripts.stackedOn(other, group)
                        || Fractions.across(group, other, loose, h)) {
                    group.addAll(other);
                    grew = true;
                }
            }
        }
        return group;
    }

    // unbound strokes on seed's line, grown until nothing else joins
    private Set<Stroke> looseRow(Stroke seed, double h, Set<Stroke> skip) {
        Set<Stroke> group = new LinkedHashSet<>(List.of(seed));
        boolean grew = true;
        while (grew) {
            grew = false;
            Rectangle box = boundsOf(group);
            for (Stroke s : ink.strokes()) {
                Rectangle b = group.contains(s) || skip.contains(s) || ink.book().isBound(s) ? null : boundsOf(List.of(s));
                if (b != null && box != null && AnswerLayout.sameNewFormula(box, h, b)) {
                    group.add(s);
                    grew = true;
                }
            }
        }
        return group;
    }

    private static Rectangle boundsOf(Iterable<Stroke> strokeSet) {
        return AnswerLayout.bounds(strokeSet);
    }
}
