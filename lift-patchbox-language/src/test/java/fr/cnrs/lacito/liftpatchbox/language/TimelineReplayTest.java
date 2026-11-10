package fr.cnrs.lacito.liftpatchbox.language;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftpatchbox.StandardFixture;
import fr.cnrs.lacito.liftpatchbox.TuwuliDictionary;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.metamodel.Json;
import fr.cnrs.lacito.liftpatchbox.timeline.Timeline;
import fr.cnrs.lacito.liftpatchbox.timeline.TimelinePosition;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The timeline's journal, replayed against every script of the conformance corpus:
 * whatever a script changes, reverting it gives back the dictionary as it was, and
 * reapplying it gives the dictionary the script produces — compared as the saved
 * LIFT document, byte for byte.
 */
class TimelineReplayTest {

    private static final String RESOURCE = "/fr/cnrs/lacito/liftpatchbox/language/corpus.json";

    private static LiftPatchBox box() {
        return new LiftPatchBox().withDefaultObjectLanguage("tww").withDefaultMetaLanguage("en");
    }

    private static String xml(LiftDictionary dictionary) {
        return StandardFixture.xml(dictionary);
    }

    /**
     * A dictionary's document with the ids the dictionary generates blanked out, to
     * compare two dictionaries that ran the same scripts independently.
     */
    private static String comparable(LiftDictionary dictionary) {
        return UUID.matcher(xml(dictionary)).replaceAll("<uuid>");
    }

    private static final Pattern UUID = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private record Case(String id, String script, Syntax syntax) {
    }

    private static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        try (InputStream in = TimelineReplayTest.class.getResourceAsStream(RESOURCE)) {
            for (Object raw : Json.parseArray(new String(in.readAllBytes(), StandardCharsets.UTF_8))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> c = (Map<String, Object>) raw;
                out.add(new Case(Json.string(c, "id"), Json.string(c, "script"),
                    "LiftPatchShort".equals(Json.string(c, "syntax")) ? Syntax.CONCISE : Syntax.REFERENCE));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** The command model of a case, or {@code null} when the case does not parse. */
    private static Script parse(LiftPatchBox box, Case c) {
        try {
            return box.parse(c.script(), c.syntax(), c.id()).script();
        } catch (LiftPatchException e) {
            return null;
        }
    }

    @Test
    void savingIsDeterministic() {
        LiftDictionary dictionary = StandardFixture.build();
        assertEquals(xml(dictionary), xml(dictionary));
        assertEquals(xml(dictionary), xml(StandardFixture.build()));
    }

    @TestFactory
    Stream<DynamicTest> everyCorpusScriptReplays() {
        return cases().stream().map(c -> DynamicTest.dynamicTest(c.id(), () -> {
            LiftPatchBox box = box();
            Script script = parse(box, c);
            if (script == null) {
                return;
            }
            LiftDictionary dictionary = StandardFixture.build();
            String before = xml(dictionary);

            LiftDictionary reference = StandardFixture.build();
            try {
                box.engine().apply(reference, script);
            } catch (LiftPatchException e) {
                // A refused script leaves the reference unchanged.
            }
            String after = comparable(reference);

            try (Timeline timeline = box.engine().record(dictionary, List.of(script))) {
                assertEquals(after, comparable(dictionary), c.id() + ": the recorded series ends elsewhere");
                String end = xml(dictionary);
                assertReplays(timeline, dictionary, c.id());
                timeline.seek(timeline.positions().get(0));
                assertEquals(before, xml(dictionary), c.id() + ": the start is not the original");
                timeline.close();
                assertEquals(end, xml(dictionary), c.id() + ": closing does not return to the end");
            }
        }));
    }

    @Test
    void theWholeCorpusAsOneSeries() {
        LiftPatchBox box = box();
        List<Script> series = cases().stream().map(c -> parse(box, c)).filter(s -> s != null).toList();

        LiftDictionary reference = StandardFixture.build();
        for (Script script : series) {
            try {
                box.engine().apply(reference, script);
            } catch (LiftPatchException e) {
                // Refused or rolled back: the next script starts from the same state.
            }
        }

        LiftDictionary dictionary = StandardFixture.build();
        String before = xml(dictionary);
        try (Timeline timeline = box.engine().record(dictionary, series)) {
            assertEquals(comparable(reference), comparable(dictionary));
            String end = xml(dictionary);
            assertReplays(timeline, dictionary, "series");
            timeline.seek(timeline.positions().get(0));
            assertEquals(before, xml(dictionary));
            timeline.close();
            assertEquals(end, xml(dictionary));
        }
    }

    @Test
    void theCorpusSeriesOnAFieldDictionary() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(TuwuliDictionary.available());
        LiftPatchBox box = new LiftPatchBox()
            .withDefaultObjectLanguage(TuwuliDictionary.OBJECT_LANGUAGE)
            .withDefaultMetaLanguage(TuwuliDictionary.META_LANGUAGE);
        List<Script> series = cases().stream().map(c -> parse(box, c)).filter(s -> s != null).toList();
        LiftDictionary dictionary = TuwuliDictionary.load();
        String before = xml(dictionary);
        try (Timeline timeline = box.engine().record(dictionary, series)) {
            String end = xml(dictionary);
            List<TimelinePosition> positions = timeline.positions();
            // A sample of the positions: saving a field dictionary is not free.
            List<TimelinePosition> sample = new ArrayList<>();
            for (int i = 0; i < positions.size(); i += Math.max(1, positions.size() / 12)) {
                sample.add(positions.get(i));
            }
            List<String> forward = new ArrayList<>();
            for (TimelinePosition p : sample) {
                timeline.seek(p);
                forward.add(xml(dictionary));
            }
            for (int i = sample.size() - 1; i >= 0; i--) {
                timeline.seek(sample.get(i));
                assertEquals(forward.get(i), xml(dictionary), "position " + sample.get(i));
            }
            assertEquals(before, forward.get(0));
            timeline.close();
            assertEquals(end, xml(dictionary));
        }
    }

    /**
     * Every position reached going forward is the same state as reached going
     * backward, the positions of failed scripts included.
     */
    private static void assertReplays(Timeline timeline, LiftDictionary dictionary, String id) {
        List<TimelinePosition> positions = timeline.positions();
        List<String> forward = new ArrayList<>();
        for (TimelinePosition p : positions) {
            timeline.seek(p);
            forward.add(xml(dictionary));
        }
        for (int i = positions.size() - 1; i >= 0; i--) {
            timeline.seek(positions.get(i));
            assertEquals(forward.get(i), xml(dictionary),
                id + ": position " + positions.get(i) + " differs going backward");
        }
    }
}
