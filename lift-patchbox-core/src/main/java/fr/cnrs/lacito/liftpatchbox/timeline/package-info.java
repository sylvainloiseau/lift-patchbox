/**
 * The timeline of a series of scripts applied to a dictionary: what each operation
 * changed, which entries it changed, and the state of the dictionary at any point
 * of the series.
 *
 * <p>{@link fr.cnrs.lacito.liftpatchbox.timeline.Timeline} is made by
 * {@link fr.cnrs.lacito.liftpatchbox.LiftPatchEngine#record(fr.cnrs.lacito.liftapi.LiftDictionary, java.util.List)}.
 * It applies the series once, each script in its own transaction, keeping every
 * change the dictionary reported; seeking a position then reverts or reapplies
 * those changes, on the same objects, so the dictionary is never copied.</p>
 */
package fr.cnrs.lacito.liftpatchbox.timeline;
