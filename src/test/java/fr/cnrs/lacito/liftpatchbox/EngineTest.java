package fr.cnrs.lacito.liftpatchbox;

import static fr.cnrs.lacito.liftpatchbox.ast.LiftPatch.chain;
import static fr.cnrs.lacito.liftpatchbox.ast.LiftPatch.step;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftpatchbox.ast.AtClause;
import fr.cnrs.lacito.liftpatchbox.ast.LiftPatch;
import fr.cnrs.lacito.liftpatchbox.ast.PropertyRef;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import fr.cnrs.lacito.liftpatchbox.error.ErrorKind;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.ModelErrorCode;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The engine driven without any surface syntax: every script here is built with
 * {@link LiftPatch}, validated, planned and applied by {@link LiftPatchEngine}.
 */
class EngineTest extends EngineTestSupport {

    @BeforeEach
    void setUp() {
        newDictionary();
    }

    /** The entry {@code mami} of homograph number 1. */
    private static LiftPatch.StepBuilder mami1() {
        return step("entry").eq("form", "tww", "mami").hn(1);
    }

    /** The entry {@code memi}, the only one with that form. */
    private static LiftPatch.StepBuilder memi() {
        return step("entry").eq("form", "tww", "memi");
    }

    @Nested
    @DisplayName("component commands")
    class ComponentCommands {

        @Test
        @DisplayName("create a sense under an entry")
        void create() {
            Plan plan = apply(script(LiftPatch.create("sense").init("gloss", "en", "puppy")
                .under(chain(memi()))));
            assertEffects(plan, "created:sense@2", "set:gloss@en=puppy");
            assertEquals(1, senses(entry("tww", "memi"), "en", "puppy").size());
        }

        @Test
        @DisplayName("upsert creates once, then finds what it created")
        void upsertIsIdempotent() {
            Script script = script(LiftPatch.upsert("sense").init("gloss", "en", "puppy")
                .under(chain(memi())));
            assertEffects(apply(script), "created:sense@2", "set:gloss@en=puppy");
            assertEffects(apply(script));
            assertEquals(1, senses(entry("tww", "memi"), "en", "puppy").size());
        }

        @Test
        @DisplayName("ensure succeeds on an existing component and fails on a missing one")
        void ensure() {
            assertEffects(apply(script(LiftPatch.ensure(step("sense").eq("gloss", "en", "dog").build())
                .under(chain(memi())))));
            refused(ModelErrorCode.NOT_FOUND,
                script(LiftPatch.ensure(step("sense").eq("gloss", "en", "cat").build())
                    .under(chain(memi()))));
        }

        @Test
        @DisplayName("delete a sense")
        void delete() {
            Plan plan = apply(script(LiftPatch.delete(step("sense").eq("gloss", "en", "pork").build())
                .under(chain(mami1()))));
            assertEffects(plan, "deleted:sense@2");
            assertTrue(entries("tww", "mami").stream()
                .allMatch(e -> senses(e, "en", "pork").isEmpty()));
        }

        @Test
        @DisplayName("move a sense to another entry")
        void move() {
            Plan plan = apply(script(LiftPatch.move(step("sense").eq("gloss", "en", "pork").build())
                .from(chain(mami1()))
                .to(chain(memi()))
                .at(AtClause.end())));
            assertEquals(1, plan.allEffects().size());
            assertEquals(1, senses(entry("tww", "memi"), "en", "pork").size());
            assertTrue(entries("tww", "mami").stream()
                .allMatch(e -> senses(e, "en", "pork").isEmpty()));
        }
    }

    @Nested
    @DisplayName("property commands")
    class PropertyCommands {

        @Test
        @DisplayName("set, update and clear a definition")
        void setUpdateClear() {
            LiftPatch.ChainBuilder dog = chain(step("sense").eq("gloss", "en", "dog")).of(memi());
            assertEffects(apply(script(LiftPatch.set().assign("definition", "en", "a dog").on(dog))),
                "set:definition@en=a dog");
            assertEffects(apply(script(LiftPatch.update().assign("definition", "en", "a canine")
                    .on(chain(step("sense").eq("gloss", "en", "dog")).of(memi())))),
                "replaced:definition@en:a dog->a canine");
            assertEffects(apply(script(LiftPatch.clear().property(PropertyRef.of("definition", "en"))
                    .on(chain(step("sense").eq("gloss", "en", "dog")).of(memi())))),
                "removed:definition@en=a canine");
        }

        @Test
        @DisplayName("a label binds the component a command created")
        void label() {
            Plan plan = apply(script(
                LiftPatch.create("sense").init("gloss", "en", "puppy").under(chain(memi())).as("p"),
                LiftPatch.set().assign("definition", "en", "a young dog")
                    .on(chain(LiftPatch.label("p")))));
            assertEffects(plan,
                "created:sense@2", "set:gloss@en=puppy", "set:definition@en=a young dog");
        }
    }

    @Nested
    @DisplayName("blocks and directives")
    class BlocksAndDirectives {

        @Test
        @DisplayName("a selector block is the parent of the commands in its body")
        void selectorBlock() {
            Plan plan = apply(script(
                LiftPatch.block(LiftPatch.selectorHeader(chain(memi()).build(), null))
                    .add(LiftPatch.create("sense").init("gloss", "en", "puppy"))));
            assertEffects(plan, "created:sense@2", "set:gloss@en=puppy");
        }

        @Test
        @DisplayName("a with block binds the default meta language of its body")
        void withBlock() {
            Plan plan = apply(script(
                LiftPatch.block(LiftPatch.withHeader(Map.of(LanguageKind.META, "fr")))
                    .add(LiftPatch.set().assign(PropertyRef.of("gloss"), LiftPatch.text("chien"))
                        .on(chain(step("sense").eq("gloss", "en", "dog")).of(memi())))));
            assertEffects(plan, "set:gloss@fr=chien");
        }

        @Test
        @DisplayName("declaration directives")
        void directives() {
            Plan plan = apply(script(
                () -> LiftPatch.languageCreate(LanguageKind.OBJECT, "bis"),
                () -> LiftPatch.featureSetCreate("variant-type")));
            assertEffects(plan, "language:object:bis", "declared:feature-set:variant-type");
        }
    }

    @Nested
    @DisplayName("errors")
    class Errors {

        @Test
        @DisplayName("a property the component type does not have is a static error")
        void unknownProperty() {
            LiftPatchError e = refused(ModelErrorCode.PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE,
                script(LiftPatch.create("sense").init("colour", "en", "pink").under(chain(memi()))));
            assertEquals(ErrorKind.STATIC, e.kind());
        }

        @Test
        @DisplayName("a parent that cannot hold the component is a static error")
        void illegalParent() {
            refused(ModelErrorCode.ILLEGAL_PARENT,
                script(LiftPatch.create("example").init("text", "tww", "a memi").under(chain(memi()))));
        }

        @Test
        @DisplayName("an unbound label is a static error")
        void unknownLabel() {
            refused(ModelErrorCode.UNKNOWN_LABEL,
                script(LiftPatch.set().assign("definition", "en", "x").on(chain(LiftPatch.label("nope")))));
        }

        @Test
        @DisplayName("a move without an at clause is refused like the grammar refuses it")
        void moveWithoutAt() {
            refused(ModelErrorCode.SYNTAX_ERROR,
                script(LiftPatch.move(step("sense").eq("gloss", "en", "pork").build())
                    .from(chain(mami1())).to(chain(memi()))));
        }

        @Test
        @DisplayName("a selector matching several components is a dynamic error")
        void ambiguous() {
            LiftPatchError e = refused(ModelErrorCode.AMBIGUOUS_REFERENCE,
                script(LiftPatch.create("sense").init("gloss", "en", "x")
                    .under(chain(step("entry").eq("form", "tww", "mami")))));
            assertEquals(ErrorKind.DYNAMIC, e.kind());
        }

        @Test
        @DisplayName("a failing command rolls back the commands before it")
        void rollback() {
            refused(ModelErrorCode.CANNOT_CREATE_DUPLICATE, script(
                LiftPatch.create("sense").init("gloss", "en", "puppy").under(chain(memi())),
                LiftPatch.create("sense").init("gloss", "en", "dog").under(chain(memi()))));
            assertTrue(senses(entry("tww", "memi"), "en", "puppy").isEmpty());
        }
    }

    @Nested
    @DisplayName("plan mode")
    class PlanMode {

        @Test
        @DisplayName("planning changes nothing")
        void planChangesNothing() {
            Plan plan = plan(script(LiftPatch.create("sense").init("gloss", "en", "puppy")
                .under(chain(memi()))));
            assertTrue(plan.isOk());
            assertEffects(plan, "created:sense@2", "set:gloss@en=puppy");
            assertTrue(senses(entry("tww", "memi"), "en", "puppy").isEmpty());
        }

        @Test
        @DisplayName("a script built in Java reports no syntax, and its commands are rendered")
        void planOfAJavaScript() {
            Plan plan = plan(script(LiftPatch.create("sense").init("gloss", "en", "puppy")
                .under(chain(memi()))));
            assertNull(plan.syntax());
            assertTrue(plan.toJson().contains("\"syntax\": null"), plan.toJson());
            String command = plan.operations().get(0).command();
            assertTrue(command.startsWith("create sense"), command);
        }

        @Test
        @DisplayName("planning a deletion reports the references it leaves dangling")
        void danglingReferences() {
            Plan plan = plan(script(LiftPatch.delete(mami1().build())));
            assertTrue(plan.isOk(), () -> String.valueOf(plan.error()));
            assertFalse(plan.danglingReferences().isEmpty());
        }
    }
}
