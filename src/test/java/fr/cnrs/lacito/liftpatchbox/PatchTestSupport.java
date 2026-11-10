package fr.cnrs.lacito.liftpatchbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import fr.cnrs.lacito.liftpatchbox.parser.Syntax;
import fr.cnrs.lacito.liftpatchbox.engine.Plan;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;

/**
 * What the behaviour tests are written with: the fixture and assertions of
 * {@link EngineTestSupport}, and scripts written as text in either surface syntax.
 */
public abstract class PatchTestSupport extends EngineTestSupport {

    /** The front door, configured with the fixture's languages. */
    protected LiftPatchBox box;

    /**
     * Rebuild the fixture. JUnit calls this before each test through the
     * {@code @BeforeEach} of the concrete class.
     */
    @Override
    protected void newDictionary() {
        super.newDictionary();
        box = new LiftPatchBox()
            .withDefaultObjectLanguage("tww")
            .withDefaultMetaLanguage("en");
    }

    // ===================================================================
    // Running
    // ===================================================================

    /**
     * Apply a reference-syntax script and return what it did.
     *
     * @param script the script
     * @return the plan document
     */
    protected Plan apply(String script) {
        return box.apply(dictionary, script, Syntax.REFERENCE, "<test>");
    }

    /**
     * Apply a concise-syntax script and return what it did.
     *
     * @param script the script
     * @return the plan document
     */
    protected Plan applyConcise(String script) {
        return box.apply(dictionary, script, Syntax.CONCISE, "<test>");
    }

    /**
     * Plan a reference-syntax script without changing anything.
     *
     * @param script the script
     * @return the plan document
     */
    protected Plan plan(String script) {
        return box.plan(dictionary, script, Syntax.REFERENCE, "<test>");
    }

    // ===================================================================
    // Asserting
    // ===================================================================

    /**
     * Assert that a reference-syntax script is refused with a given code, and that
     * the dictionary is left unchanged.
     *
     * @param expected the code the script must raise
     * @param script   the script
     * @return the error, so that a test may look at its position or message
     */
    protected LiftPatchError refused(ErrorCode expected, String script) {
        return refused(expected, script, Syntax.REFERENCE);
    }

    /**
     * Assert that a concise-syntax script is refused with a given code.
     *
     * @param expected the code the script must raise
     * @param script   the script
     * @return the error
     */
    protected LiftPatchError refusedConcise(ErrorCode expected, String script) {
        return refused(expected, script, Syntax.CONCISE);
    }

    private LiftPatchError refused(ErrorCode expected, String script, Syntax syntax) {
        int entriesBefore = dictionary.getLiftDictionaryRegistry().getEntries().size();
        try {
            box.apply(dictionary, script, syntax, "<test>");
        } catch (LiftPatchException e) {
            assertEquals(expected, e.code(),
                "wrong code (" + e.firstError().message() + ")");
            assertEquals(entriesBefore, dictionary.getLiftDictionaryRegistry().getEntries().size(),
                "a refused script must leave the dictionary unchanged");
            return e.firstError();
        }
        fail("expected " + expected + " but the script applied cleanly: " + script);
        return null;
    }
}
