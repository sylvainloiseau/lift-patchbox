package fr.cnrs.lacito.liftpatchbox.timeline;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftpatchbox.LiftPatchEngine;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.engine.Executor;
import fr.cnrs.lacito.liftpatchbox.engine.Operation;
import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCollector;
import fr.cnrs.lacito.liftpatchbox.model.Journal;
import fr.cnrs.lacito.liftpatchbox.model.LiftModel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A series of scripts applied to a dictionary, which can be brought to the state
 * it had at any point of the series.
 *
 * <pre>{@code
 * try (Timeline timeline = engine.record(dictionary, scripts)) {
 *     timeline.seek(timeline.positions().get(12));   // the dictionary as it was then
 *     for (TimelineOperation op : timeline.historyOf("mami_1234")) {
 *         ...                                         // what shaped that entry
 *     }
 * }                                                   // back to the end of the series
 * }</pre>
 *
 * <p>Recording applies the series once. Each script is validated, then run in its
 * own transaction, as {@link LiftPatchEngine#apply} would: a script with a static
 * error is rejected and changes nothing; a script whose command fails is rolled
 * back; the next script starts from what the previous ones committed. Labels and
 * {@code with} scopes do not carry over from one script to the next.</p>
 *
 * <p>Seeking reverts or reapplies the changes the dictionary reported, on the very
 * objects that changed: the dictionary is changed in place, and a component keeps
 * its identity and its id across seeks. Nothing else may change the dictionary while
 * the timeline is open; a listener added with {@link #addSeekListener(Consumer)} is
 * told when to read it again. {@link #close()} brings it back to the end of the
 * series.</p>
 *
 * <p>The positions inside a script that failed can be sought too, for debugging:
 * they are {@linkplain TimelinePosition#isCommitted() not committed}, and leaving
 * them restores the committed state.</p>
 */
public final class Timeline implements AutoCloseable {

    private final Journal journal;
    private final List<ScriptRun> scripts;
    private final List<TimelineOperation> operations;
    private final List<TimelinePosition> positions;
    private final List<Consumer<TimelinePosition>> listeners = new CopyOnWriteArrayList<>();
    private TimelinePosition current;
    private boolean closed;

    private Timeline(
        Journal journal, List<ScriptRun> scripts, List<TimelineOperation> operations,
        List<TimelinePosition> positions
    ) {
        this.journal = journal;
        this.scripts = List.copyOf(scripts);
        this.operations = List.copyOf(operations);
        this.positions = List.copyOf(positions);
        this.current = end();
    }

    /**
     * Apply a series of scripts to a dictionary, recording a timeline of it.
     *
     * @param engine     the engine to validate and run the scripts with
     * @param dictionary the dictionary, left at the end of the series
     * @param series     the scripts, in the order they are applied
     * @return the timeline
     */
    public static Timeline record(LiftPatchEngine engine, LiftDictionary dictionary, List<Script> series) {
        Journal journal = new Journal(dictionary);
        journal.keepHistory(true);
        List<ScriptRun> runs = new ArrayList<>();
        List<TimelineOperation> operations = new ArrayList<>();
        List<TimelinePosition> positions = new ArrayList<>();
        positions.add(new TimelinePosition(0, TimelinePosition.Status.START, null, 0, null, 0));
        try {
            LiftModel model = new LiftModel(dictionary, engine.metamodel(), journal);
            for (int i = 0; i < series.size(); i++) {
                Script script = series.get(i);
                ErrorCollector errors = engine.validate(script);
                if (errors.hasErrors()) {
                    runs.add(new ScriptRun(i, script.source(), ScriptRun.Status.REJECTED,
                        Plan.ofStaticErrors(script.source(), script.syntaxName(),
                            engine.metamodel().version(), errors.errors(), errors.warnings()),
                        List.of()));
                    continue;
                }
                int start = journal.mark();
                Executor executor = engine.newExecutor(model, journal);
                Plan plan = executor.execute(script);
                List<Integer> marks = executor.operationMarks();
                List<TimelineOperation> scriptOperations = new ArrayList<>();
                if (plan.isOk()) {
                    journal.commit();
                    int previous = start;
                    for (int k = 0; k < plan.operations().size(); k++) {
                        int mark = Math.max(previous, marks.get(k));
                        TimelineOperation op = operation(operations, i, plan.operations().get(k),
                            journal.entriesBetween(previous, mark), true);
                        scriptOperations.add(op);
                        positions.add(new TimelinePosition(positions.size(),
                            TimelinePosition.Status.COMMITTED, op, mark, null, 0));
                        previous = mark;
                    }
                    runs.add(new ScriptRun(i, script.source(), ScriptRun.Status.APPLIED, plan,
                        scriptOperations));
                } else {
                    Journal.Segment segment = journal.cut(start);
                    int previous = 0;
                    for (int k = 0; k < plan.operations().size(); k++) {
                        int count = Math.min(segment.size(),
                            Math.max(previous, marks.get(k) - start));
                        TimelineOperation op = operation(operations, i, plan.operations().get(k),
                            segment.entriesBetween(previous, count), false);
                        scriptOperations.add(op);
                        positions.add(new TimelinePosition(positions.size(),
                            TimelinePosition.Status.FAILED_SCRIPT, op, start, segment, count));
                        previous = count;
                    }
                    runs.add(new ScriptRun(i, script.source(), ScriptRun.Status.FAILED, plan,
                        scriptOperations));
                }
            }
        } finally {
            // The series is recorded: from now on, the journal only replays.
            journal.close();
        }
        return new Timeline(journal, runs, operations, positions);
    }

    private static TimelineOperation operation(
        List<TimelineOperation> all, int scriptIndex, Operation operation,
        Set<LiftEntry> entries, boolean committed
    ) {
        Set<String> ids = new LinkedHashSet<>();
        for (LiftEntry entry : entries) {
            entry.getId().ifPresent(ids::add);
        }
        TimelineOperation op = new TimelineOperation(all.size() + 1, scriptIndex, operation, ids,
            committed);
        all.add(op);
        return op;
    }

    /**
     * What became of each script of the series.
     *
     * @return one run per script, in order
     */
    public List<ScriptRun> scripts() {
        return scripts;
    }

    /**
     * Every operation of the series, those of failed scripts included, in order.
     *
     * @return the operations
     */
    public List<TimelineOperation> operations() {
        return operations;
    }

    /**
     * Every position the dictionary can be brought to, in order: the start, then one
     * after each operation.
     *
     * @return the positions
     */
    public List<TimelinePosition> positions() {
        return positions;
    }

    /**
     * The position the dictionary is at.
     *
     * @return the current position
     */
    public TimelinePosition current() {
        return current;
    }

    /**
     * The last committed position: the dictionary at the end of the series.
     *
     * @return the position
     */
    public TimelinePosition end() {
        for (int i = positions.size() - 1; i >= 0; i--) {
            if (positions.get(i).isCommitted()) {
                return positions.get(i);
            }
        }
        throw new IllegalStateException("a timeline always has a start");
    }

    /**
     * Bring the dictionary to the state it had at a position.
     *
     * @param position one of {@link #positions()}
     */
    public void seek(TimelinePosition position) {
        if (closed) {
            throw new IllegalStateException("the timeline is closed");
        }
        if (positions.get(position.index()) != position) {
            throw new IllegalArgumentException("not a position of this timeline: " + position);
        }
        journal.moveTo(position.mark());
        if (position.status() == TimelinePosition.Status.FAILED_SCRIPT) {
            journal.enter(position.segment(), position.segmentCount());
        }
        current = position;
        listeners.forEach(l -> l.accept(position));
    }

    /**
     * The committed operations that changed the subtree of an entry, in order: its
     * creation, the changes to its forms, senses, examples and every other
     * component under it, its deletion.
     *
     * @param entryId the id of the entry
     * @return the operations
     */
    public List<TimelineOperation> historyOf(String entryId) {
        return historyOf(entryId, false);
    }

    /**
     * The operations that changed the subtree of an entry, in order.
     *
     * @param entryId       the id of the entry
     * @param includeFailed whether to include the operations of failed scripts,
     *                      whose changes were rolled back
     * @return the operations
     */
    public List<TimelineOperation> historyOf(String entryId, boolean includeFailed) {
        return operations.stream()
            .filter(op -> op.entryIds().contains(entryId))
            .filter(op -> includeFailed || op.committed())
            .toList();
    }

    /**
     * Be told after each seek, to read the dictionary again.
     *
     * @param listener receives the position sought
     */
    public void addSeekListener(Consumer<TimelinePosition> listener) {
        listeners.add(listener);
    }

    /**
     * Stop being told of seeks.
     *
     * @param listener a listener added before
     */
    public void removeSeekListener(Consumer<TimelinePosition> listener) {
        listeners.remove(listener);
    }

    /**
     * Bring the dictionary back to the end of the series, and allow no further seek.
     */
    @Override
    public void close() {
        if (!closed) {
            seek(end());
            closed = true;
        }
    }
}
