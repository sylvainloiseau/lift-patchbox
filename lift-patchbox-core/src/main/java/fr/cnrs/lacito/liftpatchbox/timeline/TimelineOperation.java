package fr.cnrs.lacito.liftpatchbox.timeline;

import fr.cnrs.lacito.liftpatchbox.engine.Operation;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import java.util.Set;

/**
 * One operation of a timeline.
 *
 * @param index       its 1-based index across the whole series of scripts
 * @param scriptIndex the 0-based index of its script in the series
 * @param operation   the operation as the script's plan reports it: its text, its
 *                    source position, its target and its effects
 * @param entryIds    the ids of the entries whose subtree the operation changed
 * @param committed   {@code false} for an operation of a script that failed, whose
 *                    changes were rolled back
 */
public record TimelineOperation(
    int index,
    int scriptIndex,
    Operation operation,
    Set<String> entryIds,
    boolean committed
) {

    /**
     * Canonical constructor, taking an unmodifiable copy of the ids.
     *
     * @param index       the index across the series
     * @param scriptIndex the index of the script
     * @param operation   the operation
     * @param entryIds    the ids of the entries it changed
     * @param committed   whether its script was committed
     */
    public TimelineOperation {
        entryIds = Set.copyOf(entryIds);
    }

    /**
     * The error that stopped the script at this operation.
     *
     * @return the error, or {@code null} for an operation that succeeded
     */
    public LiftPatchError error() {
        return operation.error();
    }
}
