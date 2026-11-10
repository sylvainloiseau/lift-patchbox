package fr.cnrs.lacito.liftpatchbox;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftpatchbox.ast.Script;
import fr.cnrs.lacito.liftpatchbox.engine.Executor;
import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCollector;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel;
import fr.cnrs.lacito.liftpatchbox.model.Journal;
import fr.cnrs.lacito.liftpatchbox.model.LiftModel;
import fr.cnrs.lacito.liftpatchbox.validation.SemanticValidator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The front door of the core: validate a script and either plan it or apply it to
 * a dictionary.
 *
 * <p>A script is a command model, built with {@link fr.cnrs.lacito.liftpatchbox.ast.LiftPatch}
 * or produced by a parser of one of the surface syntaxes:</p>
 *
 * <pre>{@code
 * Script script = LiftPatch.script()
 *     .add(LiftPatch.create("sense")
 *             .init("gloss", "en", "pig")
 *             .under(LiftPatch.chain(LiftPatch.step("entry").eq("form", "tww", "mami"))))
 *     .build();
 * LiftPatchEngine engine = new LiftPatchEngine().withDefaultObjectLanguage("tww");
 * Plan plan = engine.plan(dictionary, script);
 * if (plan.isOk()) {
 *     engine.apply(dictionary, script);
 * }
 * }</pre>
 *
 * <p>Static validation always runs first: a script with a static error changes
 * nothing, and every static error is reported. Execution then resolves each
 * command against the dictionary and applies the script in one transaction.</p>
 */
public final class LiftPatchEngine {

    private static final Logger LOGGER = Logger.getLogger(LiftPatchEngine.class.getName());

    private final Metamodel metamodel;
    private String defaultObjectLanguage;
    private String defaultMetaLanguage;

    /**
     * An engine validating against the standard metamodel of Appendix A.
     */
    public LiftPatchEngine() {
        this(Metamodel.standard());
    }

    /**
     * An engine validating against a given metamodel.
     *
     * @param metamodel the metamodel; an extended one may declare additional
     *                  component types and properties
     */
    public LiftPatchEngine(Metamodel metamodel) {
        this.metamodel = metamodel;
    }

    /**
     * Set the default object language, overriding the dictionary's first one.
     *
     * <p>Part 1, section 2.1 makes the first language of the dictionary's list the
     * fallback default. A caller that knows better states it here, and a
     * {@code language-default} directive or a {@code with} block in the script
     * still overrides it.</p>
     *
     * @param language the language code, or {@code null} to use the dictionary's first
     * @return this instance
     */
    public LiftPatchEngine withDefaultObjectLanguage(String language) {
        this.defaultObjectLanguage = language;
        return this;
    }

    /**
     * Set the default meta language, overriding the dictionary's first one.
     *
     * @param language the language code, or {@code null} to use the dictionary's first
     * @return this instance
     */
    public LiftPatchEngine withDefaultMetaLanguage(String language) {
        this.defaultMetaLanguage = language;
        return this;
    }

    /**
     * The metamodel this engine validates against.
     *
     * @return the metamodel
     */
    public Metamodel metamodel() {
        return metamodel;
    }

    /**
     * Run the static validation pass.
     *
     * @param script the command model
     * @return the collected errors and warnings; free of errors when the script has
     *         no static error
     */
    public ErrorCollector validate(Script script) {
        return new SemanticValidator(metamodel).validate(script);
    }

    /**
     * Validate a script and resolve it against a dictionary without committing
     * anything: the plan mode of Part 2, section 12.4.
     *
     * @param dictionary the dictionary to plan against
     * @param script     the command model
     * @return the plan document, whose status says whether the script would apply cleanly
     */
    public Plan plan(LiftDictionary dictionary, Script script) {
        return run(dictionary, script, true);
    }

    /**
     * Validate a script and apply it to a dictionary in one transaction.
     *
     * @param dictionary the dictionary to patch
     * @param script     the command model
     * @return the plan document describing what was applied
     * @throws LiftPatchException when the script has a static error, in which case
     *                            nothing is applied, or when a command fails, in
     *                            which case everything already applied is rolled back
     */
    public Plan apply(LiftDictionary dictionary, Script script) {
        Plan plan = run(dictionary, script, false);
        if (!plan.isOk()) {
            throw new LiftPatchException(
                plan.staticErrors().isEmpty() ? List.of(plan.error()) : plan.staticErrors());
        }
        LOGGER.log(Level.INFO, () -> "Applied " + script.source() + ": "
            + plan.summary().componentsCreated() + " component(s) created, "
            + plan.summary().componentsDeleted() + " deleted, "
            + plan.summary().propertiesSet() + " property value(s) written");
        LOGGER.log(Level.FINE, () -> plan.summary().format());
        return plan;
    }

    private Plan run(LiftDictionary dictionary, Script script, boolean planOnly) {
        ErrorCollector errors = validate(script);
        if (errors.hasErrors()) {
            // A script containing a static error changes nothing, and every static
            // error is reported before any command is applied (section 12.3).
            return Plan.ofStaticErrors(script.source(), script.syntaxName(), metamodel.version(),
                errors.errors(), errors.warnings());
        }
        Journal journal = new Journal();
        LiftModel model = new LiftModel(dictionary, metamodel, journal);
        Executor executor = new Executor(model, metamodel, journal);
        if (defaultObjectLanguage != null
            && model.hasLanguage(LanguageKind.OBJECT, defaultObjectLanguage)) {
            executor.bindDefaultLanguage(LanguageKind.OBJECT, defaultObjectLanguage);
        }
        if (defaultMetaLanguage != null
            && model.hasLanguage(LanguageKind.META, defaultMetaLanguage)) {
            executor.bindDefaultLanguage(LanguageKind.META, defaultMetaLanguage);
        }
        return executor.run(script, planOnly);
    }
}
