package fr.cnrs.lacito.liftpatchbox.language.parser;

import java.util.Set;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The error strategy of the reference parser: it resynchronises at the next
 * command, and reports one syntax error per broken command.
 *
 * <p>ANTLR's default recovery resynchronises on the follow sets of the rules it is
 * in. In this grammar a bare name can begin a block header, so after an error the
 * default strategy readily starts spurious items at the words of the broken command
 * (`entry`, `form`, …) and reports an error for each. This strategy changes two
 * things:</p>
 *
 * <ul>
 *   <li><strong>Resynchronisation.</strong> When a rule fails, the tokens are
 *       skipped up to the next token that can only begin a command or a directive
 *       ({@code create}, {@code set}, {@code with}, {@code language-default}, …), a
 *       closing brace, or the end of the script.</li>
 *   <li><strong>Reporting.</strong> Once an error has been reported, the further
 *       errors are not, until the parser has matched such a command keyword. They
 *       are almost always consequences of the first, and listing them would bury
 *       the one the writer has to fix. A second mistake in another command is still
 *       reported.</li>
 * </ul>
 *
 * <p>Install it with {@link Parser#setErrorHandler} and register
 * {@link #listener()} with {@link Parser#addParseListener}.</p>
 */
final class ItemErrorStrategy extends DefaultErrorStrategy {

    /** The tokens that can only begin a command or a directive. */
    private static final Set<Integer> COMMAND_STARTS = Set.of(
        LiftPatchRefLexer.CREATE, LiftPatchRefLexer.UPSERT, LiftPatchRefLexer.ENSURE,
        LiftPatchRefLexer.DELETE, LiftPatchRefLexer.MOVE, LiftPatchRefLexer.SET,
        LiftPatchRefLexer.UPDATE, LiftPatchRefLexer.CLEAR, LiftPatchRefLexer.WITH,
        LiftPatchRefLexer.LANGUAGE_DEFAULT, LiftPatchRefLexer.LANGUAGE_CREATE,
        LiftPatchRefLexer.LANGUAGE_DELETE,
        LiftPatchRefLexer.FEATURE_SET_CREATE, LiftPatchRefLexer.FEATURE_SET_DELETE,
        LiftPatchRefLexer.FEATURE_SET_RENAME, LiftPatchRefLexer.FEATURE_CREATE,
        LiftPatchRefLexer.FEATURE_DELETE, LiftPatchRefLexer.FEATURE_RENAME,
        LiftPatchRefLexer.FIELD_CREATE, LiftPatchRefLexer.FIELD_DELETE,
        LiftPatchRefLexer.TRAIT_CREATE, LiftPatchRefLexer.TRAIT_DELETE);

    /** Whether errors are left unreported, an error having been reported in this command. */
    private boolean quiet;

    /** The index of the first token of the item the parser is in. */
    private int itemStart = -1;

    /** The token index of the last resynchronisation, to make sure each one moves on. */
    private int lastRecoverIndex = -1;

    /**
     * The listener that tells this strategy where items begin and which tokens are
     * matched.
     *
     * @return a parse listener to register on the parser
     */
    ParseTreeListener listener() {
        return new ParseTreeListener() {
            @Override
            public void enterEveryRule(ParserRuleContext ctx) {
                if (ctx instanceof LiftPatchRefParser.ItemContext) {
                    itemStart = ctx.getStart().getTokenIndex();
                }
            }

            @Override
            public void exitEveryRule(ParserRuleContext ctx) {
                // Nothing to do on exit.
            }

            @Override
            public void visitTerminal(TerminalNode node) {
                // A command keyword matched as such: a new command has begun.
                if (COMMAND_STARTS.contains(node.getSymbol().getType())) {
                    quiet = false;
                }
            }

            @Override
            public void visitErrorNode(ErrorNode node) {
                // A token skipped by the recovery begins nothing.
            }
        };
    }

    @Override
    public void reportError(Parser recognizer, RecognitionException e) {
        if (!suppressed(recognizer)) {
            super.reportError(recognizer, e);
        }
    }

    /**
     * Report a prediction failure at the token where the failing construct began,
     * not at the token where ANTLR gave up: the latter is often the first token of
     * the next command, on the next line, which is not where the writer has to look.
     */
    @Override
    protected void reportNoViableAlternative(Parser recognizer, NoViableAltException e) {
        TokenStream tokens = recognizer.getInputStream();
        String input = e.getStartToken().getType() == Token.EOF
            ? "<EOF>"
            : tokens.getText(e.getStartToken(), e.getOffendingToken());
        recognizer.notifyErrorListeners(e.getStartToken(),
            "no viable alternative at input " + escapeWSAndQuote(input), e);
    }

    @Override
    protected void reportUnwantedToken(Parser recognizer) {
        if (!suppressed(recognizer)) {
            super.reportUnwantedToken(recognizer);
        }
    }

    @Override
    protected void reportMissingToken(Parser recognizer) {
        if (!suppressed(recognizer)) {
            super.reportMissingToken(recognizer);
        }
    }

    /**
     * Skip to the next command. The first token of the failing item is skipped in
     * any case, so that an item that fails on its first token is not parsed again
     * from the same place, and every resynchronisation moves on by at least one token.
     */
    @Override
    public void recover(Parser recognizer, RecognitionException e) {
        TokenStream input = recognizer.getInputStream();
        if (input.LA(1) != Token.EOF
            && (input.index() == itemStart || input.index() == lastRecoverIndex)) {
            recognizer.consume();
        }
        while (input.LA(1) != Token.EOF
            && input.LA(1) != LiftPatchRefLexer.RBRACE
            && !COMMAND_STARTS.contains(input.LA(1))) {
            recognizer.consume();
        }
        lastRecoverIndex = input.index();
    }

    /**
     * Whether this error is to be left unreported. A suppressed error still begins
     * the error condition, as a reported one does, so that recovery is the same
     * either way.
     */
    private boolean suppressed(Parser recognizer) {
        if (inErrorRecoveryMode(recognizer)) {
            // The default strategy reports nothing in this state anyway.
            return false;
        }
        if (quiet) {
            beginErrorCondition(recognizer);
            return true;
        }
        quiet = true;
        return false;
    }
}
