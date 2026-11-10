package fr.cnrs.lacito.liftpatchbox;

import static fr.cnrs.lacito.liftpatchbox.ast.LiftPatch.chain;
import static fr.cnrs.lacito.liftpatchbox.ast.LiftPatch.step;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftpatchbox.ast.AtClause;
import fr.cnrs.lacito.liftpatchbox.ast.LiftPatch;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import fr.cnrs.lacito.liftpatchbox.timeline.ScriptRun;
import fr.cnrs.lacito.liftpatchbox.timeline.Timeline;
import fr.cnrs.lacito.liftpatchbox.timeline.TimelineOperation;
import fr.cnrs.lacito.liftpatchbox.timeline.TimelinePosition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The timeline of a series of scripts: positions, seeking, failed and rejected
 * scripts, and the history of each entry. The fixture's entries are {@code e-1}
 * ({@code mami}, homograph 1, senses pig and pork), {@code e-2} ({@code mami},
 * homograph 2) and {@code e-3} ({@code memi}, sense dog, a synonym of {@code e-1}).
 */
class TimelineTest extends EngineTestSupport {

    @BeforeEach
    void setUp() {
        newDictionary();
    }

    private static LiftPatch.StepBuilder mami1() {
        return step("entry").eq("form", "tww", "mami").hn(1);
    }

    private static LiftPatch.StepBuilder memi() {
        return step("entry").eq("form", "tww", "memi");
    }

    private static LiftPatch.CreateBuilder newSense(String gloss) {
        return LiftPatch.create("sense").init("gloss", "en", gloss).under(chain(memi()));
    }

    private Timeline record(Script... scripts) {
        return engine.record(dictionary, List.of(scripts));
    }

    private static List<Integer> indices(List<TimelineOperation> operations) {
        return operations.stream().map(TimelineOperation::index).toList();
    }

    private boolean memiHas(String gloss) {
        return !senses(entry("tww", "memi"), "en", gloss).isEmpty();
    }

    @Test
    @DisplayName("one position per operation, after the start; seeking brings the dictionary there")
    void positionsAndSeeking() {
        String before = StandardFixture.xml(dictionary);
        try (Timeline timeline = record(
            script(newSense("puppy")),
            script(newSense("hound"), newSense("cur")))) {
            List<TimelinePosition> positions = timeline.positions();
            assertEquals(4, positions.size());
            assertEquals(TimelinePosition.Status.START, positions.get(0).status());
            assertSame(positions.get(3), timeline.current());
            assertTrue(memiHas("cur"));

            timeline.seek(positions.get(2));
            assertTrue(memiHas("hound"));
            assertFalse(memiHas("cur"));

            timeline.seek(positions.get(0));
            assertEquals(before, StandardFixture.xml(dictionary));
        }
        assertTrue(memiHas("cur"), "closing returns to the end of the series");
    }

    @Test
    @DisplayName("seeking keeps the very objects, so references held by a GUI stay valid")
    void identityIsKept() {
        try (Timeline timeline = record(script(newSense("puppy")))) {
            LiftEntry memi = entry("tww", "memi");
            LiftSense puppy = senses(memi, "en", "puppy").get(0);
            String id = puppy.getId().orElseThrow();
            timeline.seek(timeline.positions().get(0));
            timeline.seek(timeline.end());
            assertSame(memi, entry("tww", "memi"));
            assertSame(puppy, senses(memi, "en", "puppy").get(0));
            assertEquals(id, puppy.getId().orElseThrow());
        }
    }

    @Test
    @DisplayName("the history of an entry lists the operations that changed its subtree")
    void historyOfAnEntry() {
        try (Timeline timeline = record(
            script(newSense("puppy"),
                LiftPatch.set().assign("definition", "en", "a dog")
                    .on(chain(step("sense").eq("gloss", "en", "dog")).of(memi()))),
            script(LiftPatch.create("sense").init("gloss", "en", "boar").under(chain(mami1()))))) {
            assertEquals(List.of(1, 2), indices(timeline.historyOf("e-3")));
            assertEquals(List.of(3), indices(timeline.historyOf("e-1")));
            assertTrue(timeline.historyOf("e-2").isEmpty());
            assertEquals(1, timeline.operations().get(2).scriptIndex());
        }
    }

    @Test
    @DisplayName("a move between entries belongs to the history of both")
    void moveBelongsToBoth() {
        try (Timeline timeline = record(script(
            LiftPatch.move(step("sense").eq("gloss", "en", "pork").build())
                .from(chain(mami1())).to(chain(memi())).at(AtClause.end())))) {
            assertEquals(List.of(1), indices(timeline.historyOf("e-1")));
            assertEquals(List.of(1), indices(timeline.historyOf("e-3")));
        }
    }

    @Test
    @DisplayName("deleting an entry is in its history, not in that of an entry referring to it")
    void deletionAndDanglingReference() {
        try (Timeline timeline = record(script(LiftPatch.delete(mami1().build())))) {
            assertEquals(List.of(1), indices(timeline.historyOf("e-1")));
            assertTrue(timeline.historyOf("e-3").isEmpty(),
                "e-3's synonym relation is left dangling, and e-3 itself unchanged");
            timeline.seek(timeline.positions().get(0));
            assertEquals(2, entries("tww", "mami").size());
        }
    }

    @Test
    @DisplayName("a new entry has a history from its creation")
    void newEntry() {
        try (Timeline timeline = record(
            script(LiftPatch.create("entry").init("form", "tww", "kokoi")),
            script(LiftPatch.create("sense").init("gloss", "en", "bird")
                .under(chain(step("entry").eq("form", "tww", "kokoi")))))) {
            String id = entry("tww", "kokoi").getId().orElseThrow();
            assertEquals(List.of(1, 2), indices(timeline.historyOf(id)));
        }
    }

    @Test
    @DisplayName("an upsert that finds its component, and a directive, change no entry")
    void noEntryChanged() {
        try (Timeline timeline = record(script(
            LiftPatch.upsert("sense").init("gloss", "en", "dog").under(chain(memi())),
            () -> LiftPatch.languageCreate(LanguageKind.OBJECT, "bis")))) {
            assertEquals(3, timeline.positions().size());
            assertTrue(timeline.operations().stream().allMatch(op -> op.entryIds().isEmpty()));
        }
    }

    @Test
    @DisplayName("a script with a static error is rejected and has no position")
    void rejectedScript() {
        try (Timeline timeline = record(
            script(newSense("puppy").as("p")),
            // Labels do not carry over from one script to the next.
            script(LiftPatch.set().assign("definition", "en", "x").on(chain(LiftPatch.label("p")))),
            script(newSense("hound")))) {
            assertEquals(List.of(ScriptRun.Status.APPLIED, ScriptRun.Status.REJECTED,
                ScriptRun.Status.APPLIED), timeline.scripts().stream().map(ScriptRun::status).toList());
            assertEquals("UNKNOWN_LABEL",
                timeline.scripts().get(1).plan().staticErrors().get(0).code().id());
            assertEquals(3, timeline.positions().size());
            assertTrue(memiHas("hound"));
        }
    }

    @Test
    @DisplayName("a failed script's positions show the state it reached, greyed out")
    void failedScript() {
        try (Timeline timeline = record(
            script(newSense("puppy")),
            script(newSense("kitten"), newSense("dog")),
            script(newSense("hound")))) {
            assertEquals(ScriptRun.Status.FAILED, timeline.scripts().get(1).status());
            assertFalse(memiHas("kitten"));
            assertTrue(memiHas("hound"));

            TimelinePosition kitten = timeline.positions().get(2);
            assertEquals(TimelinePosition.Status.FAILED_SCRIPT, kitten.status());
            assertFalse(kitten.isCommitted());
            timeline.seek(kitten);
            assertTrue(memiHas("puppy"));
            assertTrue(memiHas("kitten"));
            assertFalse(memiHas("hound"));

            TimelinePosition failure = timeline.positions().get(3);
            assertEquals("CANNOT_CREATE_DUPLICATE",
                failure.operation().orElseThrow().error().code().id());

            timeline.seek(timeline.positions().get(4));
            assertFalse(memiHas("kitten"));
            assertTrue(memiHas("hound"));

            assertEquals(List.of(1, 4), indices(timeline.historyOf("e-3")));
            // The failing command had attached its sense when the uniqueness check
            // refused it: it changed e-3 too, before the script was rolled back.
            assertEquals(List.of(1, 2, 3, 4), indices(timeline.historyOf("e-3", true)));
        }
    }

    @Test
    @DisplayName("a failure inside a block keeps what the block had done for the greyed positions")
    void failureInsideABlock() {
        try (Timeline timeline = record(script(
            LiftPatch.block(LiftPatch.selectorHeader(chain(memi()).build(), null))
                .add(LiftPatch.create("sense").init("gloss", "en", "kitten"))
                .add(LiftPatch.create("sense").init("gloss", "en", "dog"))))) {
            assertEquals(ScriptRun.Status.FAILED, timeline.scripts().get(0).status());
            assertFalse(memiHas("kitten"));
            TimelinePosition afterKitten = timeline.positions().get(2);
            timeline.seek(afterKitten);
            assertTrue(memiHas("kitten"));
            timeline.seek(timeline.positions().get(0));
            assertFalse(memiHas("kitten"));
        }
    }

    @Test
    @DisplayName("seek listeners are told of each seek")
    void seekListeners() {
        List<TimelinePosition> seen = new ArrayList<>();
        try (Timeline timeline = record(script(newSense("puppy")))) {
            timeline.addSeekListener(seen::add);
            timeline.seek(timeline.positions().get(0));
        }
        assertEquals(2, seen.size(), "the seek, and the return to the end on close");
    }
}
