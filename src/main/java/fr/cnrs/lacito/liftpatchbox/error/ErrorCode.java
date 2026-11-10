package fr.cnrs.lacito.liftpatchbox.error;

/**
 * An error code of Appendix B, with its kind.
 *
 * <p>Each module declares its codes as an {@code enum} implementing this interface:
 * {@link ModelErrorCode} for the command model (validation, resolution, execution),
 * and the language module for the codes only a parser can raise. A code is
 * identified by {@link #id()}, the name the specification gives it, which is what
 * plan documents and conformance cases carry; two codes with the same id never
 * coexist.</p>
 */
public interface ErrorCode {

    /**
     * The code as the specification spells it, such as {@code NOT_FOUND}.
     *
     * @return the identifier
     */
    String id();

    /**
     * The kind of this error code.
     *
     * @return {@link ErrorKind#STATIC} when the error depends only on the script
     *         and the metamodel, {@link ErrorKind#DYNAMIC} when it depends on the
     *         state of the dictionary
     */
    ErrorKind kind();

    /**
     * Whether this code denotes a static error.
     *
     * @return {@code true} if and only if {@link #kind()} is {@link ErrorKind#STATIC}
     */
    default boolean isStatic() {
        return kind() == ErrorKind.STATIC;
    }
}
