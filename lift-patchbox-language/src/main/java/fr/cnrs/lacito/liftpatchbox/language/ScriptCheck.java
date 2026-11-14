package fr.cnrs.lacito.liftpatchbox.language;

import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchWarning;
import fr.cnrs.lacito.liftpatchbox.language.parser.ParseOutcome;
import java.util.ArrayList;
import java.util.List;

/**
 * Every syntax error and every static error of a script, found in one call and
 * returned rather than thrown: what {@link LiftPatchBox#check} reports.
 *
 * <p>Static validation runs on the commands that parsed, so that a script with a
 * syntax error still has the static errors of its other commands reported. Two
 * rules keep that from reporting what is not there, and apply only when there is
 * at least one syntax error:</p>
 *
 * <ul>
 *   <li>no {@code UNKNOWN_LABEL} is reported for a label that the script binds
 *       somewhere with {@code as $name}: the binding command may be one that failed
 *       to parse;</li>
 *   <li>the operation index of the static errors is {@code 0} (unknown), since the
 *       commands left out shift the numbering. Their source position is exact, and
 *       is what identifies them.</li>
 * </ul>
 *
 * <p>For a script with no syntax error, {@link #staticErrors()} is exactly what
 * {@link LiftPatchBox#validate} reports.</p>
 *
 * @param script       the script built from the commands that parsed, or
 *                     {@code null} after a fatal error (see {@link ParseOutcome})
 * @param syntaxErrors every syntax error, sorted by position
 * @param staticErrors every static error of the commands that parsed, sorted by position
 * @param warnings     the warnings of the text and of the validation
 */
public record ScriptCheck(
    SourceScript script,
    List<LiftPatchError> syntaxErrors,
    List<LiftPatchError> staticErrors,
    List<LiftPatchWarning> warnings
) {

    /**
     * Canonical constructor, sorting the errors by position and taking
     * unmodifiable copies.
     *
     * @param script       the script, or {@code null} after a fatal error
     * @param syntaxErrors the syntax errors
     * @param staticErrors the static errors
     * @param warnings     the warnings
     */
    public ScriptCheck {
        syntaxErrors = sorted(syntaxErrors);
        staticErrors = sorted(staticErrors);
        warnings = List.copyOf(warnings);
    }

    private static List<LiftPatchError> sorted(List<LiftPatchError> errors) {
        List<LiftPatchError> copy = new ArrayList<>(errors);
        copy.sort(ParseOutcome.BY_POSITION);
        return List.copyOf(copy);
    }

    /**
     * Whether the script has no syntax error and no static error.
     *
     * @return {@code true} when both lists are empty
     */
    public boolean isOk() {
        return syntaxErrors.isEmpty() && staticErrors.isEmpty();
    }

    /**
     * The syntax errors and the static errors together.
     *
     * @return both lists merged, sorted by position
     */
    public List<LiftPatchError> errors() {
        List<LiftPatchError> all = new ArrayList<>(syntaxErrors);
        all.addAll(staticErrors);
        return sorted(all);
    }
}
