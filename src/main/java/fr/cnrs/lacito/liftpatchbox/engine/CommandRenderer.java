package fr.cnrs.lacito.liftpatchbox.engine;

import fr.cnrs.lacito.liftpatchbox.ast.Chain;
import fr.cnrs.lacito.liftpatchbox.ast.Command;
import fr.cnrs.lacito.liftpatchbox.ast.Directive;
import fr.cnrs.lacito.liftpatchbox.ast.Step;

/**
 * How the engine writes a command, a chain, a step or a directive as text: in the
 * {@code command} field of a plan operation, when the command carries no source
 * text of its own, and in the messages of the errors it raises.
 *
 * <p>The default, {@link #REFERENCE_NOTATION}, uses each node's {@code toString()},
 * which writes the command model in the notation of Part 2 — the notation the
 * specification uses to define what each command means. A caller that shows the
 * commands to its users in another form, such as a surface syntax of its own,
 * supplies another renderer.</p>
 */
public interface CommandRenderer {

    /**
     * The renderer writing each node in the notation of Part 2, by its {@code toString()}.
     */
    CommandRenderer REFERENCE_NOTATION = new CommandRenderer() {
        @Override
        public String render(Command command) {
            return command.toString();
        }

        @Override
        public String render(Directive directive) {
            return directive.toString();
        }

        @Override
        public String render(Chain chain) {
            return chain.toString();
        }

        @Override
        public String render(Step step) {
            return step.toString();
        }
    };

    /**
     * Write a command.
     *
     * @param command the command
     * @return its text
     */
    String render(Command command);

    /**
     * Write a directive.
     *
     * @param directive the directive
     * @return its text
     */
    String render(Directive directive);

    /**
     * Write a chain.
     *
     * @param chain the chain
     * @return its text
     */
    String render(Chain chain);

    /**
     * Write a step.
     *
     * @param step the step
     * @return its text
     */
    String render(Step step);
}
