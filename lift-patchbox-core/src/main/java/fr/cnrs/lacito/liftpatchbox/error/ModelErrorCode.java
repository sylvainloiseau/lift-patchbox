package fr.cnrs.lacito.liftpatchbox.error;

/**
 * The error codes of Appendix B that concern the command model itself: every code
 * the static validation, the resolution and the execution of a script can raise,
 * whatever surface syntax the script was written in, or whether it was built in
 * Java rather than parsed.
 *
 * <p>The codes that only a parser can raise (the version pragma, and the rules of
 * the concise syntax's paths and initializers) are declared by the language module.
 * {@code SYNTAX_ERROR} belongs here: a command built in Java can combine parts in a
 * way the grammar forbids, and the validator reports it with the same code as a
 * parser would.</p>
 *
 * <p>The codes of B.3 — the codes a conforming implementation MUST NOT raise — are
 * deliberately absent, so that they cannot be raised by accident. The constant name
 * is the code as the specification spells it, and is what {@link #id()} returns.</p>
 */
public enum ModelErrorCode implements ErrorCode {

    // ---------------------------------------------------------------- static

    /** A property is used on a component type the metamodel does not define it on. */
    PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE(ErrorKind.STATIC),
    /** {@code set}, {@code update} or {@code clear} names a property the metamodel declares read-only. */
    PROPERTY_IS_READ_ONLY(ErrorKind.STATIC),
    /** Two component types are stated to be parent and child where the metamodel does not relate them. */
    ILLEGAL_PARENT(ErrorKind.STATIC),
    /** A language qualifier is written on a scalar property. */
    LANG_KEY_NOT_SUPPORTED_ON_SCALAR(ErrorKind.STATIC),
    /** {@code clear} is written on a multitext with neither a language code nor {@code @*}. */
    MISSING_LANGUAGE_QUALIFIER(ErrorKind.STATIC),
    /** The wildcard {@code @*} is used where it is forbidden. */
    WILDCARD_NOT_ALLOWED(ErrorKind.STATIC),
    /** The same qualified property is initialized or assigned twice in one command. */
    DUPLICATE_PROPERTY(ErrorKind.STATIC),
    /** A multi-language literal is assigned to a qualified property name. */
    QUALIFIER_ON_MULTITEXT_LITERAL(ErrorKind.STATIC),
    /** A selector states the same constraint twice or combines conflicting strategies. */
    DUPLICATE_SELECTOR(ErrorKind.STATIC),
    /** A selector required to select exactly one component uses no valid selection strategy. */
    INCOMPLETE_SELECTOR(ErrorKind.STATIC),
    /** A `create` or `upsert` initializer list omits a property the metamodel declares required. */
    MISSING_REQUIRED_PROPERTY(ErrorKind.STATIC),
    /** An `upsert` initializer list does not cover the whole natural identity property set. */
    MISSING_IDENTITY_PROPERTY(ErrorKind.STATIC),
    /** A unique selector carries a predicate that is not part of its selection strategy. */
    PREDICATE_NOT_ALLOWED_IN_UNIQUE_SELECTOR(ErrorKind.STATIC),
    /** A pseudo-property is used in a filtering selector. */
    PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER(ErrorKind.STATIC),
    /** {@code hn} is used with a command that does not allow it. */
    COMMAND_NOT_ALLOWING_HN(ErrorKind.STATIC),
    /** {@code has} or {@code has-gloss} is used with a command that does not allow it. */
    COMMAND_NOT_ALLOWING_HAS(ErrorKind.STATIC),
    /** {@code hn} is used in a selector that does not also give {@code form}. */
    HN_CANNOT_BE_USED_ALONE(ErrorKind.STATIC),
    /** {@code has-gloss} is used in a selector that does not also give {@code form}. */
    HAS_GLOSS_CANNOT_BE_USED_ALONE(ErrorKind.STATIC),
    /** The value given for {@code hn} is not an integer, or is lower than 1. */
    ILLEGAL_HN(ErrorKind.STATIC),
    /** An ordinal, or the {@code n} of an {@code at index n} clause, is lower than 1. */
    ILLEGAL_ORDINAL(ErrorKind.STATIC),
    /** {@code id} is used in an {@code upsert} initializer list. */
    ID_NOT_ALLOWED_ON_UPSERT(ErrorKind.STATIC),
    /** {@code upsert entry(...)} gives a form with no {@code has} or {@code has-gloss} predicate. */
    UPSERT_ENTRY_WITHOUT_DISAMBIGUATION(ErrorKind.STATIC),
    /** A chain or a path stops before reaching the root. */
    INCOMPLETE_ANCESTOR_CHAIN(ErrorKind.STATIC),
    /** A command gives no parent where one is required. */
    MISSING_PARENT_CLAUSE(ErrorKind.STATIC),
    /** A command tries to create, upsert, delete or move a singleton component type. */
    SINGLETON_CANNOT_BE_CREATED_OR_DELETED(ErrorKind.STATIC),
    /** A singleton step is given a selector where it is a unique selector. */
    SINGLETON_TAKES_NO_SELECTOR(ErrorKind.STATIC),
    /** An ordinal, an {@code at} clause or a {@code move} names a type whose kind is not ordered. */
    COMPONENT_NOT_ORDERED(ErrorKind.STATIC),
    /** A type key names a component type whose kind is not typed. */
    COMPONENT_NOT_TYPED(ErrorKind.STATIC),
    /** An operator is used on a datatype that does not admit it. */
    OPERATOR_NOT_APPLICABLE_TO_DATATYPE(ErrorKind.STATIC),
    /** The component designated as the value of a reference property is neither an entry nor a sense. */
    INVALID_TARGET(ErrorKind.STATIC),
    /** A multiplicity keyword is used where it is not allowed. */
    MULTIPLICITY_NOT_ALLOWED(ErrorKind.STATIC),
    /** A label name visible in the current scope is bound again. */
    DUPLICATE_LABEL(ErrorKind.STATIC),
    /** A {@code $name} is used that no visible command has bound. */
    UNKNOWN_LABEL(ErrorKind.STATIC),
    /** A bare string is assigned to, or compared with, a property of datatype reference. */
    REFERENCE_VALUE_MUST_BE_A_CHAIN(ErrorKind.STATIC),
    /** A {@code move} command appears in a block body. */
    MOVE_NOT_ALLOWED_IN_BLOCK(ErrorKind.STATIC),
    /** A concise command line is indented under a {@code d}. */
    DELETE_CANNOT_BE_AN_ANCHOR(ErrorKind.STATIC),
    /** An {@code upsert}, {@code ensure} or {@code update} appears in the scope of a {@code create} block. */
    CANNOT_UPSERT_ENSURE_OR_UPDATE_IN_CREATION_BLOCK(ErrorKind.STATIC),
    /** {@code clear} targets a scalar property belonging to the natural identity property set. */
    CANNOT_CLEAR_IDENTITY_PROPERTY(ErrorKind.STATIC),
    /** {@code clear} targets a scalar property the metamodel declares required. */
    CANNOT_CLEAR_REQUIRED_PROPERTY(ErrorKind.STATIC),
    /** The script does not parse, or uses a construct the grammar forbids in that position. */
    SYNTAX_ERROR(ErrorKind.STATIC),
    /** A {@code field-create} or {@code trait-create} names an unknown data model, or one of the other kind. */
    ILLEGAL_DATA_MODEL(ErrorKind.STATIC),
    /** A {@code range} is missing with an option data model, or given with another one. */
    ILLEGAL_RANGE(ErrorKind.STATIC),

    // --------------------------------------------------------------- dynamic

    /** A selector, a path, a value chain or an {@code ensure} assertion matches no component. */
    NOT_FOUND(ErrorKind.DYNAMIC),
    /** A selector required to select exactly one component matches several. */
    AMBIGUOUS_REFERENCE(ErrorKind.DYNAMIC),
    /** A command would give two same-type siblings the same natural identity key. */
    CANNOT_CREATE_DUPLICATE(ErrorKind.DYNAMIC),
    /** {@code update} or {@code clear} targets a property that has no value at all. */
    UNSET_PROPERTY(ErrorKind.DYNAMIC),
    /** {@code update} or {@code clear} targets a qualified value that is not set. */
    UNSET_QUALIFIED_PROPERTY(ErrorKind.DYNAMIC),
    /** {@code clear} would leave a required or identity multitext with no value at all. */
    CANNOT_CLEAR_REQUIRED_MULTITEXT(ErrorKind.DYNAMIC),
    /** An ordinal or an {@code at index n} is greater than the upper bound of the sibling list. */
    INDEX_OUT_OF_BOUNDS(ErrorKind.DYNAMIC),
    /** A {@code move} resolves to the position the component already occupies. */
    MOVING_TO_CURRENT_POSITION(ErrorKind.DYNAMIC),
    /** A {@code move} would make a component its own ancestor. */
    SELF_ANCESTOR(ErrorKind.DYNAMIC),
    /** An object language code is not in the dictionary's object language list. */
    NO_SUCH_OBJECT_LANGUAGE(ErrorKind.DYNAMIC),
    /** A meta language code is not in the dictionary's meta language list. */
    NO_SUCH_META_LANGUAGE(ErrorKind.DYNAMIC),
    /** {@code language-create} names a language the dictionary already has. */
    LANGUAGE_ALREADY_EXISTS(ErrorKind.DYNAMIC),
    /** {@code language-delete} names the only language of its kind. */
    CANNOT_DELETE_LAST_LANGUAGE(ErrorKind.DYNAMIC),
    /** {@code language-delete} names a language a text is written in, or the default bound in scope. */
    LANGUAGE_IN_USE(ErrorKind.DYNAMIC),
    /** A declaration directive creates, or renames onto, a name that is already declared. */
    DECLARATION_ALREADY_EXISTS(ErrorKind.DYNAMIC),
    /** A declaration directive names a feature set, a feature or a definition that does not exist. */
    NO_SUCH_DECLARATION(ErrorKind.DYNAMIC),
    /** A feature set, a feature or a definition still in use would be deleted. */
    DECLARATION_IN_USE(ErrorKind.DYNAMIC),
    /** A built-in feature set would be deleted or renamed. */
    DECLARATION_IS_BUILT_IN(ErrorKind.DYNAMIC),
    /**
     * A feature set defined in an external ranges file, which is read-only, would be
     * renamed, have a feature created, deleted or renamed, or gain a value a command names.
     */
    DECLARATION_IS_EXTERNAL(ErrorKind.DYNAMIC);

    private final ErrorKind kind;

    ModelErrorCode(ErrorKind kind) {
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
