package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourceRange;

/**
 * One element of the assignment list of a {@code set} or an {@code update}
 * (Part 2, section 9.1.1).
 *
 * <p>An assignment list is surface sugar with no semantics of its own: a command
 * carrying one is defined as being exactly equivalent to the sequence of the
 * corresponding single-property commands, with the {@code on} clause resolved
 * once and shared by all of them.</p>
 *
 * @param property the property written
 * @param value    the value assigned
 * @param range    where the assignment was written
 */
public record Assignment(PropertyRef property, Value value, SourceRange range) {

    /**
     * An assignment at an unknown position.
     *
     * @param property the property written
     * @param value    the value assigned
     * @return the assignment
     */
    public static Assignment of(PropertyRef property, Value value) {
        return new Assignment(property, value, SourceRange.UNKNOWN);
    }

    @Override
    public String toString() {
        return property + " = " + value;
    }
}
