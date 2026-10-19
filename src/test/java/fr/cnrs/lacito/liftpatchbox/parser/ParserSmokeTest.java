package fr.cnrs.lacito.liftpatchbox.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import fr.cnrs.lacito.liftpatchbox.ast.Block;
import fr.cnrs.lacito.liftpatchbox.ast.Command;
import fr.cnrs.lacito.liftpatchbox.ast.Directive;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.ast.Syntax;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A first end-to-end check that both grammars parse and both visitors build. */
class ParserSmokeTest {

    private final ScriptParser parser = new ScriptParser();

    private Script ref(String text) {
        return parser.parse(text, Syntax.REFERENCE, "<test>");
    }

    private Script concise(String text) {
        return parser.parse(text, Syntax.CONCISE, "<test>");
    }

    private static final String DIRECTIVES = """
        language-delete object = "tpi"
        feature-set-create "variant-type"
        feature-set-delete "variant-type"
        feature-set-rename "variant-type" to "variant-kind"
        feature-create "Dialectal Variant" in "variant-type"
        feature-delete "Dialectal Variant" in "variant-type"
        feature-rename "Dialectal" in "variant-type" to "Dialectal Variant"
        field-create "literal-meaning" model = multistring
        trait-create "semantic-domain" model = option-collection range = "semantic-domain"
        field-delete "editorial"
        trait-delete "cv-pattern"
        """;

    @Test
    void parsesEveryDirectiveInBothSyntaxes() {
        for (Script s : List.of(ref(DIRECTIVES), concise(DIRECTIVES))) {
            List<Directive> ds = s.items().stream().map(Directive.class::cast).toList();
            assertEquals(11, ds.size());
            assertInstanceOf(Directive.LanguageDelete.class, ds.get(0));
            Directive.FeatureRename rename = assertInstanceOf(Directive.FeatureRename.class, ds.get(6));
            assertEquals("Dialectal", rename.name());
            assertEquals("variant-type", rename.featureSet());
            assertEquals("Dialectal Variant", rename.newName());
            assertEquals("feature-rename \"Dialectal\" in \"variant-type\" to \"Dialectal Variant\"",
                rename.toString());
            Directive.DefinitionCreate trait =
                assertInstanceOf(Directive.DefinitionCreate.class, ds.get(8));
            assertEquals(Directive.DefinitionKind.TRAIT, trait.kind());
            assertEquals("option-collection", trait.model());
            assertEquals("semantic-domain", trait.range());
            Directive.DefinitionDelete field = assertInstanceOf(Directive.DefinitionDelete.class, ds.get(9));
            assertEquals(Directive.DefinitionKind.FIELD, field.kind());
            assertEquals("trait-delete", ds.get(10).keyword());
        }
    }

    @Test
    void theDirectiveKeywordsRemainNamesOutsideADirective() {
        // `to` is Tongan, and `in`, `model`, `range` are ordinary words elsewhere.
        assertInstanceOf(Command.Create.class, ref(
            "create note(type = \"in\", text@to = \"x\") under entry[form@tww = \"mami\"] as $range")
            .items().get(0));
        assertInstanceOf(Command.Set.class, ref(
            "set text@en = \"x\" on note^model of entry[form@tww = \"memi\"]").items().get(0));
        assertInstanceOf(Command.Create.class, concise("c /model/range x(\"in\")").items().get(0));
    }

    @Test
    void parsesAReferenceCreate() {
        Script s = ref("create sense(gloss@en = \"pig\") under entry[form@tww = \"mami\"]");
        assertEquals(1, s.items().size());
        Command.Create c = assertInstanceOf(Command.Create.class, s.items().get(0));
        assertEquals("sense", c.constructor().componentType());
        assertEquals("entry", ((fr.cnrs.lacito.liftpatchbox.ast.Step.Component)
            c.parent().head()).componentType());
        System.out.println(c);
    }

    @Test
    void parsesAReferenceBlock() {
        Script s = ref("""
            entry[form@tww = "mami"] {
              create sense(gloss@en = "pig") {
                set definition@en = "An animal"
                set value = "Noun" on category
              }
            }
            """);
        Block b = assertInstanceOf(Block.class, s.items().get(0));
        assertEquals(1, b.body().size());
        System.out.println(b);
    }

    @Test
    void parsesAConciseCreate() {
        Script s = concise("c /e[f=\"mami\"] s(g=\"pig\")");
        Command.Create c = assertInstanceOf(Command.Create.class, s.items().get(0));
        assertEquals("sense", c.constructor().componentType());
        System.out.println(c);
    }

    @Test
    void parsesConciseAbbreviations() {
        Script s = concise("c /mami/pig x(\"a mami jefi\")");
        Command.Create c = assertInstanceOf(Command.Create.class, s.items().get(0));
        System.out.println(c);
        assertEquals("example", c.constructor().componentType());
    }

    @Test
    void parsesTheUpsertIdiom() {
        Script s = concise("p /mimi/lizard");
        Block b = assertInstanceOf(Block.class, s.items().get(0));
        System.out.println(b);
    }

    @Test
    void parsesAnIndentedBlock() {
        Script s = concise("""
            c e("mimi")
              c s("lizard")
                s (d@en = "A lizard")
            """);
        Block b = assertInstanceOf(Block.class, s.items().get(0));
        System.out.println(b);
        assertNotNull(b.header());
    }

    @Test
    void ignoresProse() {
        Script s = concise("""
            c est une erreur fréquente
            e e[f="pig"] is prose too, since a bare step is not a path
            c /memi/puppy
            """);
        assertEquals(1, s.items().size());
        System.out.println(s.items().get(0));
    }

    @Test
    void parsesTheWorkedExample() {
        Script s = concise("c e(\"mami\", s(\"pig\", x(\"A mami jefi\", o(\"literal\", \"I shot a pig\"))))");
        System.out.println(s.items().get(0));
    }
}
