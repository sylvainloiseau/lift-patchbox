package fr.cnrs.lacito.liftpatchbox.metamodel;

import java.util.Optional;

/**
 * The data model of a field or trait definition, as {@code field-create} and
 * {@code trait-create} name it (Part 1, section 2.1.5): LIFT's own names, which the
 * dictionary model reads as they are.
 *
 * <p>The validator checks a declaration against this list without consulting the
 * dictionary: whether the model exists, whether it suits the kind of definition, and
 * whether it needs a {@code range}. Those are static errors.</p>
 */
public enum DataModel {

    /** A trait whose value is a string; the default for a trait. */
    STRING("string", false, true, false),
    /** A trait whose value is an integer. */
    INTEGER("integer", false, true, false),
    /** A trait whose value is a date and time. */
    DATETIME("datetime", false, true, false),
    /** A trait whose value is one feature of its range. */
    OPTION("option", false, true, true),
    /** A trait whose value is a set of features of its range. */
    OPTION_COLLECTION("option-collection", false, true, true),
    /** A trait whose value is a sequence of features of its range. */
    OPTION_SEQUENCE("option-sequence", false, true, true),
    /** A field holding a text in several languages; the default for a field. */
    MULTITEXT("multitext", true, false, false),
    /** A field holding a plain string in several languages. */
    MULTISTRING("multistring", true, false, false);

    private final String name;
    private final boolean forFields;
    private final boolean forTraits;
    private final boolean needsRange;

    DataModel(String name, boolean forFields, boolean forTraits, boolean needsRange) {
        this.name = name;
        this.forFields = forFields;
        this.forTraits = forTraits;
        this.needsRange = needsRange;
    }

    /**
     * The model a declaration names.
     *
     * @param name the name as written, for instance {@code "option-collection"}
     * @return the model, or empty when the name is none of LIFT's
     */
    public static Optional<DataModel> named(String name) {
        for (DataModel m : values()) {
            if (m.name.equals(name)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    /**
     * The name of the model, as written and as LIFT writes it.
     *
     * @return for instance {@code "option"}
     */
    public String jsonName() {
        return name;
    }

    /**
     * Whether a field definition may take this model.
     *
     * @return {@code true} for {@code multitext} and {@code multistring}
     */
    public boolean forFields() {
        return forFields;
    }

    /**
     * Whether a trait definition may take this model.
     *
     * @return {@code true} for every model but the two of fields
     */
    public boolean forTraits() {
        return forTraits;
    }

    /**
     * Whether the model takes its values from a feature set, given by {@code range}.
     *
     * @return {@code true} for the three {@code option} models
     */
    public boolean needsRange() {
        return needsRange;
    }
}
