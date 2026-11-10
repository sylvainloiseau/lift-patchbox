package fr.cnrs.lacito.liftpatchbox.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * A whole script: a sequence of items.
 *
 * <p>A script is the unit of atomicity (Part 2, section 12.2): it either
 * succeeds completely or leaves the dictionary exactly as it was.</p>
 *
 * <p>A script carries no trace of the text it may have been parsed from, beyond two
 * labels that the plan document reports: the name of its source, and the name of
 * the surface syntax it was written in, which is {@code null} for a script built
 * in Java.</p>
 *
 * @param items      the top-level items, in source order
 * @param source     the name of the source, for diagnostics and plan documents
 * @param syntaxName the name of the surface syntax, such as {@code "LiftPatchRef"},
 *                   or {@code null} when the script was not parsed
 */
public record Script(
    List<Item> items,
    String source,
    String syntaxName
) {

    /**
     * Canonical constructor, taking an unmodifiable copy of the items.
     *
     * @param items      the top-level items
     * @param source     the name of the source
     * @param syntaxName the name of the surface syntax, or {@code null}
     */
    public Script {
        items = List.copyOf(items);
    }


    /**
     * Every command of the script, blocks included, flattened in source order.
     *
     * <p>A block header that is a command is included at the position it occupies,
     * before the commands of its body, which is the numbering rule of Part 2,
     * section 12.4.1.</p>
     *
     * @return the commands, in source order
     */
    public List<Command> allCommands() {
        List<Command> out = new ArrayList<>();
        collectCommands(items, out);
        return out;
    }

    private static void collectCommands(List<Item> items, List<Command> out) {
        for (Item i : items) {
            switch (i) {
                case Command c -> out.add(c);
                case Block b -> {
                    if (b.header() instanceof BlockHeader.Anchor a) {
                        out.add(a.command());
                    }
                    collectCommands(b.body(), out);
                }
                case Directive unused -> {
                    // Directives are not commands, whether or not they are operations.
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Item i : items) {
            sb.append(i).append(System.lineSeparator());
        }
        return sb.toString();
    }
}
