package fr.cnrs.lacito.liftpatchbox;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCollector;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel;
import fr.cnrs.lacito.liftpatchbox.parser.ScriptParser;
import fr.cnrs.lacito.liftpatchbox.parser.SourceScript;
import fr.cnrs.lacito.liftpatchbox.parser.Syntax;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The front door of the language: parse a script written in one of the two
 * surface syntaxes, then validate it and either plan it or apply it to a
 * dictionary through a {@link LiftPatchEngine}.
 *
 * <pre>{@code
 * LiftDictionary dictionary = LiftDictionary.loadDictionaryFromFile(new File("tww.lift"));
 * LiftPatchBox box = new LiftPatchBox()
 *     .withDefaultObjectLanguage("tww")
 *     .withDefaultMetaLanguage("en");
 *
 * Plan plan = box.plan(dictionary, script, Syntax.CONCISE, "edits.liftpatchs");
 * if (plan.isOk()) {
 *     box.apply(dictionary, script, Syntax.CONCISE, "edits.liftpatchs");
 * }
 * }</pre>
 *
 * <p>Parsing yields a {@link SourceScript}: the command model the engine runs, and
 * what the text said about itself. The warnings the text calls for are reported
 * in the plan ahead of those of execution.</p>
 */
public final class LiftPatchBox {

    private final LiftPatchEngine engine;

    /**
     * A front door validating against the standard metamodel of Appendix A.
     */
    public LiftPatchBox() {
        this(Metamodel.standard());
    }

    /**
     * A front door validating against a given metamodel.
     *
     * @param metamodel the metamodel; an extended one may declare additional
     *                  component types and properties
     */
    public LiftPatchBox(Metamodel metamodel) {
        this.engine = new LiftPatchEngine(metamodel);
    }

    /**
     * Set the default object language, overriding the dictionary's first one.
     *
     * @param language the language code, or {@code null} to use the dictionary's first
     * @return this instance
     * @see LiftPatchEngine#withDefaultObjectLanguage(String)
     */
    public LiftPatchBox withDefaultObjectLanguage(String language) {
        engine.withDefaultObjectLanguage(language);
        return this;
    }

    /**
     * Set the default meta language, overriding the dictionary's first one.
     *
     * @param language the language code, or {@code null} to use the dictionary's first
     * @return this instance
     * @see LiftPatchEngine#withDefaultMetaLanguage(String)
     */
    public LiftPatchBox withDefaultMetaLanguage(String language) {
        engine.withDefaultMetaLanguage(language);
        return this;
    }

    /**
     * The metamodel this instance parses and validates against.
     *
     * @return the metamodel
     */
    public Metamodel metamodel() {
        return engine.metamodel();
    }

    /**
     * The engine that validates, plans and applies the parsed scripts.
     *
     * @return the engine
     */
    public LiftPatchEngine engine() {
        return engine;
    }

    // ===================================================================
    // Parsing
    // ===================================================================

    /**
     * Parse a script held in a string.
     *
     * @param text   the script text
     * @param syntax the syntax to parse it with, or {@code null} to take it from the pragma
     * @param source the source name, used in diagnostics and plan documents
     * @return the parsed script
     * @throws LiftPatchException when the script does not parse
     */
    public SourceScript parse(String text, Syntax syntax, String source) {
        return new ScriptParser(engine.metamodel()).parse(text, syntax, source);
    }

    /**
     * Parse a script held in a file.
     *
     * @param file   the file, read as UTF-8
     * @param syntax the syntax to parse it with, or {@code null} to take it from the
     *               pragma or from the file extension
     * @return the parsed script
     * @throws IOException        if the file cannot be read
     * @throws LiftPatchException when the script does not parse
     */
    public SourceScript parseFile(Path file, Syntax syntax) throws IOException {
        return new ScriptParser(engine.metamodel()).parseFile(file, syntax);
    }

    // ===================================================================
    // Validation, planning and applying
    // ===================================================================

    /**
     * Run the static validation pass.
     *
     * @param script the parsed script
     * @return the collected errors and warnings; empty when the script is free of
     *         static errors
     */
    public ErrorCollector validate(SourceScript script) {
        return engine.validate(script.script());
    }

    /**
     * Validate a parsed script and resolve it against a dictionary without
     * committing anything: the plan mode of Part 2, section 12.4.
     *
     * @param dictionary the dictionary to plan against
     * @param script     the parsed script
     * @return the plan document, whose status says whether the script would apply cleanly
     */
    public Plan plan(LiftDictionary dictionary, SourceScript script) {
        return engine.plan(dictionary, script.script()).withLeadingWarnings(script.warnings());
    }

    /**
     * Validate a parsed script and apply it to a dictionary in one transaction.
     *
     * @param dictionary the dictionary to patch
     * @param script     the parsed script
     * @return the plan document describing what was applied
     * @throws LiftPatchException when the script has a static error, in which case
     *                            nothing is applied, or when a command fails, in
     *                            which case everything already applied is rolled back
     */
    public Plan apply(LiftDictionary dictionary, SourceScript script) {
        return engine.apply(dictionary, script.script()).withLeadingWarnings(script.warnings());
    }

    /**
     * Parse, validate and apply a script in one call.
     *
     * @param dictionary the dictionary to patch
     * @param text       the script text
     * @param syntax     the syntax to parse it with, or {@code null}
     * @param source     the source name
     * @return the plan document describing what was applied
     */
    public Plan apply(LiftDictionary dictionary, String text, Syntax syntax, String source) {
        return apply(dictionary, parse(text, syntax, source));
    }

    /**
     * Parse, validate and plan a script in one call.
     *
     * @param dictionary the dictionary to plan against
     * @param text       the script text
     * @param syntax     the syntax to parse it with, or {@code null}
     * @param source     the source name
     * @return the plan document
     */
    public Plan plan(LiftDictionary dictionary, String text, Syntax syntax, String source) {
        return plan(dictionary, parse(text, syntax, source));
    }
}
