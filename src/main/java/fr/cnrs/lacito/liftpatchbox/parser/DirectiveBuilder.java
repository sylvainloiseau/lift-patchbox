package fr.cnrs.lacito.liftpatchbox.parser;

import fr.cnrs.lacito.liftpatchbox.ast.Directive;
import fr.cnrs.lacito.liftpatchbox.ast.Directive.DefinitionKind;
import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import java.util.List;

/**
 * Builds a {@link Directive} from the parts of a directive line.
 *
 * <p>A directive is written identically in both surface syntaxes (Part 1, sections
 * 2.1.1 and 2.1.3 to 2.1.5), and the two grammars give it the same productions; only
 * the classes of their parse trees differ. Each visitor therefore extracts the same
 * parts - the keyword, the language kind, the quoted strings in order, the data model
 * and whether a {@code range} was given - and this class turns them into the one
 * record they denote, so that the mapping is written once.</p>
 */
final class DirectiveBuilder {

    private DirectiveBuilder() {
    }

    /**
     * The directive a line denotes.
     *
     * @param keyword  the directive keyword, for instance {@code "feature-rename"}
     * @param kind     the language kind of a language directive, {@code null} otherwise
     * @param strings  the quoted strings of the line, unquoted, in order
     * @param model    the data model of a {@code field-create} or a {@code trait-create},
     *                 or {@code null} when none is written
     * @param hasRange whether a {@code range = "..."} was written; it is then the last string
     * @param position where the line was written
     * @return the directive
     */
    static Directive build(
        String keyword, LanguageKind kind, List<String> strings, String model, boolean hasRange,
        SourcePosition position
    ) {
        return switch (keyword) {
            case "language-default" -> new Directive.LanguageDefault(kind, strings.get(0), position);
            case "language-create" -> new Directive.LanguageCreate(kind, strings.get(0), position);
            case "language-delete" -> new Directive.LanguageDelete(kind, strings.get(0), position);
            case "feature-set-create" -> new Directive.FeatureSetCreate(strings.get(0), position);
            case "feature-set-delete" -> new Directive.FeatureSetDelete(strings.get(0), position);
            case "feature-set-rename" ->
                new Directive.FeatureSetRename(strings.get(0), strings.get(1), position);
            case "feature-create" ->
                new Directive.FeatureCreate(strings.get(0), strings.get(1), position);
            case "feature-delete" ->
                new Directive.FeatureDelete(strings.get(0), strings.get(1), position);
            case "feature-rename" ->
                new Directive.FeatureRename(strings.get(0), strings.get(1), strings.get(2), position);
            case "field-create", "trait-create" -> new Directive.DefinitionCreate(
                keyword.startsWith("field") ? DefinitionKind.FIELD : DefinitionKind.TRAIT,
                strings.get(0), model, hasRange ? strings.get(strings.size() - 1) : null, position);
            case "field-delete" ->
                new Directive.DefinitionDelete(DefinitionKind.FIELD, strings.get(0), position);
            case "trait-delete" ->
                new Directive.DefinitionDelete(DefinitionKind.TRAIT, strings.get(0), position);
            default -> throw new IllegalStateException("not a directive keyword: " + keyword);
        };
    }
}
