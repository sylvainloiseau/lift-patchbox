package fr.cnrs.lacito.liftpatchbox.ast;

import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;

/**
 * A directive: a line that manages the languages or the declarations of the
 * dictionary rather than a component (Part 1, sections 2.1.1 and 2.1.3 to 2.1.5).
 *
 * <p>{@code language-default} binds a name for the rest of a scope and changes
 * nothing, so it is not an operation and takes no index in a plan document. Every
 * other directive changes the dictionary: it is an operation, carries one effect, is
 * part of the script's transaction, and is not scoped.</p>
 *
 * <p>Both surface syntaxes write a directive identically, and produce the same
 * records.</p>
 */
public sealed interface Directive extends Item
    permits Directive.LanguageDefault, Directive.LanguageCreate, Directive.LanguageDelete,
            Directive.FeatureSetCreate, Directive.FeatureSetDelete, Directive.FeatureSetRename,
            Directive.FeatureCreate, Directive.FeatureDelete, Directive.FeatureRename,
            Directive.DefinitionCreate, Directive.DefinitionDelete {

    /**
     * Where the directive was written.
     *
     * @return the position
     */
    SourcePosition position();

    /**
     * The keyword of the directive, which plan mode reports as the operation's verb.
     *
     * @return for instance {@code "feature-create"}
     */
    String keyword();

    /**
     * Whether this directive is an operation in the sense of Part 2, section 12.4.1.
     *
     * @return {@code true} for every directive but {@code language-default}
     */
    default boolean isOperation() {
        return true;
    }

    /** Which kind of definition a {@code field-*} or {@code trait-*} directive is about. */
    enum DefinitionKind {
        FIELD("field"),
        TRAIT("trait");

        private final String jsonName;

        DefinitionKind(String jsonName) {
            this.jsonName = jsonName;
        }

        /**
         * The name used in keywords and in the {@code declarationType} of an effect.
         *
         * @return {@code "field"} or {@code "trait"}
         */
        public String jsonName() {
            return jsonName;
        }
    }

    // ===================================================================
    // Languages (section 2.1.3)
    // ===================================================================

    /**
     * {@code language-default K = "code"}.
     *
     * @param kind     the language kind bound
     * @param language the language code
     * @param position where the directive was written
     */
    record LanguageDefault(LanguageKind kind, String language, SourcePosition position)
        implements Directive {

        @Override
        public String keyword() {
            return "language-default";
        }

        @Override
        public boolean isOperation() {
            return false;
        }

        @Override
        public String toString() {
            return keyword() + " " + kind.jsonName() + " = " + quote(language);
        }
    }

    /**
     * {@code language-create K = "code"}.
     *
     * @param kind     the language kind created
     * @param language the language code
     * @param position where the directive was written
     */
    record LanguageCreate(LanguageKind kind, String language, SourcePosition position)
        implements Directive {

        @Override
        public String keyword() {
            return "language-create";
        }

        @Override
        public String toString() {
            return keyword() + " " + kind.jsonName() + " = " + quote(language);
        }
    }

    /**
     * {@code language-delete K = "code"}.
     *
     * @param kind     the language kind
     * @param language the language code
     * @param position where the directive was written
     */
    record LanguageDelete(LanguageKind kind, String language, SourcePosition position)
        implements Directive {

        @Override
        public String keyword() {
            return "language-delete";
        }

        @Override
        public String toString() {
            return keyword() + " " + kind.jsonName() + " = " + quote(language);
        }
    }

    // ===================================================================
    // Feature sets and features (section 2.1.4)
    // ===================================================================

    /**
     * {@code feature-set-create "name"}.
     *
     * @param name     the id of the new feature set
     * @param position where the directive was written
     */
    record FeatureSetCreate(String name, SourcePosition position) implements Directive {

        @Override
        public String keyword() {
            return "feature-set-create";
        }

        @Override
        public String toString() {
            return keyword() + " " + quote(name);
        }
    }

    /**
     * {@code feature-set-delete "name"}.
     *
     * @param name     the id of the feature set
     * @param position where the directive was written
     */
    record FeatureSetDelete(String name, SourcePosition position) implements Directive {

        @Override
        public String keyword() {
            return "feature-set-delete";
        }

        @Override
        public String toString() {
            return keyword() + " " + quote(name);
        }
    }

    /**
     * {@code feature-set-rename "name" to "newName"}.
     *
     * @param name     the current id of the feature set
     * @param newName  its new id
     * @param position where the directive was written
     */
    record FeatureSetRename(String name, String newName, SourcePosition position)
        implements Directive {

        @Override
        public String keyword() {
            return "feature-set-rename";
        }

        @Override
        public String toString() {
            return keyword() + " " + quote(name) + " to " + quote(newName);
        }
    }

    /**
     * {@code feature-create "name" in "featureSet"}.
     *
     * @param name       the id of the new feature
     * @param featureSet the feature set it is created in
     * @param position   where the directive was written
     */
    record FeatureCreate(String name, String featureSet, SourcePosition position)
        implements Directive {

        @Override
        public String keyword() {
            return "feature-create";
        }

        @Override
        public String toString() {
            return keyword() + " " + quote(name) + " in " + quote(featureSet);
        }
    }

    /**
     * {@code feature-delete "name" in "featureSet"}.
     *
     * @param name       the id of the feature
     * @param featureSet the feature set it belongs to
     * @param position   where the directive was written
     */
    record FeatureDelete(String name, String featureSet, SourcePosition position)
        implements Directive {

        @Override
        public String keyword() {
            return "feature-delete";
        }

        @Override
        public String toString() {
            return keyword() + " " + quote(name) + " in " + quote(featureSet);
        }
    }

    /**
     * {@code feature-rename "name" in "featureSet" to "newName"}.
     *
     * @param name       the current id of the feature
     * @param featureSet the feature set it belongs to
     * @param newName    its new id
     * @param position   where the directive was written
     */
    record FeatureRename(String name, String featureSet, String newName, SourcePosition position)
        implements Directive {

        @Override
        public String keyword() {
            return "feature-rename";
        }

        @Override
        public String toString() {
            return keyword() + " " + quote(name) + " in " + quote(featureSet)
                + " to " + quote(newName);
        }
    }

    // ===================================================================
    // Field and trait definitions (section 2.1.5)
    // ===================================================================

    /**
     * {@code field-create "name" [model = M]} or
     * {@code trait-create "name" [model = M] [range = "featureSet"]}.
     *
     * @param kind     field or trait
     * @param name     the name of the new definition
     * @param model    the data model as written, or {@code null} for the default of the kind
     * @param range    the feature set of an option model, or {@code null}
     * @param position where the directive was written
     */
    record DefinitionCreate(
        DefinitionKind kind, String name, String model, String range, SourcePosition position
    ) implements Directive {

        @Override
        public String keyword() {
            return kind.jsonName() + "-create";
        }

        @Override
        public String toString() {
            return keyword() + " " + quote(name)
                + (model == null ? "" : " model = " + model)
                + (range == null ? "" : " range = " + quote(range));
        }
    }

    /**
     * {@code field-delete "name"} or {@code trait-delete "name"}.
     *
     * @param kind     field or trait
     * @param name     the name of the definition
     * @param position where the directive was written
     */
    record DefinitionDelete(DefinitionKind kind, String name, SourcePosition position)
        implements Directive {

        @Override
        public String keyword() {
            return kind.jsonName() + "-delete";
        }

        @Override
        public String toString() {
            return keyword() + " " + quote(name);
        }
    }

    /** A string as a double-quoted literal, with the two escapes of Part 1, section 1.2. */
    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
