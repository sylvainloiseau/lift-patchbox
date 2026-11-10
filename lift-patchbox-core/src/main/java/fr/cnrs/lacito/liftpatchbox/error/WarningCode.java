package fr.cnrs.lacito.liftpatchbox.error;

/**
 * A warning code of Appendix B.4.
 *
 * <p>A warning reports a situation that is legal, changes nothing, and is more
 * often a mistake than an intention. It never stops a script and never changes
 * the status of a plan document.</p>
 *
 * <p>Each module declares its warnings as an {@code enum} implementing this
 * interface: {@link ModelWarningCode} for execution, and the language module for
 * the warnings about the script text. A warning is identified by {@link #id()}, the
 * name the specification gives it.</p>
 */
public interface WarningCode {

    /**
     * The code as the specification spells it, such as {@code COMMAND_APPLIES_TO_NO_COMPONENT}.
     *
     * @return the identifier
     */
    String id();
}
