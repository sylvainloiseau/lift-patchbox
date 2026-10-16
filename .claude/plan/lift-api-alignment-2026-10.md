# Aligning lift-patchbox with lift-api HEAD: plan (2026-10-16)

## Why

lift-api commit `59d408e` (2026-10-14) changed three things lift-patchbox relies on.
Compiled against lift-api HEAD, `model/LiftModel.java` has 9 compile errors; with those
stubbed, 3 of 414 tests fail at run time. The later lift-api work (builder-only
construction, `49268b6` and the uncommitted edits) breaks nothing here.

| # | lift-api change | Effect on lift-patchbox | Decision |
|---|---|---|---|
| 1 | The `xxxProperty()` lists (`sensesProperty()`, `examplesProperty()`…) are read-only views | `LiftModel.insert` / `reinsert` reorder through them: `UnsupportedOperationException` on every `create … at` and on positional restores | use the indexed `addX(int, …)` lift-api now offers for every ordered component |
| 2 | `LiftField.specificationProperty()` is read-only: the parent keys its fields by the definition's name. No `retypeField` exists | `LiftModel.retypeField` no longer compiles | **2a**: `field.type` becomes read-only in the language, like `trait.type` |
| 3 | `LiftVariant` has no type any more (`getType`, `setType`, `LiftHeader.getVariantTypeManager()` removed). LIFT gives a variant no type attribute; FieldWorks writes the kind as a `variant-type` trait | `variant.type` (required, natural identity `type + target`) has nowhere to live | **3b**: `type` is removed from `variant`; its natural identity becomes `target`; the kind of variant is an ordinary `trait` |

**Decided within 3b:** the natural identity of `variant` is `["target"]`. Two variants
of one entry pointing to the same entry or sense are duplicates
(`CANNOT_CREATE_DUPLICATE`). An empty identity (as for `entry`) was the alternative, but it
would make `upsert variant(...)` and strategy S3 meaningless, and every selection of a
variant positional.

The specification is revised by deleting the superseded wording outright, with no
"earlier versions said…" notes.

---

## 1. Specification (`specification.md`)

Line numbers are those of the current file, for orientation only.

### 1.1 `field.type` read-only (2a)

- **§4.1 property table** (l. 470): `| Field | type | … | yes |` → Writable
  `**no** (section "4.5")`, as the Trait row.
- **§4.5 Read-only properties** (l. 576–609):
  - "`trait.type` is the only read-only property" (l. 604) → `trait.type` and
    `field.type` are the two read-only properties.
  - Rationale paragraph: keep the trait reason (key *and* datatype definition), add
    the field's (the key it is stored under is the name of the definition that
    describes it, and the data model fixes it at construction).
  - "Every other typed component type — `note`, `field`, `translation` — is re-keyed…"
    → `note` and `translation`.
  - Optionally a second example line after the trait ones:
    `set type = "review" on field^editorial of entry[...]   # PROPERTY_IS_READ_ONLY`.
- **§3.1 typed components** (l. 381): "unless that `type` is read-only, as a
  `trait`'s is" → "as a `trait`'s or a `field`'s is".
- **§5.3.4 Type selectors**, last paragraph (l. 1312–1315): "The one exception is the
  `trait`" → the two exceptions, `trait` and `field`.
- **§5.4** (l. 1376): "A `trait`'s `type` is the one that may not" → "A `trait`'s
  and a `field`'s `type` may not".
- **§8.5 `move`** (l. 2413): "read-only as a `trait`'s is" → "as a `trait`'s or a
  `field`'s is".
- **§9.3.2** table (l. 2730): add the row
  `clear type on field[...]` | `Field.type`: read-only (section "4.5") | `PROPERTY_IS_READ_ONLY`.
- **Appendix A**:
  - `field.properties.type`: add `"writable": false`.
  - Reading rule for `writable` (l. 4979): "for every property but one" → "but two".
- **Appendix B** (l. 5005): the `PROPERTY_IS_READ_ONLY` row's "such as the `type` of a
  `trait`" → "the `type` of a `trait` or of a `field`".
- Unchanged, checked: the field examples at l. 904, 919, 1028, 1936, 2022, 2130,
  4553 write `text`, select by `type`, or create; none writes `field.type`.

### 1.2 `variant` without `type` (3b)

- **§4.1 property table** (l. 452–453): delete the `Variant | type` row;
  `Variant | target` → natural identity `yes`.
- **§4.1 or §3, new short paragraph after the table**: LIFT gives a variant no type.
  The kind of a variant is recorded, as FieldWorks does, by a `trait` named
  `variant-type`, written like any trait:
  ```LiftPatchRef
  create variant(target = entry[form@tww = "memi"]) under entry[form@tww = "mami", hn = 1] {
    create trait(type = "variant-type", value = "Dialectal Variant")
  }
  ```
  Put this example in a ` ```LiftPatchRef ` fence only if `SpecificationExamplesTest`
  accepts it against the standard fixture; otherwise use ` ```text `.
- **§5.1 selector examples** (l. 785):
  `variant[type="dialectal", target = sense[id = "pig-44"]]` →
  `variant[target = sense[id = "pig-44"]]`.
- **§8.1 duplicate detection** (l. 2152): "two variants are identical if they have the
  same type and target" → "if they have the same target".
- **§8.2 `upsert`** (l. 2267): "type + target for a variant" → "target for a variant".
- **§10 Reference properties**:
  - l. 2770–2775, "**The selector of a `relation` or a `variant` must name its current
    target.**": rewrite for `relation` alone (`type + target`). Add one sentence: a
    variant's identity *is* its target, so a unique selector on a variant names
    its target anyway.
  - l. 2792: "`relation` and `variant` carry no `id`": unchanged.
  - l. 2811 example: `variant[type="dialectal", target = entry[id = "pig-44"]]` →
    `variant[target = entry[id = "pig-44"]]`.
  - l. 2836–2840: "Since `type + target` is the natural identity of both…" → the
    identity of a `relation` is `type + target`, that of a `variant` is `target`. Two
    relations with the same type and target, or two variants with the same target,
    under one parent are duplicates…
- **Part 3**:
  - l. 3634–3638: "the natural identity of a `relation` and of a `variant` is
    `type + target`" → keep for `relation` only.
  - l. 3644 example: `d /e[f="mami"] v[y="dialectal", a = /memi]` →
    `d /e[f="mami"] v[a = /memi]`.
  - l. 3856–3857 (tokenisation example): `v[y="dialectal", a = /memi]` is used to show
    that a space inside brackets does not split a token. Replace it with
    `r[y="synonym", a = /memi]`, which still has the inner space, in both places.
  - l. 4541–4545: "`relation` and `variant` take a `type` and a `target`" →
    "`relation` takes a `type` and a `target`, `variant` a `target`"; the `Variant | — | —`
    row of the unnamed-argument table (l. 4528) stays: `target` is a reference, so
    still no unnamed form.
- **Appendix A**, `variant` (l. 4838–4846): delete the `type` property;
  `"naturalIdentity": ["target"]`; `unnamedArguments` stays `[]`.
- Unchanged, checked: l. 1620 `create relation(type = "variant", …)` is a relation
  type; the grammars of Appendix C name no property; the short code `v` stays.

### 1.3 Conformance corpus (Appendix D.3)

D.4 rule 2: an outcome change changes the corpus in the same edit. No existing case
uses a variant or writes `field.type`, so only additions are needed:

| id | section | script (outline) | expected |
|---|---|---|---|
| C-088 | 4.5 | `set type = "review" on field^editorial of entry[form@tww = "memi"]` | `PROPERTY_IS_READ_ONLY` (static, no field needed) |
| C-089 | 4.1 | `create variant(target = entry[form@tww = "memi"]) under entry[form@tww = "mami", hn = 1] { create trait(type = "variant-type", value = "Dialectal Variant") }` | ok: variant created, target set, trait created |
| C-090 | 4.1 | `create variant(type = "dialectal", target = …) under …` | `PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE` |
| C-091 | 5.2 | two `create variant(target = entry[form@tww = "memi"])` under the same entry | `CANNOT_CREATE_DUPLICATE` |

Exact effect lists are written from what plan mode produces once the implementation is
done, then checked by hand against §12.4.1.

---

## 2. Implementation

### 2.1 Metamodel resource

`src/main/resources/fr/cnrs/lacito/liftpatchbox/metamodel-1.0.json`: the same two edits
as Appendix A (`field.type` `"writable": false`; `variant` loses `type`, identity
`["target"]`). Keep it identical to Appendix A.

Javadoc and comments that say `trait.type` is the only read-only property:
- `metamodel/PropertyDef.java` l. 41 (`isReadOnly`);
- `metamodel/Metamodel.java` l. 149 ("every property but one").

`SemanticValidator` needs no change: it dispatches on `PropertyDef.isReadOnly()`.

### 2.2 `model/LiftModel.java`

**Lists (1).**
- `insert` (l. 1187): take an `IntConsumer`/`BiConsumer` that calls `addX(index, c)`
  when a position is given, and `addX(c)` otherwise. Delete the remove/re-add through
  the list. Clamp as today (a position past the end appends).
- `reinsert` (l. 1344): same, with `addX(position, c)`.
- `reattach` (l. 1295): restore *every* ordered component at its original position
  with the indexed add. Today etymology, variant relations, variant pronunciations,
  media, reversals, traits and annotations are restored at the end. Typed components
  (note, field, translation) keep the plain add.
- Update the javadoc of `insert` ("only then moved into place through the observable
  list") to the new mechanism.

**Field (2a).**
- `setScalar`, `case LiftField f` (l. 711–716): no writable property is left on a
  field except `text`, which goes through the multitext path. Make `type` fall to
  `unsupportedProperty("field", property)`, as every unknown property does; the
  validator refuses it statically before it gets there.
- Delete `retypeField` (l. 862–878).
- Class javadoc (l. 64–71): the item on `trait.type` covers `field.type` too, with
  lift-api's reason (`LiftField.specificationProperty()` is read-only because the parent
  keys its fields by the definition's name). "A `note` and a `translation` are keyed by
  their type too, but theirs is writable": unchanged.

**Variant (3b).**
- `scalarOfNode`, `case LiftVariant` (l. 457–460): delete the `"type"` case.
- `setScalar`, `case LiftVariant` (l. 632–641): delete the `"type"` case.
- `createVariant` (l. 1005–1014): no type; `target` only. The variant's kind arrives as
  an ordinary `trait` child through `createTrait`, which already handles variants
  (`AbstractExtensibleWithoutField`).
- Check `createTrait` with a `variant-type` definition coming from a file. In the
  Tuwuli file it may be declared with an option range; `new LiftTrait(def, String)`
  then goes through `LiftTrait.setValue`, which refuses an unknown feature. If so, use
  `LiftTrait.parseValue(def, value)` (creates the feature, as the reader does) for every
  trait, not only variant ones.
- Remove the `Feature`/`FeatureSet` imports if they become unused.

### 2.3 Tests

- `PropertyCommandsTest$ReadOnly`: `set` / `update` / `clear type` on `field^…`
  refused with `PROPERTY_IS_READ_ONLY` statically; the concise spelling
  `s /memi f^editorial (y = "review")`; `set text@en` on the field still allowed.
- `ComponentCommandsTest`:
  - `$Create`: variant with a target, plus its `variant-type` trait; `type` refused
    (`PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE`); same target twice refused
    (`CANNOT_CREATE_DUPLICATE`); `at` on a variant and on a relation of a variant
    (positional insert on lists not exercised today).
  - `$Delete` / `ConstructsTest$Atomicity`: delete a variant's relation, an
    annotation and a trait in a failing script and check that each comes back at its
    original position (covers `reattach`).
- `TuwuliDictionaryTest$Reading`: select a variant of the Tuwuli file and read its
  `trait^variant-type` (e.g. a `Free Variant`).
- `corpus.json`: cases C-088 to C-091, identical to Appendix D.3.
- `SpecificationExamplesTest` picks up the edited examples by itself.

---

## 3. Order of work

1. Specification (§1) and metamodel resource (§2.1) in one edit, with the corpus
   (§1.3, effects filled in at step 3).
2. `LiftModel` (§2.2).
3. Tests (§2.3); fill the effect lists of C-089 and C-091 from plan mode.
4. Publish lift-api (`mvn deploy`, or `mvn install` to try locally). Patchbox resolves
   `0.1-SNAPSHOT` and the latest published build (2026-10-15 12:52 UTC) predates
   `49268b6`.
5. `mvn clean test` in patchbox. Use `clean`: an incremental `test-compile` skipped
   recompiling and hid the breakage the first time.

## 4. Not changed

- Spec version (`Version: 1.0`) and the resource name `metamodel-1.0.json`. Removing
  `variant.type` changes what a script means, so if version 1.0 has been published,
  this should be 1.1 instead.
- Forward references, header-text registration, `Form.getMultiText()`: new in lift-api,
  no effect here (411 of 414 tests pass against HEAD).
- `LiftModel` still creates components with the model's constructors rather than the
  builders; that still works and is outside this plan.

## 5. Results (2026-10-16)

Implemented, uncommitted. 432 tests pass, both against lift-api HEAD (`d887524`, built
from source) and against the `0.1-SNAPSHOT` installed in `~/.m2`. Appendix A and
`metamodel-1.0.json` are identical; Appendix D.3 and `corpus.json` are identical
(91 cases).

Deviations from the plan:

- **Trait values (§2.2).** The check turned up a real defect: `new LiftTrait(def, String)`
  works only for a `string` definition; for an option-range definition (how FieldWorks
  declares `variant-type` when it exports the range) it throws a `NullPointerException`.
  `createTrait` and `set value` on a trait now go through
  `LiftTrait.parseValue` / `setTypedValue`, as lift-api's `TraitBuilder` does. Test:
  `ComponentCommandsTest$Create.createsATraitWithAnOptionRange`.
- **Tuwuli test (§2.3).** The Tuwuli file has no `variant-type` on any `<variant>`: its
  1,377 variants have a form and a `morph-type` trait and no `ref`, and its 15
  `variant-type` traits sit on `_component-lexeme` relations. The test instead reaches a
  real variant's `morph-type` trait by ordinal.
- **`reattach`** restores a reversal through `HasReversal`, so a sub-reversal (parent: a
  reversal) no longer hits a `ClassCastException`.
- `insert` takes the read-only sibling list for its size; the `xxxProperty()` lists,
  deprecated for removal in lift-api, are no longer used.

Left open:

- The metamodel gives `variant` no `form` property, and makes `target` required and its
  whole identity. FieldWorks variants (as in the Tuwuli file) have a form and no target,
  so they can be reached only by ordinal (`variant#n`), and cannot be created as they
  are. This predates the present change, but 3b makes `target` the only identity.
  *Resolved by `variant-form-2026-10.md`: a variant is now identified by its `form`.*
