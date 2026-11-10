package fr.cnrs.lacito.liftpatchbox.timeline;

import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import java.util.List;

/**
 * What became of one script of a timeline.
 *
 * @param index      its 0-based index in the series
 * @param source     the name of its source
 * @param status     whether it was applied, rejected or rolled back
 * @param plan       its plan document: the static errors of a rejected script, the
 *                   operations and the error of a failed one
 * @param operations its operations on the timeline; none for a rejected script
 */
public record ScriptRun(
    int index,
    String source,
    Status status,
    Plan plan,
    List<TimelineOperation> operations
) {

    /** What became of a script. */
    public enum Status {
        /** Applied and committed. */
        APPLIED,
        /** Refused by static validation: it never ran, and changed nothing. */
        REJECTED,
        /** A command failed while it ran: everything it changed was rolled back. */
        FAILED
    }

    /**
     * Canonical constructor, taking an unmodifiable copy of the operations.
     *
     * @param index      the index in the series
     * @param source     the source name
     * @param status     the status
     * @param plan       the plan document
     * @param operations the operations
     */
    public ScriptRun {
        operations = List.copyOf(operations);
    }
}
