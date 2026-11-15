package fr.cnrs.lacito.liftpatchbox.language.parser;

import fr.cnrs.lacito.liftpatchbox.error.ModelErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.SourceRange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * An ANTLR error listener that turns every lexer and parser diagnostic into a
 * {@link ModelErrorCode#SYNTAX_ERROR}, with its range.
 *
 * <p>The listener replaces ANTLR's default console listener, which writes to
 * {@code System.err} and is invisible to the caller. A line that matches the
 * command-recognition rule of Part 3, section 1.1 but does not parse is an error
 * and never prose, which is why the concise parser installs this listener
 * too.</p>
 *
 * <p>The range of an error is the offending token, or, for a lexer error, the
 * characters the lexer was reading; a {@link Locator} turns the indexes of the stream
 * the recognizer reads into the ranges of the document, so that a fragment parsed out
 * of a larger document — a concise command line — reports ranges of that document.</p>
 */
public final class CollectingErrorListener extends BaseErrorListener {

    /** Where characters of the stream a recognizer reads are in the document. */
    @FunctionalInterface
    public interface Locator {

        /**
         * The range of characters of the stream.
         *
         * @param start the index of the first character, in code points of the stream
         * @param end   the index after the last character, at least {@code start}
         * @return their range in the document
         */
        SourceRange chars(int start, int end);

        /**
         * The range of a token of the stream.
         *
         * @param token a token
         * @return its range, empty for a token of no character (end of file)
         */
        default SourceRange token(Token token) {
            int start = token.getStartIndex();
            int stop = token.getStopIndex();
            return chars(start, stop < start ? start : stop + 1);
        }
    }

    private final List<LiftPatchError> errors = new ArrayList<>();
    private final Locator locator;

    /**
     * A listener locating its errors with a locator.
     *
     * @param locator the ranges of the stream's characters in the document
     */
    public CollectingErrorListener(Locator locator) {
        this.locator = locator;
    }

    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e
    ) {
        SourceRange range;
        if (offendingSymbol instanceof Token t && t.getStartIndex() >= 0) {
            range = locator.token(t);
        } else if (recognizer instanceof Lexer lexer) {
            int start = lexer._tokenStartCharIndex;
            int end = Math.max(start, lexer.getInputStream().index());
            range = locator.chars(start, end);
        } else {
            range = SourceRange.UNKNOWN;
        }
        errors.add(LiftPatchError.of(ModelErrorCode.SYNTAX_ERROR, msg, range));
    }

    /**
     * Whether at least one syntax error was reported.
     *
     * @return {@code true} when {@link #errors()} is not empty
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * The syntax errors reported, in the order in which the recognizer found them.
     *
     * @return an unmodifiable view
     */
    public List<LiftPatchError> errors() {
        return Collections.unmodifiableList(errors);
    }
}
