---
name: spec-consistency
description: Use whenever specification.md is edited (a rule, a property, a component type, an error code, an effect, a directive, the grammar, an example or a corpus case), and before reporting such an edit as done. Lists every place of the specification and of the code that must change with it so that the document stays consistent with itself, with its machine-readable twins (metamodel JSON, corpus JSON, ErrorCode) and with the implementation, and how to verify it.
---

# Keeping specification.md consistent

`specification.md` states most facts more than once: a rule in the prose, in a table,
in Appendix A, in an example, in a corpus case, in the grammar. Three of these have a
twin in the code that must stay **identical**, and the implementation must keep
passing the examples and the corpus. A change is not done until every copy agrees.

Work in this order:

1. Find every place the change touches, with the checklists below and a `grep` of the
   names involved.
2. Edit them all in the same change.
3. Run the verification at the end. Report what it showed, not what you expect.

## 0. Rules of style that apply to every edit

- **Delete superseded wording outright.** Do not write "earlier versions said…" or
  "formerly"; keep the normative content and remove the history. Two things are not
  superseded and stay unless the user says otherwise: deprecated but still accepted
  features (the `[index = n]` alias of `#n`), and Appendix B.3 (codes that must not be
  raised).
- **Counts in prose are facts.** Grep for number words and fix them: "the only
  read-only property" / "the two", "every property but one/two", "exactly these
  eleven `kind` values", "seven built-in feature sets", "the ten totals", "three cases a
  lexicographer meets". Javadoc in the code repeats several of them (§7).
- **Section references** are written `section "x.y"` (Part 2/3: `Part 3, section "x"`).
  When adding, moving or renumbering a section, grep for the old number. Part 3 has its
  own numbering (§1–§9), distinct from Part 1/2.
- **Line numbers** in plans go stale after the first edit: locate passages by their text.
- **Version:** `Version: 1.0` at the top, and the resource name `metamodel-1.0.json`. A
  change to what an existing script means calls for a version bump once 1.0 has been
  published. Ask, do not decide silently.
- **Last edit**: update the Last edit field in the yaml header when the specification is edited.

## 1. The twins that must be identical (checked by script)

| In the spec | Twin | Rule |
|---|---|---|
| Appendix A, the JSON block | `src/main/resources/fr/cnrs/lacito/liftpatchbox/metamodel-1.0.json` | parsed JSON equal |
| Appendix D.3, the corpus array | `src/test/resources/fr/cnrs/lacito/liftpatchbox/corpus.json` | text identical; ids `C-001…` in sequence |
| Appendix B.1 / B.2 tables | `src/main/java/.../error/ErrorCode.java` | same codes, same kind (STATIC/DYNAMIC) |
| §4.1 property table | Appendix A | qualifier, required, natural identity, writable agree; one row per property |
| Part 3 unnamed-argument table | Appendix A `unnamedArguments` | same mapping, same order |

`python3 .claude/skills/spec-consistency/check_twins.py` checks all five. It exits 1 and
prints `MISMATCH:` lines on disagreement. It also lists the codes no corpus case
raises (D.4 rule 3, a SHOULD), which is informational.

## 2. Changing a property, a component type or the metamodel

A property's datatype, qualifier, required/identity/writable flags, or a component's
children, kind or identity:

- **§4.1 property table**: the row(s), and the explanatory paragraphs after it. A
  variant, for instance, has its own paragraph there.
- **§2/§3 tables**: the component name table, and the parentage table (Appendix A
  `children` is authoritative).
- **§3.1** component kinds (ordered / typed / singleton) and the lists of examples of
  each kind.
- **Appendix A**: the component's block (`componentKind`, `children`,
  `naturalIdentity`, `unnamedArguments`, `properties`). Then the reading rules after
  the JSON, which carry invariants and counts ("every property but two").
- **Natural identity** is repeated in:
  - §5.1 selector examples;
  - §5.2 (uniqueness invariant);
  - §8.1 duplicate detection ("two senses are identical if…");
  - §8.2 `upsert` (`MISSING_IDENTITY_PROPERTY` parenthetical "… for an example, … for a
    variant");
  - §10 (references, for `relation`);
  - Part 3 examples and the paragraph about selectors needing the whole identity.
- **Writable / read-only** (`"writable": false`):
  - §4.5 (the list of read-only properties and why);
  - §3.1 (`move` on typed components);
  - §5.3.4 (type selectors, "the exceptions");
  - §5.4 (applicability, "may not be written");
  - §8.5 (`move`);
  - §9.3.1/§9.3.2 (`clear` tables, add a row);
  - Appendix A reading rule for `writable`;
  - the `PROPERTY_IS_READ_ONLY` row of Appendix B.
- **Unnamed arguments**: the Part 3 table, and the paragraph after it ("the component
  types with no unnamed form are…").
- **References** (`datatype: reference`): §10 in full; Part 3 reference-value rules;
  the dangling-reference report (§12.4.2) is driven by the metamodel.
- **Code:**
  - `model/LiftModel.java`: how the property is read and written (`scalarOfNode`,
    `setScalar`, `multitext`, `createX`), and the class javadoc "The metamodel and the
    object model agree", which lists the deliberate declarations;
  - `metamodel/PropertyDef.java` / `Metamodel.java` comments that count read-only
    properties.

  The validator and executor are metamodel-driven; check whether a rule special-cases
  a name (`grep '"form"'` found entry-only cases in `Executor` and `SemanticValidator`).

## 3. Changing or adding an error code

- **Appendix B.1 (static) or B.2 (dynamic)**: one row, with the sections defining it.
  Never reuse a B.3 code.
- **§12.3**: the sentence listing dynamic errors by name.
- **The section that defines the rule**, which names the code.
- **`error/ErrorCode.java`**: enum constant, kind, one-line javadoc.
- **The corpus**: a case raising it (D.4 rule 3), with `kind` static/dynamic and the
  right `commandIndex`.
- **Static vs dynamic** must match what the implementation can know: a static error
  is decided from the script alone, before any dictionary is consulted.

## 4. Changing plan mode: effects, summary, operations

- **§12.4.1:**
  - the effects table, and the sentence "`effects` uses exactly these N `kind` values";
  - the `summary` JSON example;
  - the operation-numbering rules (what is an operation);
  - the rules about effects of `delete`, `move` and renames (one effect per command).
- **Appendix D.1**: the exhaustive list of compared effect fields.
- **Code:**
  - `engine/Effect.java` (kinds, record fields, factories, javadoc counts);
  - `engine/Summary.java` (counters, javadoc count);
  - `engine/Plan.java` (JSON writer: effects and summary);
  - `test/.../ConformanceCorpusTest.java` (`render`, `expectedEffects`);
  - `test/.../PatchTestSupport.java` (`render`, the strings tests assert).

## 5. Changing the grammar, a keyword or a directive

- **Appendix C.1** (LiftPatchRef) and **C.2** (LiftPatchShort) productions, and their
  "Notes on the productions" (constraints the grammar cannot express, and the static
  error that enforces each).
- **ANTLR files**, which must accept the same language:
  - `src/main/antlr4/.../LiftPatchRef.g4`;
  - `src/main/antlr4/.../LiftPatchShort.g4`.

  A new keyword token must also be added to `anyName` (Ref) / `bareWord` (Short), or it
  stops being usable as a name, a language code (`to` is Tongan) or a label. Keyword
  tokens are declared before `NAME` / `BARE_WORD`.
- **§1.2**: the lists of what a script contains, in both syntaxes.
- **Part 3 §1.1 recognition rule**: which lines are commands or directives, so that
  prose is not misread. Code: `parser/ShortLinePreprocessor.java` (`isDirective`, …).
- **Directives:**
  - the AST record (`ast/Directive.java`, sealed);
  - `parser/DirectiveBuilder.java`, used by `RefScriptVisitor` and `ShortScriptParser`;
  - `ast/LiftPatch.java` factories;
  - `validation/SemanticValidator.java` (operation counting, static checks);
  - `engine/Executor.java` (`executeDirective`).
- **§11** (what a block body may hold) and Part 3 indented blocks, for anything allowed
  inside a block.

## 6. Examples and the conformance corpus

- **Every fenced ` ```LiftPatchRef ` / ` ```LiftPatchShort ` example is parsed and
  statically validated** by `SpecificationExamplesTest`.
  - An example meant to be illegal must be listed in its `DELIBERATELY_ILLEGAL` map
    with its code, or the test fails.
  - Prefer these fences to ` ```text `, which nothing checks. Inline examples in
    backticks are not checked either: keep them consistent by hand.
- **Corpus (Appendix D.3)**, D.4 rule 2: **a change of outcome changes the corpus in the
  same edit**.
  - New cases get the next ids.
  - Edit the spec and `corpus.json` identically (§1).
  - Cases run against the standard fixture, `src/test/resources/.../fixture-standard.json`:
    check what it contains (two homophones `mami`, `memi`; languages `tww`, `tpi`, `en`,
    `fr`; `Noun` categories; one `synonym` relation), so that a case fails for the
    reason it claims.
  - Write the expected effects, then let `ConformanceCorpusTest` confirm them. Do not
    trust a hand-written effect list it has not run.
- **Normative examples SHOULD appear in the corpus** (D.4 rule 1).

## 7. Prose that the code repeats

Javadoc mirrors the specification and goes stale silently. Grep the code for the
section number and for the rule's key phrase. Known places:

- `model/LiftModel.java` class javadoc: the declarations that keep metamodel and model
  aligned;
- `metamodel/PropertyDef.java` (`isReadOnly`) and `Metamodel.java` (`writable` default):
  the read-only count;
- `engine/Effect.java`: kind count, compared fields;
- `engine/Summary.java`: totals count;
- `error/ErrorCode.java`: one javadoc per code;
- `engine/LanguageScope.java`: default-language rules;
- the grammars' header comments.

## 8. The dictionary model (lift-api)

The language must not promise what lift-api cannot store or check (`../lift-api`,
dependency `io.github.sylvainloiseau:lift-api`, version in the `lift-api.version` property of `pom.xml`). Before specifying a new
operation, check that the model offers it. Examples:

- a feature set's id was immutable until lift-api `cd3998a`;
- `LiftField.specificationProperty()` is read-only, which is why `field.type` is
  read-only;
- LIFT gives a variant no type.

If lift-api must change, write that plan in `../lift-api/.claude/plan/` and say so.

## 9. Verification (run all, report the output)

```bash
python3 .claude/skills/spec-consistency/check_twins.py   # §1 twins
mvn -o clean test                                         # clean: the IDE's compiler can leave stale classes
```

`mvn clean test` runs:
- `SpecificationExamplesTest` (every example);
- `ConformanceCorpusTest` (every case);
- `ParserSmokeTest`;
- the behavioural suites.

Then:

- grep for the old wording, names and counts you replaced: no hit should remain;
- grep for `section "x.y"` of any section you added, moved or renamed;
- if the change alters the meaning of existing scripts, say so to the user, with the
  version question of §0.
