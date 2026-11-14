package fr.cnrs.lacito.liftpatchbox.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftpatchbox.ast.Block;
import fr.cnrs.lacito.liftpatchbox.ast.Item;
import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchException;
import fr.cnrs.lacito.liftpatchbox.error.ModelErrorCode;
import fr.cnrs.lacito.liftpatchbox.language.error.SyntaxErrorCode;
import fr.cnrs.lacito.liftpatchbox.language.parser.ParseOutcome;
import fr.cnrs.lacito.liftpatchbox.language.parser.ScriptParser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Parsing goes on after a syntax error, in both syntaxes, and reports every syntax
 * error of a script (Part 2, section 12.3); {@link LiftPatchBox#check} adds the
 * static errors of the commands that parsed, without throwing.
 */
@DisplayName("Collecting syntax and static errors")
class SyntaxErrorCollectionTest {

    private final ScriptParser parser = new ScriptParser();
    private final LiftPatchBox box = new LiftPatchBox();

    private ParseOutcome parseShort(String text) {
        return parser.parseCollecting(text, Syntax.CONCISE, "test.liftpatchshort");
    }

    private ParseOutcome parseRef(String text) {
        return parser.parseCollecting(text, Syntax.REFERENCE, "test.liftpatchref");
    }

    private static List<Integer> lines(List<LiftPatchError> errors) {
        return errors.stream().map(e -> e.position().line()).toList();
    }

    private static List<ErrorCode> codes(List<LiftPatchError> errors) {
        return errors.stream().map(LiftPatchError::code).toList();
    }

    @Nested
    @DisplayName("LiftPatchShort")
    class Concise {

        @Test
        @DisplayName("every bad line is reported, in order, with its operation index")
        void everyBadLine() {
            ParseOutcome outcome = parseShort("""
                c e("a"
                c e("b")
                c e("c"
                c e("d")
                c e("e"
                """);
            assertEquals(List.of(1, 3, 5), lines(outcome.errors()));
            assertEquals(List.of(1, 3, 5),
                outcome.errors().stream().map(LiftPatchError::operationIndex).toList());
            assertEquals(2, outcome.script().script().items().size(), "the two good lines are kept");
        }

        @Test
        @DisplayName("the lines indented under a bad line are parsed but left out")
        void badAnchor() {
            ParseOutcome outcome = parseShort("""
                c e("a"
                  c s("x"
                  c s("y")
                c e("b")
                """);
            assertEquals(List.of(1, 2), lines(outcome.errors()));
            assertEquals(1, outcome.script().script().items().size(), "only `c e(\"b\")` is kept");
        }

        @Test
        @DisplayName("a bad indented line leaves its anchor and its siblings in place")
        void badChild() {
            ParseOutcome outcome = parseShort("""
                c e("a")
                  c s("x"
                  c s("y")
                """);
            assertEquals(List.of(2), lines(outcome.errors()));
            List<Item> items = outcome.script().script().items();
            assertEquals(1, items.size());
            assertEquals(1, ((Block) items.get(0)).body().size(), "the sense `y` is kept");
        }

        @Test
        @DisplayName("a tab-indented line and a misaligned line are both reported")
        void scanErrors() {
            ParseOutcome outcome = parseShort("c e(\"a\")\n    c s(\"x\")\n  c s(\"y\")\n\tc e(\"b\")\nc e(\"c\"\n");
            assertEquals(List.of(3, 4, 5), lines(outcome.errors()));
        }

        @Test
        @DisplayName("positions are those of the document, not of the line fragment")
        void documentPositions() {
            ScriptCheck check = box.check("""
                c e("a")
                c e("b")
                s /a/g (g@* = "y")
                """, Syntax.CONCISE, "test.liftpatchshort");
            LiftPatchError error = check.staticErrors().get(0);
            assertEquals(ModelErrorCode.WILDCARD_NOT_ALLOWED, error.code());
            assertEquals(3, error.position().line());
        }
    }

    @Nested
    @DisplayName("LiftPatchRef")
    class Reference {

        @Test
        @DisplayName("one error per broken command, at the start of the command")
        void oneErrorPerCommand() {
            ParseOutcome outcome = parseRef("""
                create entry(form = "a"
                create entry(form = "b")
                create entry(form = "c"
                """);
            assertEquals(List.of(1, 3), lines(outcome.errors()));
        }

        @Test
        @DisplayName("an error the grammar cannot express is collected with the others")
        void visitorErrors() {
            ParseOutcome outcome = parseRef("""
                create entry(form = "a")
                move sense[gloss = "y"] under entry[form = "a"]
                create entry(form = "c"
                """);
            assertEquals(List.of(2, 3), lines(outcome.errors()));
            assertEquals(1, outcome.script().script().items().size());
        }

        @Test
        @DisplayName("a bad line in a block leaves the rest of the block in place")
        void badLineInBlock() {
            ParseOutcome outcome = parseRef("""
                create entry(form = "a") {
                  create sense(gloss = "x"
                  create sense(gloss = "y")
                }
                create entry(form = "b")
                """);
            assertEquals(List.of(2), lines(outcome.errors()));
            List<Item> items = outcome.script().script().items();
            assertEquals(2, items.size());
            assertEquals(1, ((Block) items.get(0)).body().size(), "the sense `y` is kept");
        }
    }

    @Nested
    @DisplayName("fatal errors")
    class Fatal {

        @Test
        @DisplayName("no syntax: one error, no script")
        void noSyntax() {
            ParseOutcome outcome = parser.parseCollecting("c e(\"a\"\n", null, "test.txt");
            assertNull(outcome.script());
            assertEquals(1, outcome.errors().size());
        }

        @Test
        @DisplayName("an unsupported version: one error, no script")
        void unsupportedVersion() {
            ParseOutcome outcome = parseShort("%liftpatch 2.0\nc e(\"a\"\nc e(\"b\"\n");
            assertNull(outcome.script());
            assertEquals(List.of(SyntaxErrorCode.UNSUPPORTED_LANGUAGE_VERSION), codes(outcome.errors()));
        }

        @Test
        @DisplayName("an unknown pragma attribute is not fatal")
        void unknownAttribute() {
            ParseOutcome outcome = parseShort("%liftpatch 1.1 colour=\"red\"\nc e(\"a\"\n");
            assertNotNull(outcome.script());
            assertEquals(List.of(SyntaxErrorCode.UNKNOWN_PRAGMA_ATTRIBUTE, ModelErrorCode.SYNTAX_ERROR),
                codes(outcome.errors()));
        }
    }

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("throws one exception carrying every error, sorted by position")
        void throwsAll() {
            LiftPatchException e = assertThrows(LiftPatchException.class, () -> parser.parse(
                "c e(\"a\"\nc e(\"b\")\nc e(\"c\"\n", Syntax.CONCISE, "test.liftpatchshort"));
            assertEquals(List.of(1, 3), lines(e.errors()));
            assertEquals(1, e.firstError().position().line());
        }
    }

    @Nested
    @DisplayName("check")
    class Check {

        @Test
        @DisplayName("reports syntax errors and the static errors of the other commands")
        void syntaxAndStatic() {
            ScriptCheck check = box.check("""
                create entry(form = "a")
                create entry(form = "b"
                set gloss@* = "y" on sense[gloss = "g"] of entry[form = "a"]
                create sense(gloss = "z") under $never
                """, Syntax.REFERENCE, "test.liftpatchref");
            assertEquals(List.of(2), lines(check.syntaxErrors()));
            assertEquals(List.of(ModelErrorCode.WILDCARD_NOT_ALLOWED, ModelErrorCode.UNKNOWN_LABEL),
                codes(check.staticErrors()));
            assertTrue(check.staticErrors().stream().allMatch(e -> e.operationIndex() == 0),
                "operation indices are unknown in a partly parsed script");
            assertEquals(List.of(2, 3, 4), lines(check.errors()));
        }

        @Test
        @DisplayName("no UNKNOWN_LABEL for a label bound by a command that did not parse")
        void labelOfFailedLine() {
            ScriptCheck check = box.check("""
                ensure entry[form = "b" as $b
                create sense(gloss = "x") under $b
                """, Syntax.REFERENCE, "test.liftpatchref");
            assertEquals(1, check.syntaxErrors().size());
            assertEquals(List.of(), check.staticErrors());
        }

        @Test
        @DisplayName("without a syntax error, the static errors are exactly those of validate")
        void sameAsValidate() {
            String text = """
                c e("a") as $a
                s /a/g (g@* = "y")
                c $never s("z")
                """;
            ScriptCheck check = box.check(text, Syntax.CONCISE, "test.liftpatchshort");
            assertEquals(List.of(), check.syntaxErrors());
            assertEquals(
                box.validate(box.parse(text, Syntax.CONCISE, "test.liftpatchshort")).errors(),
                check.staticErrors());
        }

        @Test
        @DisplayName("a clean script is ok")
        void clean() {
            assertTrue(box.check("c e(\"a\")\n", Syntax.CONCISE, "test.liftpatchshort").isOk());
        }
    }
}
