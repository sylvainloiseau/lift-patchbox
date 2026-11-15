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
import java.util.Optional;
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
 * back; the next script starts from what the previous ones committed, unless the
 * recording {@linkplain RecordPolicy#STOP_AT_FIRST_FAILURE stops there}. Labels and
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
 *
 * <p>A timeline can be <em>reused</em> rather than recorded again from a freshly
 * loaded dictionary. {@link #rewind()} brings the dictionary back to the state it had
 * before recording, so that it can be recorded again; {@link #resumeFrom(int, List)}
 * keeps the scripts before a given one as they were recorded and records new ones
 * after them, which an editor applying the scripts at every change of one of them
 * needs: a change in the last script replays that script only.</p>
 */
public final class Timeline implements AutoCloseable {

    private final LiftPatchEngine engine;
    private final LiftDictionary dictionary;
    private final LiftModel model;
    private final Journal journal;
    private final List<ScriptRun> scripts;
    private final List<TimelineOperation> operations;
    private final List<TimelinePosition> positions;
    private final RecordPolicy policy;
    private final int seriesSize;
    private final List<Consumer<TimelinePosition>> listeners = new CopyOnWriteArrayList<>();
    private TimelinePosition current;
    /** After {@link #close()} or {@link #rewind()}. */
    private boolean closed;
    /** After {@link #resumeFrom}: the new timeline owns the dictionary. */
    private boolean superseded;

    private Timeline(
        LiftPatchEngine engine, LiftDictionary dictionary, LiftModel model, Journal journal,
        List<ScriptRun> scripts, List<TimelineOperation> operations,
        List<TimelinePosition> positions, RecordPolicy policy, int seriesSize
    ) {
        this.engine = engine;
        this.dictionary = dictionary;
        this.model = model;
        this.policy = policy;
        this.seriesSize = seriesSize;
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
        return record(engine, dictionary, series, RecordPolicy.CONTINUE);
    }

    /**
     * Apply a series of scripts to a dictionary, recording a timeline of it, and decide what
     * happens after a script that is rejected or fails.
     *
     * @param engine     the engine to validate and run the scripts with
     * @param dictionary the dictionary, left at the end of what was recorded
     * @param series     the scripts, in the order they are applied
     * @param policy     go on after a script that did not apply, or stop there
     * @return the timeline
     */
    public static Timeline record(
        LiftPatchEngine engine, LiftDictionary dictionary, List<Script> series, RecordPolicy policy
    ) {
        Journal journal = new Journal(dictionary);
        journal.keepHistory(true);
        List<ScriptRun> runs = new ArrayList<>();
        List<TimelineOperation> operations = new ArrayList<>();
        List<TimelinePosition> positions = new ArrayList<>();
        positions.add(new TimelinePosition(0, TimelinePosition.Status.START, null, 0, null, 0));
        LiftModel model;
        try {
            model = new LiftModel(dictionary, engine.metamodel(), journal);
            recordInto(engine, model, journal, series, 0, policy, runs, operations, positions);
        } finally {
            // The series is recorded: from now on, the journal only replays.
            journal.close();
        }
        return new Timeline(engine, dictionary, model, journal, runs, operations, positions, policy,
            series.size());
    }

    /**
     * Record scripts after those already recorded, appending to the lists. The journal
     * must be recording, at the state the scripts start from.
     */
    private static void recordInto(
        LiftPatchEngine engine, LiftModel model, Journal journal, List<Script> series, int firstIndex,
        RecordPolicy policy, List<ScriptRun> runs, List<TimelineOperation> operations,
        List<TimelinePosition> positions
    ) {
        for (int j = 0; j < series.size(); j++) {
            int i = firstIndex + j;
            Script script = series.get(j);
            ErrorCollector errors = engine.validate(script);
            if (errors.hasErrors()) {
                runs.add(new ScriptRun(i, script.source(), ScriptRun.Status.REJECTED,
                    Plan.ofStaticErrors(script.source(), script.syntaxName(),
                        engine.metamodel().version(), errors.errors(), errors.warnings()),
                    List.of()));
                if (policy == RecordPolicy.STOP_AT_FIRST_FAILURE) {
                    break;
                }
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
                if (policy == RecordPolicy.STOP_AT_FIRST_FAILURE) {
                    break;
                }
            }
        }
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
     * Bring the dictionary back to the state it had before recording, and close this
     * timeline. The dictionary is then what it was: the same entries, components and
     * header, in the same order, saving to the same document; the components that existed
     * before are the same objects, and those the series created are detached. It can be
     * recorded again, with no reload.
     *
     * <p>Seek listeners are told once, of the start position, and removed. The data of the
     * timeline ({@link #scripts()}, {@link #operations()}, {@link #positions()}) can still
     * be read; seeking, histories and resuming are refused.</p>
     *
     * @throws IllegalStateException if the timeline is closed or superseded
     */
    public void rewind() {
        requireOpen();
        TimelinePosition start = positions.get(0);
        journal.moveTo(start.mark());
        journal.discardAll();
        current = start;
        closed = true;
        listeners.forEach(l -> l.accept(start));
        listeners.clear();
    }

    /**
     * Keep the scripts before {@code scriptIndex} as they were recorded, discard the others,
     * and record {@code newScripts} after them, as the scripts {@code scriptIndex},
     * {@code scriptIndex + 1}… The dictionary is left at the end of the new series.
     *
     * <p>The result is the timeline {@code record} would give for the kept scripts followed
     * by the new ones on the original dictionary: the same runs and plans, the same
     * operations (numbered after the kept ones), the same entries changed and the same
     * dictionary at each position. Labels and {@code with} scopes do not cross scripts, and
     * the default languages are bound for each script from the dictionary as it then is, so
     * a script sees the same input either way. Only the LIFT ids the dictionary model
     * generates for new components differ, as they do between any two recordings.</p>
     *
     * <p>The recording uses this timeline's engine and policy. This timeline is superseded:
     * every later call on it is refused, except reading its data; its seek listeners move to
     * the new one, which tells them once of its end position. The positions of the discarded
     * scripts are not positions of the new timeline.</p>
     *
     * @param scriptIndex the first script recorded again: {@code 0} records the whole new
     *                    series, {@code scripts().size()} appends to the series
     * @param newScripts  the scripts recorded from {@code scriptIndex} on
     * @return the new timeline
     * @throws IllegalArgumentException if {@code scriptIndex} is negative, past the scripts
     *         recorded, or past the script at which a stopping recording stopped
     * @throws IllegalStateException    if the timeline is closed or superseded
     */
    public Timeline resumeFrom(int scriptIndex, List<Script> newScripts) {
        requireOpen();
        int limit = stoppedAt().map(ScriptRun::index).orElse(scripts.size());
        if (scriptIndex < 0 || scriptIndex > limit) {
            throw new IllegalArgumentException("cannot resume from script " + scriptIndex + ": "
                + (stoppedAt().isPresent()
                    ? "the recording stopped at script " + limit
                    : scripts.size() + " script(s) are recorded"));
        }
        // The state the new scripts start from: after the last committed position of the
        // kept scripts, or the start.
        TimelinePosition from = positions.get(0);
        int keptPositions = 1;
        for (int p = 1; p < positions.size(); p++) {
            TimelinePosition position = positions.get(p);
            if (position.operation().orElseThrow().scriptIndex() >= scriptIndex) {
                break;
            }
            keptPositions = p + 1;
            if (position.isCommitted()) {
                from = position;
            }
        }
        journal.moveTo(from.mark());
        journal.truncate(from.mark());

        List<ScriptRun> runs = new ArrayList<>(scripts.subList(0, scriptIndex));
        List<TimelineOperation> ops = new ArrayList<>();
        for (TimelineOperation op : operations) {
            if (op.scriptIndex() < scriptIndex) {
                ops.add(op);
            }
        }
        List<TimelinePosition> kept = new ArrayList<>(positions.subList(0, keptPositions));
        journal.resume(dictionary);
        try {
            recordInto(engine, model, journal, newScripts, scriptIndex, policy, runs, ops, kept);
        } finally {
            journal.close();
        }
        Timeline next = new Timeline(engine, dictionary, model, journal, runs, ops, kept, policy,
            scriptIndex + newScripts.size());
        superseded = true;
        next.listeners.addAll(listeners);
        listeners.clear();
        TimelinePosition end = next.current;
        next.listeners.forEach(l -> l.accept(end));
        return next;
    }

    /**
     * Whether the timeline can still seek, rewind and resume: false after {@link #close()},
     * {@link #rewind()} and {@link #resumeFrom}.
     *
     * @return whether the timeline is open
     */
    public boolean isOpen() {
        return !closed && !superseded;
    }

    private void requireOpen() {
        if (superseded) {
            throw new IllegalStateException("the timeline was superseded by resumeFrom");
        }
        if (closed) {
            throw new IllegalStateException("the timeline is closed");
        }
    }

    /**
     * What the recording did after a script that did not apply.
     *
     * @return the policy given to {@link #record(LiftPatchEngine, LiftDictionary, List, RecordPolicy)}
     */
    public RecordPolicy policy() {
        return policy;
    }

    /**
     * The script at which a {@link RecordPolicy#STOP_AT_FIRST_FAILURE} recording stopped: the
     * first that was rejected or failed. The scripts after it are not in {@link #scripts()}.
     *
     * @return the run of that script; empty when every script applied, or when the recording
     *         went on after failures
     */
    public Optional<ScriptRun> stoppedAt() {
        if (policy != RecordPolicy.STOP_AT_FIRST_FAILURE || scripts.isEmpty()) {
            return Optional.empty();
        }
        ScriptRun last = scripts.get(scripts.size() - 1);
        return last.status() == ScriptRun.Status.APPLIED ? Optional.empty() : Optional.of(last);
    }

    /**
     * The number of scripts in the series given, recorded or not.
     *
     * @return the size of the series
     */
    public int seriesSize() {
        return seriesSize;
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
        requireOpen();
        if (position.index() >= positions.size() || positions.get(position.index()) != position) {
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
        if (superseded) {
            throw new IllegalStateException("the timeline was superseded by resumeFrom");
        }
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
     * Bring the dictionary back to the end of the series, and allow no further seek. Nothing
     * is done for a timeline already closed, rewound or superseded.
     */
    @Override
    public void close() {
        if (!closed && !superseded) {
            seek(end());
            closed = true;
        }
    }
}
