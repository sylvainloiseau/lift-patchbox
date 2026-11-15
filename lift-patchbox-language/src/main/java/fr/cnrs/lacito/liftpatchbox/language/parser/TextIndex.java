package fr.cnrs.lacito.liftpatchbox.language.parser;

import fr.cnrs.lacito.liftpatchbox.error.SourceRange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The lines of a document, and the conversion of ANTLR's character indexes into the
 * offsets of {@link SourceRange}, built once per document.
 *
 * <p>A line ends where {@code String.split("\\R")} ends it: {@code \r\n}, or one of
 * {@code \n}, {@code \u000B}, {@code \u000C}, {@code \r}, {@code \u0085},
 * {@code  }, {@code  }. Offsets and columns count UTF-16 code units. ANTLR reads
 * the text as code points ({@code CharStreams.fromString}): its indexes are converted with
 * {@link #utf16(int)}, which is the identity for a document holding no character outside
 * the Basic Multilingual Plane, checked once.</p>
 */
public final class TextIndex {

    private final String text;
    private final int[] lineStarts;
    /** The UTF-16 offsets of the supplementary characters, sorted; empty when there is none. */
    private final int[] supplementary;

    /**
     * The index of a document.
     *
     * @param text the whole text given to the parser
     */
    public TextIndex(String text) {
        this.text = text;
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        List<Integer> surrogates = null;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\r' -> {
                    if (i + 1 < n && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                    starts.add(i + 1);
                }
                case '\n', '\u000B', '\u000C', '\u0085', ' ', ' ' -> starts.add(i + 1);
                default -> {
                    if (Character.isHighSurrogate(c) && i + 1 < n && Character.isLowSurrogate(text.charAt(i + 1))) {
                        if (surrogates == null) {
                            surrogates = new ArrayList<>();
                        }
                        surrogates.add(i);
                        i++;
                    }
                }
            }
        }
        this.lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
        this.supplementary = surrogates == null ? new int[0]
            : surrogates.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * The text indexed.
     *
     * @return the document
     */
    public String text() {
        return text;
    }

    /**
     * The number of lines: one more than the line breaks.
     *
     * @return the line count
     */
    public int lineCount() {
        return lineStarts.length;
    }

    /**
     * The offset of the first character of a line.
     *
     * @param line a 1-based line number
     * @return its offset
     */
    public int lineStart(int line) {
        return lineStarts[line - 1];
    }

    /**
     * The line holding an offset; an offset at a line break belongs to the line it ends.
     *
     * @param offset a 0-based offset, at most the length of the text
     * @return the 1-based line
     */
    public int lineOf(int offset) {
        int i = Arrays.binarySearch(lineStarts, offset);
        return i >= 0 ? i + 1 : -i - 1;
    }

    /**
     * The range between two offsets of the text.
     *
     * @param start the offset of the first character
     * @param end   the offset after the last character, at least {@code start}
     * @return the range, with its lines and columns
     */
    public SourceRange range(int start, int end) {
        int s = clamp(start);
        int e = Math.max(s, clamp(end));
        int startLine = lineOf(s);
        int endLine = lineOf(e);
        return new SourceRange(startLine, s - lineStart(startLine) + 1, endLine, e - lineStart(endLine) + 1, s, e);
    }

    /**
     * An empty range at an offset.
     *
     * @param offset a 0-based offset
     * @return the place
     */
    public SourceRange point(int offset) {
        return range(offset, offset);
    }

    /**
     * The range of a whole line, without its line break and its trailing whitespace.
     *
     * @param line a 1-based line number
     * @return the range of its content
     */
    public SourceRange lineRange(int line) {
        int start = lineStart(line);
        int end = line < lineStarts.length ? lineStarts[line] : text.length();
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return range(start, end);
    }

    /**
     * The UTF-16 offset of a code-point index: ANTLR's {@code Token.getStartIndex()} on a
     * stream read with {@code CharStreams.fromString} of this text.
     *
     * @param codePointIndex an index in code points
     * @return the offset in UTF-16 code units
     */
    public int utf16(int codePointIndex) {
        if (supplementary.length == 0) {
            return codePointIndex;
        }
        // Each supplementary character before the result adds one unit. Count those whose
        // own code-point index is below codePointIndex: the k-th one (0-based) is at code
        // point supplementary[k] - k.
        int lo = 0;
        int hi = supplementary.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (supplementary[mid] - mid < codePointIndex) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return codePointIndex + lo;
    }

    private int clamp(int offset) {
        return Math.max(0, Math.min(offset, text.length()));
    }
}
