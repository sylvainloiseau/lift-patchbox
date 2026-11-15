package fr.cnrs.lacito.liftpatchbox.error;

/**
 * The span of a script's source that a construct, an error or a plan operation comes
 * from: where it starts and where it ends.
 *
 * <ul>
 *   <li>Lines are 1-based; a line ends at {@code \n}, {@code \r\n} or {@code \r} (any
 *       {@code \R}).</li>
 *   <li>Columns are 1-based and offsets 0-based, both counted in UTF-16 code units of the
 *       text given to the parser, which is what a Java {@code String} index is: a
 *       character outside the Basic Multilingual Plane counts two.</li>
 *   <li>The end is exclusive: {@code endOffset} is the index just after the last
 *       character, {@code endLine} and {@code endColumn} its line and column. An empty
 *       range marks a place, such as where a missing {@code }} was expected.</li>
 *   <li>A construct's range covers the characters that make it, without indentation,
 *       trailing whitespace, trailing comment or line break.</li>
 * </ul>
 *
 * <p>{@link #UNKNOWN} stands for a construct no text was read for — one built in Java
 * with {@link fr.cnrs.lacito.liftpatchbox.ast.LiftPatch}, or a whole-script error.</p>
 *
 * @param startLine   the line of the first character, or -1 when unknown
 * @param startColumn the column of the first character, or -1 when unknown
 * @param endLine     the line of {@code endOffset}, or -1 when unknown
 * @param endColumn   the column of {@code endOffset}, or -1 when unknown
 * @param startOffset the index of the first character, or -1 when unknown
 * @param endOffset   the index after the last character, or -1 when unknown
 */
public record SourceRange(int startLine, int startColumn, int endLine, int endColumn,
                          int startOffset, int endOffset) {

    /** The range of something that has no text, such as a whole-script error. */
    public static final SourceRange UNKNOWN = new SourceRange(-1, -1, -1, -1, -1, -1);

    /**
     * Checks a known range: positive lines and columns, ordered ends.
     */
    public SourceRange {
        if (startOffset >= 0) {
            if (endOffset < startOffset || startLine < 1 || startColumn < 1
                || endLine < startLine || endColumn < 1) {
                throw new IllegalArgumentException("not a range: " + startLine + ":" + startColumn
                    + "-" + endLine + ":" + endColumn + " [" + startOffset + ", " + endOffset + ")");
            }
        }
    }

    /**
     * An empty range: a place in the text.
     *
     * @param line   the 1-based line
     * @param column the 1-based column
     * @param offset the 0-based offset
     * @return the range starting and ending there
     */
    public static SourceRange point(int line, int column, int offset) {
        return new SourceRange(line, column, line, column, offset, offset);
    }

    /**
     * Whether this range points at text that was read.
     *
     * @return {@code false} for {@link #UNKNOWN}
     */
    public boolean isKnown() {
        return startOffset >= 0;
    }

    /**
     * Whether the range holds no character.
     *
     * @return {@code true} for a place, or for {@link #UNKNOWN}
     */
    public boolean isEmpty() {
        return endOffset <= startOffset;
    }

    /**
     * The number of UTF-16 code units the range holds.
     *
     * @return {@code endOffset - startOffset}, 0 when unknown
     */
    public int length() {
        return isKnown() ? endOffset - startOffset : 0;
    }

    /**
     * Whether an offset is inside the range.
     *
     * @param offset a 0-based offset
     * @return {@code true} when {@code startOffset <= offset < endOffset}
     */
    public boolean contains(int offset) {
        return isKnown() && offset >= startOffset && offset < endOffset;
    }

    /**
     * The smallest range containing this one and another. An unknown range is ignored.
     *
     * @param other another range of the same text
     * @return the range covering both
     */
    public SourceRange cover(SourceRange other) {
        if (other == null || !other.isKnown()) {
            return this;
        }
        if (!isKnown()) {
            return other;
        }
        boolean thisStarts = startOffset <= other.startOffset;
        boolean thisEnds = endOffset >= other.endOffset;
        return new SourceRange(
            thisStarts ? startLine : other.startLine, thisStarts ? startColumn : other.startColumn,
            thisEnds ? endLine : other.endLine, thisEnds ? endColumn : other.endColumn,
            thisStarts ? startOffset : other.startOffset, thisEnds ? endOffset : other.endOffset);
    }

    /**
     * Where the range starts, as an editor displays it: {@code line 4, column 1}.
     *
     * @return the start, or {@code unknown position}
     */
    @Override
    public String toString() {
        return isKnown() ? "line " + startLine + ", column " + startColumn : "unknown position";
    }
}
