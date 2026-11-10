# lift-patchbox-core

The LiftPatch command model for the LIFT dictionary data model, independent of
any surface syntax. It describes a sequence of mutations of a dictionary —
creating, upserting, ensuring, deleting and moving components, setting,
updating and clearing their properties, managing languages and declarations —
checks it against the metamodel, and applies it to a `lift-api` dictionary in
one transaction, or reports what it would do without changing anything.

The two surface syntaxes of the language, and the command-line tool, are in
[`lift-patchbox-language`](../lift-patchbox-language/README.md), which produces
this module's command model. An application that builds its mutations in Java
needs this module alone:

```xml
<dependency>
  <groupId>io.github.sylvainloiseau</groupId>
  <artifactId>lift-patchbox-core</artifactId>
  <version>…</version>
</dependency>
```

## Usage

```java
LiftDictionary dictionary = LiftDictionary.loadDictionaryFromFile(new File("tww.lift"));

Script script = LiftPatch.script()
    .add(LiftPatch.create("sense")
            .init("gloss", "en", "pig")
            .under(LiftPatch.chain(LiftPatch.step("entry").eq("form", "tww", "mami").hn(1)))
            .as("pig"))
    .add(LiftPatch.set()
            .assign("definition", "en", "A four-legged terrestrial animal")
            .on(LiftPatch.chain(LiftPatch.label("pig"))))
    .build();

LiftPatchEngine engine = new LiftPatchEngine()
    .withDefaultObjectLanguage("tww")
    .withDefaultMetaLanguage("en");

Plan plan = engine.plan(dictionary, script);      // validate and resolve, change nothing
if (plan.isOk()) {
    engine.apply(dictionary, script);             // validate and apply, in one transaction
}
```

- **Static validation always runs first.** `validate(script)` reports every
  error that depends only on the script and the metamodel; a script with a
  static error changes nothing.
- **A script is atomic.** A command that fails while it is resolved or applied
  rolls back every change already applied, and `apply` throws a
  `LiftPatchException` carrying the error.
- **Plan mode** returns the same `Plan` as `apply` — operations, effects, warnings,
  dangling references, summary — and rolls everything back. `Plan.toJson()`
  writes the plan document. A script built in Java has no surface syntax, so
  its `syntax` is `null`.
- **Errors** carry an `ErrorCode`, whose `id()` is the name Appendix B gives it.
  The codes this module raises are the constants of `ModelErrorCode`.
- `withRenderer(CommandRenderer)` sets how commands are written in plan
  documents and error messages; by default, in the notation of Part 2.

## Timeline

A series of scripts can be applied with a record of everything it did:

```java
try (Timeline timeline = engine.record(dictionary, List.of(script1, script2, script3))) {
    timeline.seek(timeline.positions().get(12));        // the dictionary as it was then
    for (TimelineOperation op : timeline.historyOf("e-3")) {
        // every operation that changed entry e-3, its senses, examples...
    }
}                                                       // back to the end of the series
```

- Each script runs in its own transaction, as `apply` would run it. A script with
  a static error is `REJECTED` and has no position; a script whose command fails
  is `FAILED` and rolled back, and the next script starts from the state before
  it.
- `positions()` are the start, then one position after each operation. The
  positions of a failed script can be sought, for debugging: they are not
  `isCommitted()`, and show the state the script had reached.
- Seeking changes the dictionary in place, by reverting or reapplying the changes
  the dictionary model reported (`ModelChange` in `lift-api`), on the same
  objects: a component keeps its identity and its id. Nothing else may change the
  dictionary while the timeline is open; `addSeekListener` tells a GUI when to
  read it again.
- `historyOf(entryId)` lists the committed operations that changed the entry's
  subtree, including its creation and its deletion; `historyOf(entryId, true)`
  adds those of failed scripts. A move between entries belongs to both; a
  reference left dangling by a deletion does not change the entry holding it.

## Specification

`specification.md`, at the root of the repository, is normative. The sections
that define this module's behaviour are:

- **Part 1** (sections 2 to 6): the LIFT dictionary model, components and their
  parentage, properties, selection and natural identity, creation and
  initializers;
- **Part 2, sections 8 to 12**: what each component command, property command
  and reference property does, blocks, and the execution model — atomicity,
  static and dynamic errors, plan mode and the plan document;
- **Appendix A**, the metamodel, of which
  `src/main/resources/fr/cnrs/lacito/liftpatchbox/metamodel-1.0.json` is the
  machine-readable copy, and **Appendix B**, the error codes.

The concrete syntax of Part 2 (section 7, and the grammar of Appendix C.1) and
Part 3 belong to `lift-patchbox-language`.

## Packages

| Package | What it holds |
|---|---|
| `fr.cnrs.lacito.liftpatchbox` | `LiftPatchEngine`, the front door. |
| `…ast` | The immutable command model, and `LiftPatch`, the fluent API that builds it. |
| `…validation` | Every error that depends only on the script and the metamodel, collected rather than thrown. |
| `…engine` | Selector resolution, execution, the plan document, the run summary. |
| `…model` | The mapping of the metamodel onto the `lift-api` dictionary model, and the journal of the dictionary's changes, which makes a script atomic. |
| `…timeline` | A series of scripts applied to a dictionary: positions, seeking, the history of each entry. |
| `…metamodel` | The normative metamodel of Appendix A. |
| `…error` | The error and warning codes, and the interfaces every code implements. |

## Tests

`EngineTest` drives the engine with scripts built in Java, and `TimelineTest`
the timeline. The fixtures — the
standard dictionary of Appendix D.2 (`StandardFixture`) and the Tuwuli field
dictionary (`TuwuliDictionary`) — and `EngineTestSupport` are published in this
module's test jar, which the language module's tests reuse.
