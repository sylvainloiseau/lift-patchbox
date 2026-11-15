package fr.cnrs.lacito.liftpatchbox.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftpatchbox.ast.Block;
import fr.cnrs.lacito.liftpatchbox.ast.BlockHeader;
import fr.cnrs.lacito.liftpatchbox.ast.Command;
import fr.cnrs.lacito.liftpatchbox.ast.Constructor;
import fr.cnrs.lacito.liftpatchbox.ast.Initializer;
import fr.cnrs.lacito.liftpatchbox.ast.Item;
import fr.cnrs.lacito.liftpatchbox.error.LiftPatchError;
import fr.cnrs.lacito.liftpatchbox.error.SourceRange;
import fr.cnrs.lacito.liftpatchbox.language.parser.TextIndex;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The ranges of the command model and of the errors: every construct knows where it
 * starts and ends in the text, in UTF-16 offsets, lines and columns.
 */
class RangeTest {

    private static final LiftPatchBox BOX = new LiftPatchBox()
        .withDefaultObjectLanguage("tww").withDefaultMetaLanguage("en");

    private static String text(String doc, SourceRange r) {
        return doc.substring(r.startOffset(), r.endOffset());
    }

    private static List<Item> items(String doc, Syntax syntax) {
        ScriptCheck check = BOX.check(doc, syntax, "t");
        assertTrue(check.syntaxErrors().isEmpty(), () -> "syntax errors: " + check.syntaxErrors());
        return check.script().script().items();
    }

    /** Lines and columns agree with offsets, as a TextIndex of the document gives them. */
    private static void assertConsistent(String doc, SourceRange r) {
        TextIndex index = new TextIndex(doc);
        assertEquals(index.range(r.startOffset(), r.endOffset()), r);
    }

    @Nested
    class Concise {

        @Test
        void aCommandCoversItsTextWithoutIndentationCommentOrLineBreak() {
            String doc = "Some prose about pigs.\n  \nc e(\"mami\", s(\"pig\"))   #\"tag\" a comment\nMore prose.\n";
            List<Item> items = items(doc, Syntax.CONCISE);
            assertEquals(1, items.size());
            SourceRange r = items.get(0).range();
            assertEquals("c e(\"mami\", s(\"pig\"))", text(doc, r));
            assertEquals(3, r.startLine());
            assertEquals(1, r.startColumn());
            assertEquals(3, r.endLine());
            assertConsistent(doc, r);
        }

        @Test
        void subNodesCoverTheirOwnText() {
            String doc = "c e(\"mami\", s(\"pig\"))\n";
            Command.Create c = (Command.Create) items(doc, Syntax.CONCISE).get(0);
            Constructor entry = c.constructor();
            assertEquals("e(\"mami\", s(\"pig\"))", text(doc, entry.range()));
            Initializer.Embedded sense = entry.initializers().stream()
                .filter(i -> i instanceof Initializer.Embedded).map(Initializer.Embedded.class::cast)
                .findFirst().orElseThrow();
            assertEquals("s(\"pig\")", text(doc, sense.range()));
        }

        @Test
        void windowsAndOldMacLineEndsCountAsOneBreak() {
            for (String nl : List.of("\r\n", "\r", "\n")) {
                String doc = "prose" + nl + "c e(\"mami\")" + nl + "prose" + nl + "c e(\"efe\")" + nl;
                List<Item> items = items(doc, Syntax.CONCISE);
                assertEquals("c e(\"efe\")", text(doc, items.get(1).range()), "line end " + nl.length());
                assertEquals(4, items.get(1).range().startLine());
                assertConsistent(doc, items.get(1).range());
            }
        }

        @Test
        void columnsAndOffsetsCountUtf16Units() {
            String x = "𝑥";        // U+1D465, mathematical italic x: two UTF-16 units
            String doc = "c e(\"" + x + "mami\", s(\"pig\"))\n";
            Command.Create c = (Command.Create) items(doc, Syntax.CONCISE).get(0);
            Initializer.Embedded sense = c.constructor().initializers().stream()
                .filter(i -> i instanceof Initializer.Embedded).map(Initializer.Embedded.class::cast)
                .findFirst().orElseThrow();
            assertEquals("s(\"pig\")", text(doc, sense.range()));
            assertEquals(doc.indexOf("s(\"pig\")") + 1, sense.range().startColumn());
        }

        @Test
        void aBlockCoversItsHeaderAndItsBody() {
            String doc = "c e(\"mimi\")\n  c s(\"lizard\")\n    s (d@en = \"A lizard\")\nprose\n";
            List<Item> items = items(doc, Syntax.CONCISE);
            assertEquals(1, items.size());
            Block block = (Block) items.get(0);
            assertEquals("c e(\"mimi\")\n  c s(\"lizard\")\n    s (d@en = \"A lizard\")", text(doc, block.range()));
            assertEquals("c e(\"mimi\")", text(doc, ((BlockHeader.Anchor) block.header()).command().range()));
            assertConsistent(doc, block.range());
        }

        @Test
        void aSyntaxErrorCoversTheOffendingToken() {
            String doc = "prose\nc e(\"mami\", s(\"pig\") ]\n";
            ScriptCheck check = BOX.check(doc, Syntax.CONCISE, "t");
            assertFalse(check.syntaxErrors().isEmpty());
            SourceRange r = check.syntaxErrors().get(0).range();
            assertEquals(2, r.startLine());
            assertTrue(r.startOffset() >= doc.indexOf("c e("), r.toString());
            assertTrue(r.endOffset() <= doc.indexOf("]") + 1, r.toString());
            assertConsistent(doc, r);
        }

        @Test
        void aStaticErrorIsInsideItsCommand() {
            String doc = "prose\nc e(\"mami\", s(\"pig\")) as $a\ns $b (d@en = \"x\")\n";
            ScriptCheck check = BOX.check(doc, Syntax.CONCISE, "t");
            assertTrue(check.syntaxErrors().isEmpty());
            LiftPatchError e = check.staticErrors().get(0);
            assertEquals("UNKNOWN_LABEL", e.code().id());
            int line3 = doc.indexOf("s $b");
            assertTrue(e.range().startOffset() >= line3 && e.range().endOffset() <= doc.length() - 1, e.range().toString());
            assertFalse(e.range().isEmpty());
        }
    }

    @Nested
    class Reference {

        @Test
        void commandsAndBlocksCoverTheirText() {
            String doc = "create entry(form@tww = \"mami\") {\n   create sense(gloss@en = \"pig\")\n}\ncreate entry(form@tww = \"efe\")\n";
            List<Item> items = items(doc, Syntax.REFERENCE);
            assertEquals(2, items.size());
            Block block = (Block) items.get(0);
            assertEquals("create entry(form@tww = \"mami\") {\n   create sense(gloss@en = \"pig\")\n}", text(doc, block.range()));
            assertEquals("create sense(gloss@en = \"pig\")", text(doc, block.body().get(0).range()));
            assertEquals("create entry(form@tww = \"efe\")", text(doc, items.get(1).range()));
            assertEquals(4, items.get(1).range().startLine());
            assertConsistent(doc, items.get(1).range());
        }

        @Test
        void columnsAndOffsetsCountUtf16Units() {
            String x = "𝑥";
            String doc = "create entry(form@tww = \"" + x + "\") {\n   create sense(gloss@en = \"pig\")\n}\n";
            Block block = (Block) items(doc, Syntax.REFERENCE).get(0);
            assertEquals("create sense(gloss@en = \"pig\")", text(doc, block.body().get(0).range()));
            assertConsistent(doc, block.body().get(0).range());
        }

        @Test
        void aMissingBraceIsReportedNearTheEnd() {
            String doc = "create entry(form@tww = \"mami\") {\n   create sense(gloss@en = \"pig\")\n";
            ScriptCheck check = BOX.check(doc, Syntax.REFERENCE, "t");
            assertFalse(check.syntaxErrors().isEmpty());
            SourceRange r = check.syntaxErrors().get(0).range();
            assertTrue(r.isKnown());
            assertConsistent(doc, r);
        }
    }

    @Test
    void thePlanDocumentStillGivesTheStartLineAndColumn() {
        String doc = "prose\n  \nc e(\"mami\", s(\"pig\"))\n";
        String json = BOX.plan(fr.cnrs.lacito.liftpatchbox.StandardFixture.build(),
            BOX.parse(doc, Syntax.CONCISE, "t")).toJson();
        assertTrue(json.contains("\"position\": { \"line\": 3, \"column\": 1 }"), json);
    }

    @Test
    void sourceRangeOperations() {
        SourceRange a = new SourceRange(1, 1, 1, 5, 0, 4);
        SourceRange b = new SourceRange(2, 3, 2, 6, 10, 13);
        assertEquals(new SourceRange(1, 1, 2, 6, 0, 13), a.cover(b));
        assertEquals(a, a.cover(SourceRange.UNKNOWN));
        assertEquals(b, SourceRange.UNKNOWN.cover(b));
        assertTrue(a.contains(0) && a.contains(3) && !a.contains(4));
        assertTrue(SourceRange.point(1, 5, 4).isEmpty());
        assertEquals(4, a.length());
        assertFalse(SourceRange.UNKNOWN.isKnown());
        assertEquals("line 2, column 3", b.toString());
        assertEquals("unknown position", SourceRange.UNKNOWN.toString());
    }

    @Test
    void textIndexLinesAndCodePoints() {
        String x = "𝑥";
        TextIndex index = new TextIndex("ab\r\ncd\ne" + x + "f g");
        assertEquals(4, index.lineCount());
        assertEquals(0, index.lineStart(1));
        assertEquals(4, index.lineStart(2));
        assertEquals(7, index.lineStart(3));
        assertEquals(12, index.lineStart(4));
        assertEquals(3, index.lineOf(8));
        // code points: a b \r \n c d \n e x f ... ; x is code point 8, its UTF-16 offset 8; f is cp 9, offset 10
        assertEquals(8, index.utf16(8));
        assertEquals(10, index.utf16(9));
        assertEquals(new SourceRange(2, 1, 2, 3, 4, 6), index.range(4, 6));
    }
}
