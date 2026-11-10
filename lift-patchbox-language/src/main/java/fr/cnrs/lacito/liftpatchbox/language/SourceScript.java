package fr.cnrs.lacito.liftpatchbox.language;

import fr.cnrs.lacito.liftpatchbox.language.error.SyntaxWarningCode;

import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchWarning;
import java.util.List;

/**
 * A script as a parser read it: the command model, and what the text said about
 * itself.
 *
 * <p>The {@link Script} is all the core needs to validate, plan or apply the
 * commands. The rest belongs to the surface syntax: the version pragma, which the
 * parser has already checked, the syntax the text was read with, and how many
 * lines were recognized as commands, which is what the
 * {@link SyntaxWarningCode#NO_COMMAND_RECOGNIZED} warning is based on.</p>
 *
 * @param script          the command model
 * @param pragma          the version pragma, or {@code null} when the script declares none
 * @param syntax          the surface syntax the script was parsed with
 * @param recognizedLines how many lines were recognized as commands
 */
public record SourceScript(Script script, Pragma pragma, Syntax syntax, int recognizedLines) {

    /**
     * The name of the script's source.
     *
     * @return the source name
     */
    public String source() {
        return script.source();
    }

    /**
     * The warnings the text itself calls for, reported ahead of those of execution.
     *
     * @return {@code NO_COMMAND_RECOGNIZED} when not one line was recognized as a
     *         command, and nothing otherwise
     */
    public List<LiftPatchWarning> warnings() {
        if (recognizedLines != 0) {
            return List.of();
        }
        return List.of(LiftPatchWarning.document(SyntaxWarningCode.NO_COMMAND_RECOGNIZED,
            "not one line of " + script.source() + " was recognized as a command; if this is "
                + "a reference-syntax script, declare it with %liftpatch 1.1 "
                + "syntax=\"LiftPatchRef\" or pass the syntax explicitly"));
    }
}
