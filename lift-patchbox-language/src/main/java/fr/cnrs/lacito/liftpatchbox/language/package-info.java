/**
 * The two surface syntaxes of LiftPatch — LiftPatchRef and LiftPatchShort — over
 * the command model of {@code lift-patchbox-core}.
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.language.LiftPatchBox} is the front door:
 * it parses a script into a {@link fr.cnrs.lacito.liftpatchbox.language.SourceScript},
 * and hands the command model to a {@link fr.cnrs.lacito.liftpatchbox.LiftPatchEngine}
 * to validate, plan or apply it.</p>
 *
 * <ul>
 *   <li>{@code parser} — the two ANTLR grammars, the concise-syntax preprocessor
 *       that applies the rules no token grammar can express, the short codes, and
 *       the visitors;</li>
 *   <li>{@code error} — the error and warning codes of Appendix B that only a
 *       parser can raise;</li>
 *   <li>{@code cli} — the command-line tool.</li>
 * </ul>
 */
package fr.cnrs.lacito.liftpatchbox.language;
