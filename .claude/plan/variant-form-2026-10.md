# A variant is identified by its form, not by a target: plan (2026-10-16)

## Why

The previous change (`lift-api-alignment-2026-10.md`) left `variant` with a single
property, `target`, a reference that is required and is the whole natural identity. That
does not match what a LIFT variant is, nor what FieldWorks writes. The 1,377 variants of
the Tuwuli file all have a form and a `morph-type` trait, and none has a `ref`. As a
result, they can be selected only by ordinal, and a variant like them cannot be created.

**Decision.** `variant` has no `target`. It has one property, `form`:

| Component | Property | Datatype | Qualifier | Required at creation | Natural identity | Writable |
|---|---|---|---|---:|---:|---:|
| Variant | `form` | multitext | O | yes, at least one qualified value | yes: one qualified value | yes |

The qualifier is `O`: LIFT writes a variant's forms in the object language
(`<form lang="tww">`), as it does an entry's. The kind of a variant stays a
`variant-type` trait. A link from a variant to another entry or sense, when one is
wanted, is a `relation` child, which `variant` already admits.

**Consequence, accepted.** The LIFT `ref` attribute of a variant goes out of the
language. A script neither reads nor writes it, and the dangling-reference report of
§12.4.2 no longer covers it, because that report is driven by the `reference`
properties of the metamodel. The dictionary model still holds a `ref` loaded from a
file, and `LiftModel.delete` still unlinks and restores it around a deletion
(`detachInboundReferences` works on lift-api's reference registry, not on the
metamodel). `relation.target` is untouched, and is now the only `reference` property
of the metamodel.

The specification is revised by deleting the superseded wording, with no
"earlier versions said…" notes.

---

## 1. Specification (`specification.md`)

Line numbers are those of the current file, for orientation.

### 1.1 Part 1

- **§4.1 property table** (l. 452): replace
  `| Variant | target | reference | — | yes | yes | yes |` with
  `| Variant | form | multitext | O | yes, at least one qualified value | yes: one qualified value | yes |`.
  Place the row before `Relation`, as now.
- **§4.1 paragraph on variants** (l. 476–485): keep "no `type`, kind as a
  `variant-type` trait". Add that a variant is identified by its form, and that a link
  to another entry or sense is written as a `relation` child. New example:
  ```LiftPatchRef
  create variant(form@tww = "mamii")
    under entry[form@tww = "mami", hn = 1] {
    create trait(type = "variant-type", value = "Dialectal Variant")
  }
  ```
- **§5.1 selector examples** (l. 798): `variant[target = sense[id = "pig-44"]]` →
  `variant[form@tww = "mamii"]`. The list keeps a strategy-S3 example on a multitext,
  like `sense[gloss@en = "pig"]` just above it.
- Unchanged, checked: §2 and §3 tables (component name, parentage), §3.1 component kind
  (`ordered`), §5.2 rules on multitext identity (generic), and §5.3.2 `hn`, which is
  stated for `entry` only.

### 1.2 Part 2

- **§8.1 duplicate detection** (l. 2165–2166): "two variants are identical if they have
  the same target" → "if they have the same qualified form".
- **§8.2 `upsert`** (l. 2280): "target for a variant" → "qualified form for a
  variant".
- **§10 Reference properties**, which now concerns `relation` alone:
  - l. 2806–2808: delete the paragraph "A `variant` raises no such question…".
  - l. 2810: "Note that `relation` and `variant` carry no `id`" → "Note that a
    `relation` carries no `id`… so strategy S2 is not available on it".
  - l. 2829, example of a `reference` predicate in a selector:
    `variant[target = entry[id = "pig-44"]]` →
    `relation[type = "synonym", target = entry[id = "pig-44"]]`. A relation selector
    must give both identity properties, so the type is needed.
  - l. 2854–2859: "These rules apply to the `target` property of the two component
    types that carry one: `variant` and `relation`…" → "These rules apply to the
    `target` property of `relation`, the one component type that carries one. Its
    natural identity is `type + target`: two relations with the same type and the same
    target under one parent are duplicates…".
- Unchanged, checked: l. 1633 `create relation(type = "variant", …)` is a relation
  type; §12.4.2 is generic and its example is a relation.

### 1.3 Part 3

- **Reference values** (l. 3660–3664): the example of a `reference` predicate in a
  selector, `d /e[f="mami"] v[a = /memi]` →
  `d /e[f="mami"] r[y="synonym", a = /memi]`. The surrounding text speaks of
  `reference` properties and needs no other change.
- **Unnamed-argument table** (l. 4547): `| Variant | — | — |` →
  `| Variant | 1 | form |`. Optionally add the example `c /mami v("mamii")`.
- **Paragraph after the table** (l. 4560–4563): "The two component types with no
  unnamed form are exactly the two whose required properties include a `reference`:
  `relation` takes a `type` and a `target`, `variant` a `target`…" → "The one component
  type with no unnamed form, besides the singleton `category`, is `relation`, the one
  whose required properties include a `reference`: it takes a `type` and a `target`,
  and a target is a path, not a string (section "3")…". The example
  `c /mami r(y="synonym", a = /memi)` stays.
- Unchanged, checked: the short code `v`, the property code `f`, and the depth
  abbreviations (§6.1), which cover entry / sense / example only.

### 1.4 Appendix A

```json
"variant": {
  "componentKind": "ordered",
  "children": ["pronunciation", "relation", "trait", "annotation", "field"],
  "naturalIdentity": ["form"],
  "unnamedArguments": ["form"],
  "properties": {
    "form": { "datatype": "multitext", "qualifier": "object", "required": true }
  }
}
```

### 1.5 Appendix C (grammars)

No change. `component-type` lists `variant`. `property-name` (l. 5224) already lists
`form`, and keeps `target` for `relation`.

### 1.6 Appendix D (corpus)

Change the variant cases added last time, in the spec and in `corpus.json` alike (D.4
rule 2):

| id | change |
|---|---|
| C-089 | script `create variant(form@tww = "mamii") under entry[form@tww = "mami", hn = 1] { create trait(...) }`; effect `propertySet form@tww = "mamii"` instead of `target`; description "a variant has a form and no type…" |
| C-090 | unchanged meaning ("a variant has no type property"); script uses `form@tww = "mamii"` instead of `target = …` |
| C-091 | "the natural identity of a variant is its form": two `create variant(form@tww = "mamii")` under the same entry → `CANNOT_CREATE_DUPLICATE`, dynamic, command 2 |
| C-092 (new) | `create variant(target = entry[form@tww = "memi"]) under …` → `PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE`, static |
| C-093 (new) | `create variant() under entry[form@tww = "mami", hn = 1]`, with no form → `MISSING_REQUIRED_PROPERTY`, static |

---

## 2. Code

### 2.1 Metamodel resource

`src/main/resources/fr/cnrs/lacito/liftpatchbox/metamodel-1.0.json`: the same
`variant` block as Appendix A §1.4. Appendix A and the resource must stay identical
(checked as last time, by comparing the parsed JSON).

### 2.2 `model/LiftModel.java`

- **`multitext(ref, property)`** (l. ~536): add
  `case LiftVariant v -> "form".equals(property) ? Optional.of(v.getForms()) : Optional.empty();`.
  `setQualified`, `removeQualified`, `applyMultitexts` and selection by form then work
  through the generic path.
- **`scalarOfNode`** (l. 463): delete the `LiftVariant` case. A variant has no scalar
  property, and `default -> Optional.empty()` covers it.
- **`setScalar`** (l. 635–641): the `LiftVariant` case only throws
  `unsupportedProperty("variant", property)`. Delete it and let `default` do it.
- **`createVariant`** (l. 986–993): drop `setCreatedReference(...)`. The forms arrive
  through `applyMultitexts`, as for an entry.
- **Class javadoc**, variant item: a variant has no `type` and no `target`. It is
  identified by its forms; its kind is a `variant-type` trait; a LIFT `ref` loaded from
  a file is kept by the dictionary model but is outside the language.
- `delete` / `detachInboundReferences` / `reattach`: no change.

### 2.3 Everything else

- **ANTLR grammars** (`LiftPatchRef.g4`, `LiftPatchShort.g4`): no change. Neither
  names a property or a component type: both are `NAME` tokens, checked against the
  metamodel by the validator.
- **`metamodel/ShortCodes.java`**: no change (`v` → `variant`, `f` → `form`; `a` →
  `target` stays for relations).
- **`engine/Executor.java`**: no change. `matchPropertiesOf` and the homophone
  invalidation (l. 501, 740, 831) special-case `form` on `entry` only; the uniqueness
  check on a multitext identity is generic. `findDanglingReferences` is driven by
  the metamodel and simply no longer visits variants.
- **`validation/SemanticValidator.java`**: no change. The `form` rules at l. 989 and
  1016 are inside `if (ENTRY…)` (strategy S4); a variant goes through strategy S3 like
  a sense or an example.
- **`parser/ShortScriptParser.java`**: no change (`ABBREVIATED_PROPERTIES` is per
  depth: entry, sense, example).

### 2.4 Tests

- `ComponentCommandsTest$Create`, the four variant tests:
  - `createsAVariantWithItsKindAsATrait`: `form@tww = "mamii"`; effects
    `created:variant@1`, `set:form@tww=mamii`, then the trait; assert
    `getForms().getForm("tww")`.
  - `refusesATypeOnAVariant`: script with `form@tww` instead of `target`.
  - `refusesTwoVariantsWithTheSameTarget` → `…WithTheSameForm`.
  - `placesARelationOfAVariant`: select the variant with `variant[form@tww = "mamii"]`.
  - New: `refusesATargetOnAVariant` (`PROPERTY_DOES_NOT_EXIST_ON_COMPONENT_TYPE`).
  - New: `createsAVariantInTheConciseSyntax`: `c /e[f="mami", hn=1] v("mamii")`,
    checking the unnamed argument.
  - `createsATraitWithAnOptionRange`: create the variant with a form.
- `PropertyCommandsTest`: a variant's form is writable and its identity —
  `update form@tww = "mamiii" on variant[form@tww = "mamii"] of entry[...]` succeeds;
  re-keying onto a sibling's form raises `CANNOT_CREATE_DUPLICATE`;
  `clear form@tww` on the only form raises `CANNOT_CLEAR_REQUIRED_MULTITEXT`.
- `ConstructsTest$Atomicity.aDeletedRelationOfAVariantComesBackInPlace`: the variant
  is created and selected by form.
- `TuwuliDictionaryTest$Reading.theTraitsOfARealVariantAreReachable`: select the real
  variant by its form, `trait^morph-type under variant[form@tww = "…"] of entry[...]`,
  instead of `variant#1`. Pick an entry whose first variant has a `tww` form that no
  sibling variant shares.
- `corpus.json`: as §1.6.

---

## 3. Order of work

1. Specification (§1) and metamodel resource (§2.1) in one edit, with the corpus
   (§1.6); effect lists checked by running `ConformanceCorpusTest`.
2. `LiftModel` (§2.2).
3. Tests (§2.4).
4. `mvn clean test`, against lift-api HEAD (scratch build) and against the installed
   `0.1-SNAPSHOT`.

## 4. Not changed

- `relation` and its `target`.
- The spec version (`1.0`), as in the previous plan. Both changes alter what a script
  means; bump it to 1.1 if 1.0 has been published.

## 5. Results (2026-10-16)

Implemented as planned, uncommitted. 440 tests pass, against lift-api HEAD
(`d887524`, scratch build) and against the installed `0.1-SNAPSHOT`. Appendix A equals
`metamodel-1.0.json`; Appendix D.3 equals `corpus.json` (93 cases). No code outside
`LiftModel` changed, as §2.3 predicted. The optional Part 3 example `c /mami v("mamii")`
was not added to the specification; the concise test
`createsAVariantInTheConciseSyntax` checks it instead.
