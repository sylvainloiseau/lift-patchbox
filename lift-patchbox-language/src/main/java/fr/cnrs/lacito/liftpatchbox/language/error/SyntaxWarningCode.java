package fr.cnrs.lacito.liftpatchbox.language.error;

import fr.cnrs.lacito.liftpatchbox.error.WarningCode;

/**
 * The warnings of Appendix B.4 that concern the text of a script.
 */
public enum SyntaxWarningCode implements WarningCode {

    /**
     * Not one line of the document was recognized as a command. Usually a
     * LiftPatchRef script read as a LiftPatchShort document.
     */
    NO_COMMAND_RECOGNIZED;

    @Override
    public String id() {
        return name();
    }
}
