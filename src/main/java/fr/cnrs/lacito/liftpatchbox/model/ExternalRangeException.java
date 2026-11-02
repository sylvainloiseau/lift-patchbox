package fr.cnrs.lacito.liftpatchbox.model;

/**
 * A command or a directive would change a feature set defined in an external ranges
 * file (a {@code .lift-ranges} file the dictionary refers to): add a value to it, or
 * change what it holds.
 *
 * <p>Such a set is read-only in the dictionary model, since that file is never
 * written when the dictionary is saved. The engine reports it as
 * {@code DECLARATION_IS_EXTERNAL} (Part 1, section 2.1.4).</p>
 */
public final class ExternalRangeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String featureSet;
    private final String value;

    /**
     * An exception naming the external feature set and, if any, the value it lacks.
     *
     * @param featureSet the id of the external feature set
     * @param value      the value it does not hold, or {@code null} for a change of the
     *                   set itself
     */
    public ExternalRangeException(String featureSet, String value) {
        super(value == null
            ? "the feature set `" + featureSet + "` is defined in an external ranges file, "
                + "which is not written when the dictionary is saved, and cannot be changed"
            : "the feature set `" + featureSet + "` is defined in an external ranges file, "
                + "which does not hold the value `" + value + "` and is not written when the "
                + "dictionary is saved");
        this.featureSet = featureSet;
        this.value = value;
    }

    /**
     * The external feature set.
     *
     * @return its id
     */
    public String featureSet() {
        return featureSet;
    }

    /**
     * The value the set does not hold.
     *
     * @return its id, or {@code null} for a change of the set itself
     */
    public String value() {
        return value;
    }
}
