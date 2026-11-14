package fr.cnrs.lacito.liftpatchbox.language.parser;

import fr.cnrs.lacito.liftpatchbox.language.Syntax;
import fr.cnrs.lacito.liftpatchbox.language.Pragma;
import fr.cnrs.lacito.liftpatchbox.language.SourceScript;
import fr.cnrs.lacito.liftpatchbox.language.error.SyntaxErrorCode;

import fr.cnrs.lacito.liftpatchbox.error.ModelErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.error.SourcePosition;
import fr.cnrs.lacito.liftpatchbox.metamodel.Metamodel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * The entry point for turning a script into the command model.
 *
 * <p>It dispatches on the surface syntax and applies the version pragma rules of
 * Part 1, section 1.1. The syntax is decided in this order: the {@code syntax=}
 * pragma attribute, when the script declares one; otherwise the syntax the caller
 * asked for; otherwise the file extension, {@code .liftpatchref} for the reference
 * syntax and {@code .liftpatchshort} for the concise one.</p>
 *
 * <p>Reading a script with the wrong syntax is silent rather than loud — a
 * reference script read as a concise document matches no command line and applies
 * nothing — which is why plan mode reports {@code NO_COMMAND_RECOGNIZED} for a
 * document in which not one line was recognized, and why declaring the syntax on
 * the pragma is worth doing.</p>
 */
public final class ScriptParser {

    private static final Logger LOGGER = Logger.getLogger(ScriptParser.class.getName());

    /** The language version this implementation supports. */
    public static final String SUPPORTED_VERSION = "1.1";

    private final Metamodel metamodel;

    /**
     * A parser validating against the standard metamodel of Appendix A.
     */
    public ScriptParser() {
        this(Metamodel.standard());
    }

    /**
     * A parser validating against a given metamodel.
     *
     * @param metamodel the metamodel; an extended one may declare additional
     *                  component types and properties
     */
    public ScriptParser(Metamodel metamodel) {
        this.metamodel = metamodel;
    }

    /**
     * Parse a script held in a string.
     *
     * @param text    the script text
     * @param syntax  the syntax to parse it with, or {@code null} to take the one
     *                the pragma declares and fail if it declares none
     * @param source  the source name, used in diagnostics and plan documents
     * @return the script
     * @throws LiftPatchException carrying every syntax error of the script, sorted by
     *         position, when it does not parse, or declares an unsupported version,
     *         metamodel or pragma attribute
     */
    public SourceScript parse(String text, Syntax syntax, String source) {
        return parseCollecting(text, syntax, source).orThrow();
    }

    /**
     * Parse a script held in a string, collecting every syntax error rather than
     * throwing.
     *
     * <p>Parsing resumes after a failed top-level command of the reference syntax,
     * or a failed command line of the concise one, which is left out of the script
     * returned. Three errors are fatal, and stop parsing with no script: no syntax
     * could be determined, the sigil is malformed, or the language version is not
     * supported, since the errors a 1.1 parser would find in such a script would be
     * noise.</p>
     *
     * @param text   the script text
     * @param syntax the syntax to parse it with, or {@code null} to take the one the
     *               pragma declares
     * @param source the source name, used in diagnostics and plan documents
     * @return the script built from what parsed, and every syntax error
     */
    public ParseOutcome parseCollecting(String text, Syntax syntax, String source) {
        Syntax declared = declaredSyntax(text);
        Syntax effective = declared != null ? declared : syntax;
        if (effective == null) {
            return new ParseOutcome(null, List.of(LiftPatchError.of(
                ModelErrorCode.SYNTAX_ERROR,
                "the surface syntax of " + source + " is not declared by a pragma and was not "
                    + "given; pass --syntax, use a .liftpatchref or .liftpatchshort extension, or write "
                    + "%liftpatch 1.1 syntax=\"LiftPatchRef\"",
                SourcePosition.UNKNOWN)));
        }
        LOGGER.log(Level.FINE, () ->
            "Parsing " + source + " as " + effective.pragmaName());

        List<LiftPatchError> errors = new ArrayList<>();
        SourceScript script;
        try {
            script = effective == Syntax.REFERENCE
                ? parseReference(text, source, errors)
                : new ShortScriptParser(source, metamodel).parse(text, errors);
        } catch (LiftPatchException fatal) {
            errors.addAll(fatal.errors());
            return new ParseOutcome(null, errors);
        }
        LiftPatchError version = checkVersion(script.pragma());
        if (version != null) {
            return new ParseOutcome(null, List.of(version));
        }
        checkPragmaAttributes(script.pragma(), errors);
        return new ParseOutcome(script, errors);
    }

    /**
     * Parse a script held in a file, collecting every syntax error rather than
     * throwing.
     *
     * @param file   the file to read, in UTF-8
     * @param syntax the syntax to parse it with, or {@code null} to take it from
     *               the pragma or from the file extension
     * @return the script built from what parsed, and every syntax error
     * @throws IOException if the file cannot be read
     */
    public ParseOutcome parseFileCollecting(Path file, Syntax syntax) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return parseCollecting(text, effectiveSyntax(file, syntax), file.getFileName().toString());
    }

    /**
     * Parse a script held in a file.
     *
     * @param file   the file to read, in UTF-8
     * @param syntax the syntax to parse it with, or {@code null} to take it from
     *               the pragma or from the file extension
     * @return the script
     * @throws IOException        if the file cannot be read
     * @throws LiftPatchException carrying every syntax error, when the script does not parse
     */
    public SourceScript parseFile(Path file, Syntax syntax) throws IOException {
        return parseFileCollecting(file, syntax).orThrow();
    }

    private static Syntax effectiveSyntax(Path file, Syntax syntax) {
        return syntax != null ? syntax : Syntax.fromFileName(file.getFileName().toString());
    }

    /**
     * Parse the reference syntax. ANTLR recovers after a syntax error and reports the
     * first error of each item (see {@link ItemErrorStrategy}); the visitor then
     * builds the items it can.
     */
    private SourceScript parseReference(String text, String source, List<LiftPatchError> errors) {
        LiftPatchRefLexer lexer = new LiftPatchRefLexer(CharStreams.fromString(text));
        CollectingErrorListener listener = new CollectingErrorListener();
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        LiftPatchRefParser parser = new LiftPatchRefParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        ItemErrorStrategy strategy = new ItemErrorStrategy();
        parser.setErrorHandler(strategy);
        parser.addParseListener(strategy.listener());
        LiftPatchRefParser.ScriptContext tree = parser.script();
        errors.addAll(listener.errors());
        return new RefScriptVisitor(source).buildScript(tree, errors);
    }

    /**
     * Read the {@code syntax=} attribute of the pragma without parsing the script,
     * which cannot be done before the syntax is known.
     */
    private static Syntax declaredSyntax(String text) {
        for (String raw : text.split("\\R", -1)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!line.startsWith("%liftpatch")) {
                // A pragma may be preceded by blank lines, comments and prose, so the
                // scan continues; it stops at the first command-looking line, which
                // no pragma may follow.
                if (line.startsWith("\\")) {
                    continue;
                }
                int at = line.indexOf("%liftpatch");
                if (at < 0) {
                    continue;
                }
                line = line.substring(at);
            }
            int at = line.indexOf("syntax=");
            if (at < 0) {
                return null;
            }
            int q = at + "syntax=".length();
            if (q >= line.length()) {
                return null;
            }
            char quote = line.charAt(q);
            int close = line.indexOf(quote, q + 1);
            return close < 0 ? null : Syntax.fromPragmaName(line.substring(q + 1, close));
        }
        return null;
    }

    /**
     * The first pragma rule of Part 1, section 1.1: the version must be supported.
     *
     * @return the error, or {@code null} when the version is supported or no
     *         pragma is declared
     */
    private static LiftPatchError checkVersion(Pragma pragma) {
        if (pragma == null || SUPPORTED_VERSION.equals(pragma.version())) {
            return null;
        }
        return LiftPatchError.of(
            SyntaxErrorCode.UNSUPPORTED_LANGUAGE_VERSION,
            "this implementation supports LiftPatch " + SUPPORTED_VERSION
                + ", and the script declares " + pragma.version(),
            pragma.position());
    }

    /**
     * The two other pragma rules of Part 1, section 1.1: the attributes must be
     * known, and a required metamodel must be the one that is loaded.
     */
    private void checkPragmaAttributes(Pragma pragma, List<LiftPatchError> errors) {
        if (pragma == null) {
            return;
        }
        for (String name : pragma.attributes().keySet()) {
            if (!name.equals(Pragma.SIGIL)
                && !name.equals(Pragma.METAMODEL)
                && !name.equals(Pragma.SYNTAX)) {
                errors.add(LiftPatchError.of(
                    SyntaxErrorCode.UNKNOWN_PRAGMA_ATTRIBUTE,
                    "the version pragma of LiftPatch 1.1 carries `sigil`, `metamodel` and "
                        + "`syntax`, and no other attribute; found `" + name + "`",
                    pragma.position()));
            }
        }
        String required = pragma.metamodel();
        if (required != null && !required.equals(metamodel.metamodelId())) {
            errors.add(LiftPatchError.of(
                SyntaxErrorCode.UNSUPPORTED_METAMODEL,
                "the script requires the metamodel `" + required + "`, and the one loaded is `"
                    + (metamodel.metamodelId() == null ? "1.0" : metamodel.metamodelId()) + "`",
                pragma.position()));
        }
    }
}
