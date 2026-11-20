package fr.cnrs.lacito.liftpatchbox.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftpatchbox.language.Syntax;
import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import fr.cnrs.lacito.liftpatchbox.error.ModelErrorCode;
import fr.cnrs.lacito.liftpatchbox.language.error.SyntaxErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The LiftPatchShort concise syntax (Part 3): its recognition rule, its
 * abbreviations, its axes, its indented blocks and the {@code p /path} idiom.
 *
 * <p>The concise syntax has no semantics of its own: every command it expresses
 * expands into one reference-syntax command. The tests therefore check the
 * expansion by checking its effects, and several of them write the same operation
 * in both syntaxes and assert that the two agree.</p>
 */
@DisplayName("Concise syntax")
class ConciseSyntaxTest extends PatchTestSupport {

    @BeforeEach
    void setUp() {
        newDictionary();
    }

    @Nested
    @DisplayName("recognizing a command line")
    class Recognition {

        @Test
        @DisplayName("a prose line beginning with a command letter is prose")
        void proseIsProse() {
            assertEffects(
                applyConcise("""
                    c est une erreur fréquente
                    e e[f="pig"] is prose too, since a bare step is not a path
                    c /memi/puppy
                    """),
                "created:sense@2", "set:gloss@en=puppy");
        }

        @Test
        @DisplayName("a declaration directive is recognized by its keyword and a quoted name")
        void recognizesADeclarationDirective() {
            assertEffects(
                applyConcise("""
                    feature-create is how a feature is declared, but this line is prose
                    feature-set-create "variant-type"
                    feature-create "Dialectal Variant" in "variant-type"
                    trait-create "variant-type" model = option range = "variant-type"
                    """),
                "declared:feature-set:variant-type",
                "declared:feature:variant-type/Dialectal Variant",
                "declared:trait:variant-type");
        }

        @Test
        @DisplayName("a declaration directive may be indented under a command, and outlives it")
        void anIndentedDeclarationIsNotScoped() {
            assertEffects(
                applyConcise("""
                    c /memi n("general", "checked")
                      feature-create "draft" in "note-type"
                    c /memi n("draft", "to check")
                    """),
                "created:note@null", "set:type=general", "set:text@en=checked",
                "declared:feature:note-type/draft",
                "created:note@null", "set:type=draft", "set:text@en=to check");
        }

        @Test
        @DisplayName("a line beginning with a backslash is never a command")
        void aBackslashEscapesALine() {
            Plan plan = applyConcise("\\c /memi/puppy\n");
            assertEffects(plan);
            assertEquals("NO_COMMAND_RECOGNIZED", plan.warnings().get(0).code().id());
        }

        @Test
        @DisplayName("a trailing comment is stripped, and an ordinal is not one")
        void stripsATrailingComment() {
            assertEffects(
                applyConcise("d /e[f=\"mami\", hn=1]!s[g=\"pig\"] x#1   # the first example\n"),
                "deleted:example@1");
        }

        @Test
        @DisplayName("a declared sigil decides which lines are commands")
        void aSigilDecidesWhichLinesAreCommands() {
            assertEffects(
                applyConcise("""
                    %liftpatch 1.1 sigil=">"
                    c /memi/puppy is prose here, since the line carries no sigil
                    > c /memi/puppy
                    """),
                "created:sense@2", "set:gloss@en=puppy");
        }

        @Test
        @DisplayName("a line that matches the rule but does not parse is an error, never prose")
        void aMistypedCommandIsAnError() {
            refusedConcise(ModelErrorCode.SYNTAX_ERROR, "c /e[f=\"memi\" s(g=\"puppy\")\n");
        }

        @Test
        @DisplayName("a singleton step followed by a property list is a command on an indented line")
        void anIndentedSingletonStepIsACommand() {
            // Recognized, and so refused rather than silently dropped as prose.
            refusedConcise(ModelErrorCode.PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE,
                """
                c /memi s("cat")
                  s c (category = "Noun")
                """);
        }

        @Test
        @DisplayName("a singleton step followed by a property list is prose at top level")
        void aTopLevelSingletonStepIsProse() {
            assertEffects(
                applyConcise("""
                    s c (v = "Noun") is prose, since only a block supplies the parent
                    c /memi/puppy
                    """),
                "created:sense@2", "set:gloss@en=puppy");
        }

        @Test
        @DisplayName("a bare word that names no singleton is not a relative path")
        void anIndentedBareWordIsProse() {
            assertEffects(
                applyConcise("""
                    c /memi s("cat")
                      s cat (is prose: `cat` names no singleton)
                    """),
                "created:sense@2", "set:gloss@en=cat");
        }
    }

    @Nested
    @DisplayName("paths and axes")
    class PathsAndAxes {

        @Test
        @DisplayName("every written link of a parent path is existential")
        void everyWrittenLinkIsExistential() {
            assertEffects(applyConcise("s /e[f=\"mami\"]/s[g=\"taro\"]/c (v = \"Noun\")"),
                "set:value=Noun");
        }

        @Test
        @DisplayName("the link between the path and the target is strict")
        void theLinkToTheTargetIsStrict() {
            refusedConcise(ModelErrorCode.AMBIGUOUS_REFERENCE, "d /e[f=\"mami\"] s[g=\"taro\"]");
        }

        @Test
        @DisplayName("the strict axis is written ! and admits a pseudo-property above it")
        void theStrictAxisAdmitsAPseudoProperty() {
            assertEffects(applyConcise("d /e[f=\"mami\", hn=1]!s[g=\"pig\"] x#1"),
                "deleted:example@1");
        }

        @Test
        @DisplayName("an existential path over two homophones is ambiguous")
        void anExistentialPathOverHomophonesIsAmbiguous() {
            refusedConcise(ModelErrorCode.AMBIGUOUS_REFERENCE, "d /mami/pig x#1");
        }

        @Test
        @DisplayName("the direct target of a delete is exactly one step")
        void theTargetIsOneStep() {
            refusedConcise(SyntaxErrorCode.TARGET_MUST_BE_A_SINGLE_STEP, "d /mami/pig");
        }

        @Test
        @DisplayName("a property command joins the component it writes on to its path")
        void aPropertyCommandJoinsItsComponentToThePath() {
            applyConcise("c /memi n(y=\"general\", t@en = \"recorded at Yakoro\")");
            assertEffects(
                applyConcise("u /memi!n^general (t@en = \"recorded at Yakoro, 2025\")"),
                "replaced:text@en:recorded at Yakoro->recorded at Yakoro, 2025");
        }

        @Test
        @DisplayName("a property command does not write its component apart from the path")
        void aPropertyCommandRefusesASeparateStep() {
            applyConcise("c /memi n(y=\"general\", t@en = \"recorded at Yakoro\")");
            refusedConcise(ModelErrorCode.SYNTAX_ERROR,
                "u /memi n^general (t@en = \"recorded at Yakoro, 2025\")");
        }

        @Test
        @DisplayName("an existential join below a label is the strict one")
        void anExistentialJoinBelowALabelIsStrict() {
            assertEffects(
                applyConcise("""
                    e /e[f="mami", hn=1] s[g="pig"] as $pig

                    s $pig/x#1 (t@tww = "a mami jefu")
                    s $pig/c (v = "Verb")
                    """),
                "replaced:text@tww:a mami jefi->a mami jefu",
                "replaced:value:Noun->Verb");
        }

        @Test
        @DisplayName("an existential join below a label is the strict one in the reference syntax")
        void anExistentialJoinBelowALabelIsStrictInTheReferenceSyntax() {
            assertEffects(
                apply("""
                    ensure sense[gloss@en = "pig"] under entry[form@tww = "mami", hn = 1] as $pig
                    set text@tww = "a mami jefu" on example#1 within $pig
                    """),
                "replaced:text@tww:a mami jefi->a mami jefu");
        }

        @Test
        @DisplayName("a label is still refused as a filtering selector")
        void aLabelIsRefusedAsAFilter() {
            refused(ModelErrorCode.PSEUDO_PROPERTY_NOT_ALLOWED_IN_FILTER, """
                ensure entry[form@tww = "memi"] as $memi
                delete each sense[has $memi] under entry[form@tww = "mami", hn = 1]
                """);
        }
    }

    @Nested
    @DisplayName("abbreviated steps")
    class AbbreviatedSteps {

        @Test
        @DisplayName("depth 1 is an entry designated by its form")
        void depthOneIsAnEntryForm() {
            assertEffects(applyConcise("d /memi"), "deleted:entry@null");
        }

        @Test
        @DisplayName("depth 2 is a sense designated by its gloss")
        void depthTwoIsASenseGloss() {
            assertEffects(applyConcise("e /memi dog"));
        }

        @Test
        @DisplayName("depth 3 is an example designated by its text")
        void depthThreeIsAnExampleText() {
            assertEffects(
                applyConcise("d /e[f=\"mami\", hn=1]!s[g=\"pig\"] \"a mami jefi\""),
                "deleted:example@1");
        }

        @Test
        @DisplayName("there is no abbreviation below the third depth")
        void thereIsNoAbbreviationBelowTheThird() {
            refusedConcise(SyntaxErrorCode.ABBREVIATED_STEP_NOT_ALLOWED_HERE,
                "d /mami/pig/\"a mami jefi\" foo");
        }

        @Test
        @DisplayName("a quoted token is always an abbreviated step")
        void aQuotedTokenIsAlwaysAbbreviated() {
            refusedConcise(ModelErrorCode.NOT_FOUND,
                "d /e[f=\"mami\", hn=1]!s[g=\"pig\"] \"c\"");
        }

        @Test
        @DisplayName("a bare singleton name is never an abbreviated step")
        void aBareSingletonNameIsASingletonStep() {
            refusedConcise(ModelErrorCode.SINGLETON_CANNOT_BE_CREATED_OR_DELETED,
                "d /e[f=\"mami\", hn=1]!s[g=\"pig\"] c");
        }

        @Test
        @DisplayName("a language suffix qualifies an abbreviated value")
        void aLanguageSuffixQualifiesTheValue() {
            assertEffects(applyConcise("c e(\"pikpik\"@tpi)"),
                "created:entry@null", "set:form@tpi=pikpik");
        }
    }

    @Nested
    @DisplayName("unnamed initializer arguments")
    class UnnamedArguments {

        @Test
        @DisplayName("map to the properties the metamodel declares, in order")
        void mapToTheDeclaredProperties() {
            assertEffects(
                applyConcise("c /e[f=\"memi\"]/s[g=\"dog\"] f(\"editorial\", \"To be checked\")"),
                "created:field@null", "set:type=editorial", "set:text@en=To be checked");
        }

        @Test
        @DisplayName("are refused beyond the number the component type allows")
        void areRefusedBeyondTheDeclaredNumber() {
            refusedConcise(SyntaxErrorCode.UNNAMED_ARGUMENT_NOT_ALLOWED,
                "c /e[f=\"memi\"] s(\"puppy\", \"extra\")");
        }

        @Test
        @DisplayName("are refused on a component type that declares none")
        void areRefusedOnATypeWithNone() {
            refusedConcise(SyntaxErrorCode.UNNAMED_ARGUMENT_NOT_ALLOWED,
                "c /e[f=\"memi\"] r(\"synonym\")");
        }
    }

    @Nested
    @DisplayName("indented blocks")
    class IndentedBlocks {

        @Test
        @DisplayName("anchor their commands to the component of the line above")
        void anchorToTheLineAbove() {
            assertEffects(
                applyConcise("""
                    c e("mimi")
                      c s("lizard")
                        s (d@en = "A lizard")
                    """),
                "created:entry@null", "set:form@tww=mimi",
                "created:sense@1", "set:gloss@en=lizard",
                "set:definition@en=A lizard");
        }

        @Test
        @DisplayName("let a relative path start at the block's component")
        void letARelativePathStartAtTheBlockComponent() {
            assertEffects(
                applyConcise("""
                    e /e[f="mami", hn=1]
                      d s[g="pig"] x#1
                    """),
                "deleted:example@1");
        }

        @Test
        @DisplayName("let a property command reach the category of a sense just created")
        void setTheCategoryOfACreatedSense() {
            assertEffects(
                applyConcise("""
                    c e("mimi")
                      c s("lizard")
                        s c (v = "Noun")
                    """),
                "created:entry@null", "set:form@tww=mimi",
                "created:sense@1", "set:gloss@en=lizard",
                "set:value=Noun");
        }

        @Test
        @DisplayName("accept the full singleton name and the full property name")
        void acceptTheFullNames() {
            assertEffects(
                applyConcise("""
                    c /memi s("cat")
                      s category (value = "Noun")
                    """),
                "created:sense@2", "set:gloss@en=cat", "set:value=Noun");
        }

        @Test
        @DisplayName("let set, update and clear reach the category of an existing sense")
        void writeOnTheCategoryOfAnExistingSense() {
            assertEffects(
                applyConcise("""
                    e /e[f="mami", hn=1] s[g="pig"]
                      u c (v = "Verb")
                      l c (v)
                      s c (v = "Adjective")
                    """),
                "replaced:value:Noun->Verb", "removed:value=Verb", "set:value=Adjective");
        }

        @Test
        @DisplayName("agree with the rooted path to the same category")
        void agreeWithTheRootedPath() {
            assertEffects(applyConcise("s /e[f=\"mami\", hn=1]!s[g=\"pig\"]/c (v = \"Verb\")\n"),
                "replaced:value:Noun->Verb");
            newDictionary();
            assertEffects(
                applyConcise("""
                    e /e[f="mami", hn=1] s[g="pig"]
                      s c (v = "Verb")
                    """),
                "replaced:value:Noun->Verb");
        }

        @Test
        @DisplayName("let a block's sense host both its category and its other children")
        void mixTheCategoryWithOtherCommands() {
            assertEffects(
                applyConcise("""
                    c e("mimi")
                      c s("lizard")
                        s c (v = "Noun")
                        s (d@en = "A lizard")
                        c x("a mimi jefi")
                    """),
                "created:entry@null", "set:form@tww=mimi",
                "created:sense@1", "set:gloss@en=lizard",
                "set:value=Noun", "set:definition@en=A lizard",
                "created:example@1", "set:text@tww=a mimi jefi");
        }

        @Test
        @DisplayName("reach the category through a relative path of several steps")
        void reachTheCategoryThroughALongerRelativePath() {
            assertEffects(
                applyConcise("""
                    e /e[f="mami", hn=1]
                      s s[g="pork"]/c (v = "Verb")
                    """),
                "replaced:value:Noun->Verb");
        }

        @Test
        @DisplayName("refuse a relative singleton step the block's component does not have")
        void refuseASingletonTheBlockComponentLacks() {
            refusedConcise(ModelErrorCode.ILLEGAL_PARENT,
                """
                e /memi
                  s c (v = "Noun")
                """);
        }

        @Test
        @DisplayName("close when a blank line ends the command group")
        void closeAtTheEndOfTheGroup() {
            assertEffects(
                applyConcise("""
                    c e("mimi")
                      c s("lizard")

                    c e("momo")
                    """),
                "created:entry@null", "set:form@tww=mimi",
                "created:sense@1", "set:gloss@en=lizard",
                "created:entry@null", "set:form@tww=momo");
        }

        @Test
        @DisplayName("refuse a delete as an anchor, its component being gone")
        void refuseADeleteAsAnAnchor() {
            refusedConcise(ModelErrorCode.DELETE_CANNOT_BE_AN_ANCHOR,
                """
                d /memi
                  c s("puppy")
                """);
        }

        @Test
        @DisplayName("refuse a tab in the indentation")
        void refuseATabInTheIndentation() {
            refusedConcise(ModelErrorCode.SYNTAX_ERROR, "c e(\"mimi\")\n\tc s(\"lizard\")\n");
        }
    }

    @Nested
    @DisplayName("the p /path idiom")
    class UpsertIdiom {

        @Test
        @DisplayName("creates the entry and the sense when no match exists")
        void createsWhenNoMatchExists() {
            assertEffects(applyConcise("p /mimi/lizard"),
                "created:entry@null", "set:form@tww=mimi",
                "created:sense@1", "set:gloss@en=lizard");
        }

        @Test
        @DisplayName("changes nothing when the pair already exists")
        void changesNothingWhenThePairExists() {
            assertEffects(applyConcise("p /mami/taro"));
        }

        @Test
        @DisplayName("extends to the third abbreviated step, the example text")
        void extendsToTheExample() {
            assertEffects(applyConcise("p /mimi/lizard/\"a mimi jefi\""),
                "created:entry@null", "set:form@tww=mimi",
                "created:sense@1", "set:gloss@en=lizard",
                "created:example@1", "set:text@tww=a mimi jefi");
        }

        @Test
        @DisplayName("gives a new meaning a new entry rather than a homophone that means something else")
        void givesANewMeaningANewEntry() {
            assertEffects(applyConcise("p /mami/lizard"),
                "created:entry@null", "set:form@tww=mami",
                "created:sense@1", "set:gloss@en=lizard");
            assertEquals(3, entries("tww", "mami").size());
        }

        @Test
        @DisplayName("refuses a path whose steps are not abbreviated")
        void refusesANonAbbreviatedPath() {
            refusedConcise(SyntaxErrorCode.CONSTRUCTOR_REQUIRED, "p /e[f=\"mami\"]/s[g=\"pig\"]");
        }

        @Test
        @DisplayName("refuses a single step, which upserts an entry with no disambiguation")
        void refusesASingleStep() {
            refusedConcise(ModelErrorCode.UPSERT_ENTRY_WITHOUT_DISAMBIGUATION, "p /mami");
        }
    }

    @Nested
    @DisplayName("agreement with the reference syntax")
    class AgreementWithTheReferenceSyntax {

        @Test
        @DisplayName("the same operation written in both syntaxes has the same effects")
        void bothSyntaxesAgree() {
            Plan concise = box.plan(dictionary,
                "s /e[f=\"mami\", hn=1]!s[g=\"pig\"]/c (v = \"Verb\")",
                Syntax.CONCISE, "<concise>");
            Plan reference = box.plan(dictionary, """
                set value = "Verb"
                  on category
                  of sense[gloss@en = "pig"]
                  of entry[form@tww = "mami", hn = 1]
                """, Syntax.REFERENCE, "<reference>");
            assertEquals(1, concise.summary().propertiesSet());
            assertEquals(concise.allEffects().size(), reference.allEffects().size());
            assertTrue(concise.isOk() && reference.isOk());
        }

        @Test
        @DisplayName("a concise move with a to clause matches the reference two-under form")
        void moveAgrees() {
            assertEffects(
                applyConcise("m /e[f=\"mami\", hn=1]!s[g=\"pig\"] x#1 "
                    + "to /e[f=\"mami\", hn=1]!s[g=\"pork\"] at end"),
                "moved:example:1->1");
        }
    }
}
