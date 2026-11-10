package fr.cnrs.lacito.liftpatchbox.error;

/**
 * The warnings of Appendix B.4 that execution reports, whatever the script was
 * written in.
 */
public enum ModelWarningCode implements WarningCode {

    /**
     * A command marked {@code each}, {@code all} or {@code *} whose target step
     * matched no component.
     */
    COMMAND_APPLIES_TO_NO_COMPONENT;

    @Override
    public String id() {
        return name();
    }
}
