package fr.cnrs.lacito.liftpatchbox.language.error;

import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.ErrorKind;

/**
 * The error codes of Appendix B that only a parser can raise: those of the version
 * pragma, and those of the concise syntax's paths and initializers. Every other code
 * is a {@link fr.cnrs.lacito.liftpatchbox.error.ModelErrorCode}.
 *
 * <p>The constant name is the code as the specification spells it, and is what
 * {@link #id()} returns.</p>
 */
public enum SyntaxErrorCode implements ErrorCode {

    // ---------------------------------------------------------------- static

    /** The version pragma declares a version the implementation does not support. */
    UNSUPPORTED_LANGUAGE_VERSION(ErrorKind.STATIC),
    /** The version pragma carries an attribute other than the three defined ones. */
    UNKNOWN_PRAGMA_ATTRIBUTE(ErrorKind.STATIC),
    /** The pragma requires a metamodel the implementation has not loaded. */
    UNSUPPORTED_METAMODEL(ErrorKind.STATIC),
    /** A concise initializer gives more unnamed arguments than its component type allows. */
    UNNAMED_ARGUMENT_NOT_ALLOWED(ErrorKind.STATIC),
    /** A {@code c} or {@code p} command is written with a path whose created steps are not abbreviated. */
    CONSTRUCTOR_REQUIRED(ErrorKind.STATIC),
    /** The direct target of a concise {@code d}, {@code e} or {@code m} is written as several steps. */
    TARGET_MUST_BE_A_SINGLE_STEP(ErrorKind.STATIC),
    /** A bare word or quoted string is written as a step at a depth for which no abbreviation is defined. */
    ABBREVIATED_STEP_NOT_ALLOWED_HERE(ErrorKind.STATIC);

    private final ErrorKind kind;

    SyntaxErrorCode(ErrorKind kind) {
        this.kind = kind;
    }

    @Override
    public String id() {
        return name();
    }

    @Override
    public ErrorKind kind() {
        return kind;
    }
}
