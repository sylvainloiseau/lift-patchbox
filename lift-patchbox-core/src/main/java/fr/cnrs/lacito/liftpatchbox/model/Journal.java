package fr.cnrs.lacito.liftpatchbox.model;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.change.ModelChange;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The journal of the changes made to a dictionary, which makes a script atomic and
 * lets a timeline move back and forth through what a series of scripts did.
 *
 * <p>Part 2, section 12.2 makes the whole script the unit of atomicity, and
 * requires an implementation that cannot offer a transactional dictionary to
 * simulate one. This journal is that simulation. It listens to the dictionary: the
 * dictionary model reports every change it makes as a {@link ModelChange}, which
 * carries what is needed to {@linkplain ModelChange#revert() revert} it and to
 * {@linkplain ModelChange#reapply() reapply} it, through the model's public API and
 * on the very objects that changed. The journal keeps them in order.</p>
 *
 * <p>The changes up to a <em>cursor</em> are applied. Undoing moves the cursor back,
 * reverting changes in reverse order; redoing moves it forward again, reapplying
 * them. A change made while the cursor is not at the end discards the changes past
 * it, as an editor's undo history does. A rollback undoes and discards; a commit
 * forgets everything, unless the journal keeps its history for a timeline.</p>
 *
 * <p>Because the same objects are detached and re-attached, never rebuilt, a
 * component keeps its identity — its Java object, and the id the dictionary gave
 * it — across any number of undos and redos.</p>
 *
 * <p>Each change is recorded with the entries whose subtree it changed, worked out
 * when the change is made, by an attribution the model adapter provides. Changes
 * made inside {@link #unattributed(Supplier)} are recorded with none.</p>
 *
 * <p>A run of changes can be {@linkplain #cut(int) cut} from the end of the journal
 * into a {@link Segment}, which can later be {@linkplain #enter(Segment, int) entered}:
 * replayed on top of the state it was cut from, and left again. A timeline keeps the
 * changes of a script that failed this way, off the line of committed changes.</p>
 *
 * <p>A journal is used in preference to working on a copy of the dictionary
 * because a LIFT dictionary is large — tens of megabytes is ordinary — and
 * copying it for every script would dominate the cost of running one.</p>
 */
public final class Journal implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(Journal.class.getName());

    /**
     * A change, and the entries whose subtree it changed.
     *
     * @param change  the change
     * @param entries the entries it changed, compared by identity
     */
    public record Recorded(ModelChange change, Set<LiftEntry> entries) {
    }

    /**
     * A run of changes cut from the journal, which can be replayed on top of the
     * state it was cut from.
     *
     * @param changes the changes, in the order they were made
     */
    public record Segment(List<Recorded> changes) {

        /**
         * Canonical constructor, taking an unmodifiable copy.
         *
         * @param changes the changes
         */
        public Segment {
            changes = List.copyOf(changes);
        }

        /**
         * The number of changes.
         *
         * @return the size of the segment
         */
        public int size() {
            return changes.size();
        }

        /**
         * The entries the changes between two positions of the segment changed.
         *
         * @param from the first position, inclusive
         * @param to   the last position, exclusive
         * @return the entries, compared by identity
         */
        public Set<LiftEntry> entriesBetween(int from, int to) {
            return Journal.entriesOf(changes.subList(from, to));
        }
    }

    private final List<Recorded> changes = new ArrayList<>();
    private int cursor;
    private boolean replaying;
    private boolean keepHistory;
    private int unattributed;
    private Runnable afterReplay = () -> { };
    private Function<ModelChange, Set<LiftEntry>> attribution = c -> Set.of();
    private Segment branch;
    private int branchApplied;
    private javafx.util.Subscription subscription;

    /**
     * A journal recording the changes of a dictionary from now on, until it is
     * {@linkplain #close() closed}.
     *
     * @param dictionary the dictionary to record
     */
    public Journal(LiftDictionary dictionary) {
        this.subscription = dictionary.addChangeListener(this::onChange);
    }

    private void onChange(ModelChange change) {
        if (replaying) {
            // A revert or a reapply reports its own changes; they are the journal's
            // own moves, not new changes.
            return;
        }
        if (branch != null) {
            throw new IllegalStateException("the dictionary changed while a segment was entered");
        }
        if (cursor < changes.size()) {
            changes.subList(cursor, changes.size()).clear();
        }
        Set<LiftEntry> entries = unattributed > 0 ? Set.of() : attribution.apply(change);
        changes.add(new Recorded(change, entries));
        cursor++;
    }

    /**
     * Set what to do after the journal has reverted or reapplied changes, such as
     * invalidating what was derived from the dictionary.
     *
     * @param action the action
     */
    public void afterReplay(Runnable action) {
        this.afterReplay = action;
    }

    /**
     * Set how the entries a change touches are worked out, when it is made.
     *
     * @param attribution the entries whose subtree a change changed
     */
    public void attributeWith(Function<ModelChange, Set<LiftEntry>> attribution) {
        this.attribution = attribution;
    }

    /**
     * Run code whose changes are recorded, but attributed to no entry: changes that
     * a model operation undoes itself before it returns, such as unlinking a
     * reference around a deletion and putting it back.
     *
     * @param code the code
     * @param <T>  what it returns
     * @return what the code returned
     */
    public <T> T unattributed(Supplier<T> code) {
        unattributed++;
        try {
            return code.get();
        } finally {
            unattributed--;
        }
    }

    /**
     * Keep the changes when the journal is committed, so that a timeline can still
     * move back and forth through them.
     *
     * @param keep {@code true} to keep them
     */
    public void keepHistory(boolean keep) {
        this.keepHistory = keep;
    }

    /**
     * The number of changes currently applied.
     *
     * @return the position of the cursor
     */
    public int size() {
        return cursor;
    }

    /**
     * The number of changes recorded, applied or not.
     *
     * @return the number of changes
     */
    public int recorded() {
        return changes.size();
    }

    /**
     * The current position, to roll back or move to later.
     *
     * @return the position of the cursor
     */
    public int mark() {
        return cursor;
    }

    /**
     * The entries the changes between two positions changed.
     *
     * @param from the first position, inclusive
     * @param to   the last position, exclusive
     * @return the entries, compared by identity
     */
    public Set<LiftEntry> entriesBetween(int from, int to) {
        return entriesOf(changes.subList(from, to));
    }

    private static Set<LiftEntry> entriesOf(List<Recorded> recorded) {
        Set<LiftEntry> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Recorded r : recorded) {
            out.addAll(r.entries());
        }
        return out;
    }

    /**
     * Revert the changes past a position, and discard them, those already undone
     * included.
     *
     * @param mark a position obtained from {@link #mark()}
     */
    public void rollbackTo(int mark) {
        undoTo(mark);
        changes.subList(cursor, changes.size()).clear();
    }

    /**
     * Revert and discard every change.
     */
    public void rollback() {
        LOGGER.log(Level.FINE, () -> "Rolling back " + cursor + " change(s)");
        rollbackTo(0);
    }

    /**
     * Keep the changes made. They are forgotten, unless the journal keeps its history.
     */
    public void commit() {
        LOGGER.log(Level.FINE, () -> "Committing " + cursor + " change(s)");
        if (!keepHistory) {
            changes.clear();
            cursor = 0;
        }
    }

    /**
     * Revert the changes past a position, and take them out of the journal, those
     * already undone included, as a segment that can be entered later.
     *
     * @param mark a position obtained from {@link #mark()}
     * @return the changes cut, in the order they were made
     */
    public Segment cut(int mark) {
        undoTo(mark);
        List<Recorded> tail = new ArrayList<>(changes.subList(mark, changes.size()));
        changes.subList(mark, changes.size()).clear();
        return new Segment(tail);
    }

    /**
     * Revert changes until the cursor is at a position at or before the current one.
     *
     * @param mark the position to move back to
     */
    public void undoTo(int mark) {
        if (mark < 0 || mark > cursor) {
            throw new IllegalArgumentException("cannot undo to " + mark + " from " + cursor);
        }
        replay(() -> {
            while (cursor > mark) {
                changes.get(--cursor).change().revert();
            }
        });
    }

    /**
     * Reapply changes until the cursor is at a position at or after the current one.
     *
     * @param mark the position to move forward to
     */
    public void redoTo(int mark) {
        if (mark < cursor || mark > changes.size()) {
            throw new IllegalArgumentException("cannot redo to " + mark + " from " + cursor);
        }
        replay(() -> {
            while (cursor < mark) {
                changes.get(cursor++).change().reapply();
            }
        });
    }

    /**
     * Move the cursor to a position, leaving any entered segment first, and undoing
     * or redoing as needed.
     *
     * @param mark the position
     */
    public void moveTo(int mark) {
        leave();
        if (mark < cursor) {
            undoTo(mark);
        } else if (mark > cursor) {
            redoTo(mark);
        }
    }

    /**
     * Replay the first changes of a segment on top of the current state, which must
     * be the state the segment was cut from. A segment already entered is left first.
     *
     * @param segment the segment
     * @param count   how many of its changes to reapply
     */
    public void enter(Segment segment, int count) {
        if (count < 0 || count > segment.size()) {
            throw new IllegalArgumentException("a segment of " + segment.size()
                + " change(s) has no position " + count);
        }
        if (branch != segment) {
            leave();
        }
        if (branch == null) {
            branch = segment;
            branchApplied = 0;
        }
        replay(() -> {
            while (branchApplied > count) {
                branch.changes().get(--branchApplied).change().revert();
            }
            while (branchApplied < count) {
                branch.changes().get(branchApplied++).change().reapply();
            }
        });
    }

    /**
     * Revert the changes of the segment entered, if any.
     */
    public void leave() {
        if (branch == null) {
            return;
        }
        replay(() -> {
            while (branchApplied > 0) {
                branch.changes().get(--branchApplied).change().revert();
            }
        });
        branch = null;
    }

    private void replay(Runnable moves) {
        if (replaying) {
            throw new IllegalStateException("the journal is already replaying");
        }
        replaying = true;
        try {
            moves.run();
        } finally {
            replaying = false;
            afterReplay.run();
        }
    }

    /**
     * Stop recording the changes of the dictionary. The changes already recorded
     * can still be undone and redone.
     */
    @Override
    public void close() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }
}
