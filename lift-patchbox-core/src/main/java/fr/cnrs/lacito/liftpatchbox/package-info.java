/**
 * The LiftPatch command model for the LIFT dictionary data model, independent of
 * any surface syntax.
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.LiftPatchEngine} is the front door: it runs
 * the static validation pass on a script, and either plans the script against a
 * dictionary or applies it in one transaction. A script is built with
 * {@link fr.cnrs.lacito.liftpatchbox.ast.LiftPatch}, or produced by a parser of
 * one of the surface syntaxes, which the {@code lift-patchbox-language} module
 * provides.</p>
 *
 * <p>The packages divide the work along the lines the specification itself draws
 * (Part 1, and Part 2, sections 8 to 12):</p>
 *
 * <ul>
 *   <li>{@code metamodel} — the normative metamodel of Appendix A, loaded from the
 *       very JSON document the specification prints;</li>
 *   <li>{@code ast} — the immutable command model, and the fluent API that builds
 *       it;</li>
 *   <li>{@code validation} — every error that depends only on the script and the
 *       metamodel, collected rather than thrown, so that a script is reported in
 *       full before anything is applied;</li>
 *   <li>{@code engine} — selector resolution, execution, the plan document of
 *       Part 2, section 12.4.1, and the run summary;</li>
 *   <li>{@code model} — the one place that knows how the metamodel maps onto the
 *       {@code lift-api} dictionary model, and the journal that makes a script
 *       atomic;</li>
 *   <li>{@code error} — the error codes of Appendix B that the command model
 *       raises, and the interfaces every error and warning code implements.</li>
 * </ul>
 */
package fr.cnrs.lacito.liftpatchbox;
