package fr.cnrs.lacito.liftpatchbox.language.parser;

import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.language.SourceScript;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What parsing a script produced: the command model built from what did parse, and
 * every syntax error found on the way.
 *
 * <p>Parsing does not stop at the first syntax error, since Part 2, section 12.3
 * counts every syntax error among the static errors an implementation must report.
 * A failed top-level command of the reference syntax, or a failed command line of
 * the concise one, is left out of {@link #script()}, together with the commands
 * that depend on it, and parsing resumes after it.</p>
 *
 * @param script the script built from the commands that parsed, or {@code null} after
 *               a fatal error: no syntax could be determined, the sigil is malformed,
 *               or the language version is not supported
 * @param errors every syntax error, sorted by source position
 */
public record ParseOutcome(SourceScript script, List<LiftPatchError> errors) {

    /**
     * The order errors are reported in: errors with no position first, then by
     * line, then by column.
     */
    public static final Comparator<LiftPatchError> BY_POSITION = Comparator
        .comparingInt((LiftPatchError e) -> e.position().line())
        .thenComparingInt(e -> e.position().column());

    /**
     * Canonical constructor, sorting the errors by source position. The sort is
     * stable, so errors at one position keep the order they were found in.
     *
     * @param script the script, or {@code null} after a fatal error
     * @param errors the errors, in any order
     */
    public ParseOutcome {
        List<LiftPatchError> sorted = new ArrayList<>(errors);
        sorted.sort(BY_POSITION);
        errors = List.copyOf(sorted);
    }

    /**
     * Whether at least one syntax error was found.
     *
     * @return {@code true} when {@link #errors()} is not empty
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Whether the error was fatal, so that no script could be built at all.
     *
     * @return {@code true} when {@link #script()} is {@code null}
     */
    public boolean isFatal() {
        return script == null;
    }

    /**
     * The script, when it parsed without error.
     *
     * @return the script
     * @throws LiftPatchException carrying every error, sorted by position, when there is any
     */
    public SourceScript orThrow() {
        if (hasErrors()) {
            throw new LiftPatchException(errors);
        }
        return script;
    }
}
