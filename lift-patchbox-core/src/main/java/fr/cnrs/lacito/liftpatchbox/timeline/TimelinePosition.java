package fr.cnrs.lacito.liftpatchbox.timeline;

import fr.cnrs.lacito.liftpatchbox.model.Journal;
import java.util.Optional;

/**
 * A point of a timeline that the dictionary can be brought to.
 *
 * <p>The positions are, in order: the {@link Status#START} of the series, then, for
 * each script in turn, one position after each of its operations. The positions of
 * a script that failed are {@link Status#FAILED_SCRIPT}: they show the state the
 * script had reached, for debugging, but that state was never committed, and the
 * next script starts from the state before the failed one.</p>
 */
public final class TimelinePosition {

    /** What kind of position this is. */
    public enum Status {
        /** The dictionary before the first script. */
        START,
        /** After an operation of a committed script. */
        COMMITTED,
        /** After an operation of a script that failed, which was rolled back. */
        FAILED_SCRIPT
    }

    private final int index;
    private final Status status;
    private final TimelineOperation operation;
    private final int mark;
    private final Journal.Segment segment;
    private final int segmentCount;

    TimelinePosition(
        int index, Status status, TimelineOperation operation,
        int mark, Journal.Segment segment, int segmentCount
    ) {
        this.index = index;
        this.status = status;
        this.operation = operation;
        this.mark = mark;
        this.segment = segment;
        this.segmentCount = segmentCount;
    }

    /**
     * The 0-based index of this position in {@link Timeline#positions()}.
     *
     * @return the index
     */
    public int index() {
        return index;
    }

    /**
     * The kind of position.
     *
     * @return the status
     */
    public Status status() {
        return status;
    }

    /**
     * Whether the state at this position was committed; a GUI greys out the others.
     *
     * @return {@code false} for a position inside a failed script
     */
    public boolean isCommitted() {
        return status != Status.FAILED_SCRIPT;
    }

    /**
     * The operation this position follows.
     *
     * @return the operation, or empty for the start of the series
     */
    public Optional<TimelineOperation> operation() {
        return Optional.ofNullable(operation);
    }

    int mark() {
        return mark;
    }

    Journal.Segment segment() {
        return segment;
    }

    int segmentCount() {
        return segmentCount;
    }

    @Override
    public String toString() {
        return status + "#" + index + (operation == null ? "" : " " + operation.operation().command());
    }
}
