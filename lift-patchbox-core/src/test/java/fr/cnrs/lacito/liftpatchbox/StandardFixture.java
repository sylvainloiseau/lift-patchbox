package fr.cnrs.lacito.liftpatchbox;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.builder.SenseBuilder;
import fr.cnrs.lacito.liftpatchbox.metamodel.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@code "standard"} fixture of Appendix D.2 in memory.
 *
 * <p>The fixture is deliberately built around the hard cases — two homophonous
 * entries, a sense with two glosses, a sense with two examples, and an inbound
 * reference — and most cases of the conformance corpus run against it. It is read
 * from the very JSON document the specification prints, so that a change to the
 * fixture in the specification is picked up by editing one resource.</p>
 *
 * <p>Two shapes of the fixture are normative and are honoured here: a singleton
 * child is written as an object rather than as a list, and a sense for which the
 * fixture states no {@code category} still has one, whose {@code value} is
 * unset.</p>
 *
 * <p>A corpus case may also give an inline dictionary of the same shape (Appendix
 * D.1), which {@link #build(Map)} builds. Such a document may declare
 * {@code externalFeatureSets}: feature sets defined in an external ranges file.
 * Since a feature set is external only when the dictionary was read from a file
 * referring to one, such a dictionary is written to a temporary directory, with
 * its ranges files, and read back.</p>
 */
public final class StandardFixture {

    private static final String RESOURCE =
        "/fr/cnrs/lacito/liftpatchbox/fixture-standard.json";

    private StandardFixture() {
    }

    /**
     * Build a fresh dictionary holding the standard fixture.
     *
     * @return a new dictionary, independent of every other one this method returned
     */
    public static LiftDictionary build() {
        return build(Json.parseObject(read()));
    }

    /**
     * Build a fresh dictionary from a document of the fixture's shape: the standard
     * fixture, or the inline dictionary of a corpus case (Appendix D.1).
     *
     * @param doc the document
     * @return a new dictionary
     */
    public static LiftDictionary build(Map<String, Object> doc) {
        List<String> objectLanguages = Json.strings(doc, "objectLanguages");
        List<String> metaLanguages = Json.strings(doc, "metaLanguages");
        Map<String, Object> external = Json.object(doc, "externalFeatureSets");
        LiftDictionary dictionary;
        if (external.isEmpty()) {
            dictionary = LiftDictionary.makeBuilder()
                .withObjectLanguages(objectLanguages.toArray(String[]::new))
                .withMetaLanguages(metaLanguages.toArray(String[]::new))
                .build();
        } else {
            dictionary = readWithExternalSets(external);
            for (String code : objectLanguages) {
                if (!dictionary.getObjectLanguageManager().getLanguages().contains(code)) {
                    dictionary.getObjectLanguageManager().addLanguage(code);
                }
            }
            for (String code : metaLanguages) {
                if (!dictionary.getMetaLanguageManager().getLanguages().contains(code)) {
                    dictionary.getMetaLanguageManager().addLanguage(code);
                }
            }
        }

        for (Object raw : Json.array(doc, "entries")) {
            addEntry(dictionary, cast(raw));
        }
        return dictionary;
    }

    /**
     * A dictionary with no entry, whose header refers to one external ranges file per
     * feature set, read from a temporary directory.
     */
    private static LiftDictionary readWithExternalSets(Map<String, Object> external) {
        try {
            Path dir = Files.createTempDirectory("liftpatch-fixture");
            StringBuilder ranges = new StringBuilder();
            for (Map.Entry<String, Object> set : external.entrySet()) {
                String id = set.getKey();
                String file = id + ".lift-ranges";
                StringBuilder elements = new StringBuilder();
                for (Object feature : (List<?>) set.getValue()) {
                    elements.append("<range-element id=\"").append(feature).append("\"/>\n");
                }
                Files.writeString(dir.resolve(file),
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<lift-ranges>\n<range id=\""
                        + id + "\">\n" + elements + "</range>\n</lift-ranges>\n");
                ranges.append("<range id=\"").append(id).append("\" href=\"").append(file)
                    .append("\"/>\n");
            }
            Path lift = dir.resolve("fixture.lift");
            Files.writeString(lift,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<lift version=\"0.13\" producer=\"fixture\">\n"
                    + "<header><ranges>\n" + ranges + "</ranges></header>\n</lift>\n");
            return LiftDictionary.loadDictionaryFromFile(lift.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the fixture to a temporary directory", e);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read the fixture back", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }

    private static void addEntry(LiftDictionary dictionary, Map<String, Object> entry) {
        var builder = dictionary.getComponentBuilder().entry();
        String id = Json.string(entry, "id");
        if (id != null) {
            builder.withId(id);
        }
        Json.object(entry, "form").forEach((lang, text) ->
            builder.withForm(lang, String.valueOf(text)));

        for (Object raw : Json.array(entry, "senses")) {
            Map<String, Object> sense = cast(raw);
            builder.addSense(s -> fillSense(s, sense));
        }
        for (Object raw : Json.array(entry, "relations")) {
            Map<String, Object> relation = cast(raw);
            builder.addRelation(
                Json.string(relation, "type"), Json.string(relation, "target"));
        }
        builder.build();
    }

    private static void fillSense(SenseBuilder builder, Map<String, Object> sense) {
        String id = Json.string(sense, "id");
        if (id != null) {
            builder.withId(id);
        }
        Json.object(sense, "gloss").forEach((lang, text) ->
            builder.withGloss(lang, String.valueOf(text)));
        Json.object(sense, "definition").forEach((lang, text) ->
            builder.withDefinition(lang, String.valueOf(text)));

        Map<String, Object> category = Json.object(sense, "category");
        String value = Json.string(category, "value");
        if (value != null) {
            builder.withPartOfSpeech(value);
        }

        for (Object raw : Json.array(sense, "examples")) {
            Map<String, Object> example = cast(raw);
            builder.addExample(x -> {
                Json.object(example, "text").forEach((lang, text) ->
                    x.withExample(lang, String.valueOf(text)));
                for (Object rawTranslation : Json.array(example, "translations")) {
                    Map<String, Object> translation = cast(rawTranslation);
                    String type = Json.string(translation, "type");
                    Json.object(translation, "text").forEach((lang, text) ->
                        x.addTranslation(type, lang, String.valueOf(text)));
                }
            });
        }
    }

    private static String read() {
        try (InputStream in = StandardFixture.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture resource " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the standard fixture", e);
        }
    }

    /**
     * The object languages the fixture declares, in order.
     *
     * @return the language codes
     */
    public static List<String> objectLanguages() {
        return Json.strings(Json.parseObject(read()), "objectLanguages");
    }

    /**
     * The meta languages the fixture declares, in order.
     *
     * @return the language codes
     */
    public static List<String> metaLanguages() {
        return Json.strings(Json.parseObject(read()), "metaLanguages");
    }

    /**
     * A dictionary as the LIFT document it saves to, for comparing two states of it.
     *
     * @param dictionary the dictionary
     * @return the document
     */
    public static String xml(LiftDictionary dictionary) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            dictionary.save(out);
        } catch (fr.cnrs.lacito.liftapi.WrittingLiftDocumentException e) {
            throw new IllegalStateException(e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
