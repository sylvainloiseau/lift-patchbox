package fr.cnrs.lacito.liftpatchbox.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftpatchbox.StandardFixture;
import fr.cnrs.lacito.liftpatchbox.TuwuliDictionary;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.metamodel.Json;
import fr.cnrs.lacito.liftpatchbox.timeline.RecordPolicy;
import fr.cnrs.lacito.liftpatchbox.timeline.ScriptRun;
import fr.cnrs.lacito.liftpatchbox.timeline.Timeline;
import fr.cnrs.lacito.liftpatchbox.timeline.TimelineOperation;
import fr.cnrs.lacito.liftpatchbox.timeline.TimelinePosition;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * {@link Timeline#rewind()} and {@link Timeline#resumeFrom(int, List)}, against the scripts
 * of the conformance corpus taken as one series: rewinding gives back the loaded document,
 * and resuming gives what recording the whole new series on the original dictionary gives.
 */
class TimelineResumeTest {

    private static final String RESOURCE = "/fr/cnrs/lacito/liftpatchbox/language/corpus.json";
    private static final Pattern UUID = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static LiftPatchBox box() {
        return new LiftPatchBox().withDefaultObjectLanguage("tww").withDefaultMetaLanguage("en");
    }

    private static String xml(LiftDictionary d) {
        return StandardFixture.xml(d);
    }

    /** The document with the ids the dictionary model generates blanked out. */
    private static String comparable(LiftDictionary d) {
        return UUID.matcher(xml(d)).replaceAll("<uuid>");
    }

    private static List<Script> corpus(LiftPatchBox box) {
        List<Script> out = new ArrayList<>();
        try (InputStream in = TimelineResumeTest.class.getResourceAsStream(RESOURCE)) {
            for (Object raw : Json.parseArray(new String(in.readAllBytes(), StandardCharsets.UTF_8))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> c = (Map<String, Object>) raw;
                Syntax syntax = "LiftPatchShort".equals(Json.string(c, "syntax")) ? Syntax.CONCISE : Syntax.REFERENCE;
                try {
                    out.add(box.parse(Json.string(c, "script"), syntax, Json.string(c, "id")).script());
                } catch (LiftPatchException e) {
                    // a case that does not parse is not a script of the series
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    /** What two timelines over two dictionaries say, compared: runs, operations, and a sample of positions. */
    private static void assertSameTimeline(
        Timeline expected, LiftDictionary expectedDictionary, Timeline actual, LiftDictionary actualDictionary,
        String what
    ) {
        assertEquals(expected.scripts().stream().map(r -> r.index() + ":" + r.status() + ":" + r.source()).toList(),
            actual.scripts().stream().map(r -> r.index() + ":" + r.status() + ":" + r.source()).toList(), what);
        assertEquals(describe(expected.operations()), describe(actual.operations()), what);
        assertEquals(expected.positions().size(), actual.positions().size(), what);
        for (String id : List.of("e-1", "e-2", "e-3")) {
            assertEquals(describe(expected.historyOf(id, true)), describe(actual.historyOf(id, true)), what + " " + id);
        }
        assertEquals(comparable(expectedDictionary), comparable(actualDictionary), what + ": end");
        int n = expected.positions().size();
        for (int i = 0; i < n; i += Math.max(1, n / 15)) {
            expected.seek(expected.positions().get(i));
            actual.seek(actual.positions().get(i));
            assertEquals(comparable(expectedDictionary), comparable(actualDictionary), what + ": position " + i);
        }
        expected.seek(expected.end());
        actual.seek(actual.end());
    }

    private static List<String> describe(List<TimelineOperation> ops) {
        return ops.stream().map(op -> op.index() + "/" + op.scriptIndex() + "/" + op.committed() + "/"
            + op.operation().command()).toList();
    }

    @Test
    void rewindGivesBackTheDictionaryAsLoaded() {
        LiftPatchBox box = box();
        List<Script> series = corpus(box);
        LiftDictionary dictionary = StandardFixture.build();
        String before = xml(dictionary);
        Timeline timeline = box.engine().record(dictionary, series);
        String end = comparable(dictionary);
        List<TimelinePosition> seen = new ArrayList<>();
        timeline.addSeekListener(seen::add);
        timeline.rewind();
        assertEquals(before, xml(dictionary));
        assertFalse(timeline.isOpen());
        assertEquals(List.of(timeline.positions().get(0)), seen);
        assertThrows(IllegalStateException.class, () -> timeline.seek(timeline.positions().get(0)));
        assertThrows(IllegalStateException.class, () -> timeline.resumeFrom(0, series));
        assertFalse(timeline.scripts().isEmpty(), "the data can still be read");
        timeline.close();
        assertEquals(before, xml(dictionary), "closing a rewound timeline does nothing");

        // Recorded again on the same dictionary, the series ends where it did.
        try (Timeline again = box.engine().record(dictionary, series)) {
            assertEquals(end, comparable(dictionary));
        }
    }

    @Test
    void rewindOnAFieldDictionary() throws Exception {
        Assumptions.assumeTrue(TuwuliDictionary.available());
        LiftPatchBox box = new LiftPatchBox()
            .withDefaultObjectLanguage(TuwuliDictionary.OBJECT_LANGUAGE)
            .withDefaultMetaLanguage(TuwuliDictionary.META_LANGUAGE);
        LiftDictionary dictionary = TuwuliDictionary.load();
        String before = xml(dictionary);
        box.engine().record(dictionary, corpus(box)).rewind();
        assertEquals(before, xml(dictionary));
    }

    @Test
    void resumingEqualsRecordingTheWholeNewSeries() {
        LiftPatchBox box = box();
        List<Script> series = corpus(box);
        int n = series.size();
        assertTrue(n > 20, "the corpus gives a long series");
        Script other = series.get(n / 3);
        for (int k : new int[] {0, 1, n / 2, n - 1, n}) {
            List<List<Script>> suffixes = new ArrayList<>();
            List<Script> tail = series.subList(k, n);
            suffixes.add(tail);                                                     // unchanged
            if (k < n) {
                List<Script> changed = new ArrayList<>(tail);
                changed.set(0, other);
                suffixes.add(changed);                                              // first one changed
                suffixes.add(tail.subList(1, tail.size()));                         // first one removed
            }
            suffixes.add(concat(List.of(other), tail));                             // one inserted
            for (List<Script> suffix : suffixes) {
                LiftDictionary resumed = StandardFixture.build();
                Timeline first = box.engine().record(resumed, series);
                Timeline next = first.resumeFrom(k, suffix);
                assertNotSame(first, next);
                assertFalse(first.isOpen());
                assertTrue(next.isOpen());
                assertEquals(k + suffix.size(), next.seriesSize());

                LiftDictionary fresh = StandardFixture.build();
                Timeline full = box.engine().record(fresh, concat(series.subList(0, k), suffix));
                assertSameTimeline(full, fresh, next, resumed, "k=" + k + ", suffix of " + suffix.size());
                full.close();
                next.close();
            }
        }
    }

    @Test
    void resumingTwiceStillEqualsAFullRecording() {
        LiftPatchBox box = box();
        List<Script> series = corpus(box);
        int n = series.size();
        LiftDictionary resumed = StandardFixture.build();
        Timeline t = box.engine().record(resumed, series.subList(0, n / 2));
        t = t.resumeFrom(n / 2, series.subList(n / 2, n));          // append
        t = t.resumeFrom(n / 4, series.subList(n / 4, n));          // replay the second quarter on
        LiftDictionary fresh = StandardFixture.build();
        Timeline full = box.engine().record(fresh, series);
        assertSameTimeline(full, fresh, t, resumed, "twice");
    }

    @Test
    void aStoppedRecordingResumesAtTheFailedScriptOnly() {
        LiftPatchBox box = box();
        Script ok = box.parse("c e(\"kafo\", s(\"stone\"))", Syntax.CONCISE, "ok").script();
        // memi already has the sense dog: creating it again fails (CANNOT_CREATE_DUPLICATE)
        Script fails = fr.cnrs.lacito.liftpatchbox.ast.LiftPatch.script()
            .add(fr.cnrs.lacito.liftpatchbox.ast.LiftPatch.create("sense").init("gloss", "en", "dog")
                .under(fr.cnrs.lacito.liftpatchbox.ast.LiftPatch.chain(
                    fr.cnrs.lacito.liftpatchbox.ast.LiftPatch.step("entry").eq("form", "tww", "memi"))))
            .build();
        Script fixed = box.parse("c e(\"lofi\", s(\"water\"))", Syntax.CONCISE, "fixed").script();
        Script after = box.parse("c e(\"mafa\", s(\"sun\"))", Syntax.CONCISE, "after").script();
        LiftDictionary dictionary = StandardFixture.build();
        Timeline t = box.engine().record(dictionary, List.of(ok, fails, after), RecordPolicy.STOP_AT_FIRST_FAILURE);
        assertEquals(1, t.stoppedAt().orElseThrow().index());
        assertEquals(2, t.scripts().size());
        Timeline stopped = t;
        assertThrows(IllegalArgumentException.class, () -> stopped.resumeFrom(2, List.of(after)));
        assertThrows(IllegalArgumentException.class, () -> stopped.resumeFrom(-1, List.of()));
        Timeline next = t.resumeFrom(1, List.of(fixed, after));
        assertEquals(RecordPolicy.STOP_AT_FIRST_FAILURE, next.policy());
        assertTrue(next.stoppedAt().isEmpty());
        assertEquals(List.of(ScriptRun.Status.APPLIED, ScriptRun.Status.APPLIED, ScriptRun.Status.APPLIED),
            next.scripts().stream().map(ScriptRun::status).toList());
        String text = xml(dictionary);
        assertTrue(text.contains("lofi") && text.contains("mafa") && text.contains("kafo"));
    }

    @Test
    void aSupersededTimelineRefusesEverythingButItsData() {
        LiftPatchBox box = box();
        List<Script> series = corpus(box);
        LiftDictionary dictionary = StandardFixture.build();
        Timeline first = box.engine().record(dictionary, series);
        List<TimelinePosition> seen = new ArrayList<>();
        first.addSeekListener(seen::add);
        TimelinePosition old = first.positions().get(first.positions().size() - 1);
        Timeline next = first.resumeFrom(series.size() / 2, series.subList(series.size() / 2, series.size()));
        assertEquals(List.of(next.current()), seen, "the listener moved, and was told of the new end");
        assertThrows(IllegalStateException.class, () -> first.seek(first.positions().get(0)));
        assertThrows(IllegalStateException.class, () -> first.resumeFrom(0, series));
        assertThrows(IllegalStateException.class, first::rewind);
        assertThrows(IllegalStateException.class, () -> first.historyOf("e-1"));
        first.close();                                             // does nothing
        if (old != next.positions().get(old.index())) {
            assertThrows(IllegalArgumentException.class, () -> next.seek(old),
                "a discarded position is not a position of the new timeline");
        }
        next.seek(next.positions().get(0));
        assertEquals(2, seen.size());
        next.close();
    }
}
