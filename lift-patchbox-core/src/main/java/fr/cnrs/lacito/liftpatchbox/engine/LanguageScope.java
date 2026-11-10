package fr.cnrs.lacito.liftpatchbox.engine;

import fr.cnrs.lacito.liftpatchbox.metamodel.LanguageKind;
import fr.cnrs.lacito.liftpatchbox.model.LiftModel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/**
 * The default object language and the default meta language in force at a point
 * of a script (Part 1, section 2.1).
 *
 * <p>The two are resolved independently of each other, and the rule is the same
 * for both: the default language of a kind is the most recent binding for that
 * kind in scope, whether it comes from a {@code with} header, from a
 * {@code language-default} directive or from a {@code language-create} directive,
 * the innermost scope being examined first; and, when no binding is in scope, the
 * first language of that kind in the dictionary's list.</p>
 *
 * <p>The fallback is always applicable, since neither language list can be empty,
 * so the resolution is total and deterministic.</p>
 */
public final class LanguageScope {

    private final LiftModel model;
    private final Deque<Map<LanguageKind, String>> frames = new ArrayDeque<>();

    /**
     * A scope over a dictionary, with one frame for the script itself.
     *
     * @param model the dictionary adapter, which supplies the fallback
     */
    public LanguageScope(LiftModel model) {
        this.model = model;
        frames.push(new EnumMap<>(LanguageKind.class));
    }

    /**
     * Enter a nested scope, as a block does.
     */
    public void push() {
        frames.push(new EnumMap<>(LanguageKind.class));
    }

    /**
     * Leave the innermost scope, restoring the bindings of the enclosing one.
     */
    public void pop() {
        frames.pop();
    }

    /**
     * Bind the default language of one kind for the rest of the innermost scope.
     *
     * @param kind     the language kind
     * @param language the language code
     */
    public void bind(LanguageKind kind, String language) {
        frames.peek().put(kind, language);
    }

    /**
     * The default language of one kind at this point of the script.
     *
     * @param kind the language kind
     * @return the language code; never {@code null} for a dictionary whose language
     *         list is not empty
     */
    public String defaultFor(LanguageKind kind) {
        for (Map<LanguageKind, String> frame : frames) {
            String bound = frame.get(kind);
            if (bound != null) {
                return bound;
            }
        }
        return model.firstLanguage(kind);
    }

    /**
     * Whether a scope still open binds {@code language} as the default of its kind.
     *
     * <p>Every open scope counts, not only the innermost: an outer binding comes back
     * into force when the inner scope ends. {@code language-delete} refuses such a
     * language (Part 1, section 2.1.3), so that the default of section 2.1 never names
     * a language the dictionary no longer has. The fallback - the first language of
     * the kind - is not a binding, and simply moves on to the next one.</p>
     *
     * @param kind     the language kind
     * @param language the language code
     * @return {@code true} when a binding in an open scope names it
     */
    public boolean isBound(LanguageKind kind, String language) {
        for (Map<LanguageKind, String> frame : frames) {
            if (language.equals(frame.get(kind))) {
                return true;
            }
        }
        return false;
    }
}
