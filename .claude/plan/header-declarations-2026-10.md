# Managing the dictionary's declarations: languages, feature sets, features, field and trait definitions — plan (2026-10-16)

## Why

A script can create a language (`language-create`) and nothing else in the dictionary
header. It cannot:

- delete a language;
- create, delete or rename a feature set, or a feature in one;
- declare a field or a trait with a data model (`create field(type = "x")` silently
  creates a definition with the default model, and an option trait cannot be declared
  at all).

The request proposed:

```text
lift-dictionary fs create "name"
lift-dictionary f create "f_name" on "fs_name"
lift-dictionary f rename "f_name" on "fs_name" as "new_name"
```

Shortcomings of that draft, which the syntax below avoids:

- **Two ways to write a directive.** It is a second directive family next to
  `language-create object = "tpi"`, with a different shape (prefix + subcommand +
  verb). Every header directive should read the same way.
- **Cryptic.** `fs` and `f` are opaque in the reference syntax, which is the verbose
  and explicit one. LiftPatchShort may abbreviate later, but the reference syntax
  should not.
- **Reused keywords.** `on` already means "the component whose property is written"
  and `as` already binds a label (`as $x`). Reusing them for "in this feature set" and
  "the new name" gives two keywords two meanings each.
- **`fs rename "name"` has no new name.**

## 1. Proposed syntax (recommended)

A single family of **declaration directives**, built like `language-create`: one
hyphenated keyword naming *what* and *which verb*, followed by its arguments.

```text
# Languages (2.1.3)
language-create object = "tpi"
language-delete object = "tpi"

# Feature sets and features (2.1.4)
feature-set-create "variant-type"
feature-set-rename "variant-type" to "variant-kind"
feature-set-delete "variant-kind"

feature-create "Dialectal Variant" in "variant-type"
feature-rename "Dialectal Variant" in "variant-type" to "Dialectal"
feature-delete "Dialectal" in "variant-type"

# Field and trait definitions (2.1.5)
field-create "editorial"                           # model = multitext (default)
field-create "literal-meaning" model = multistring
trait-create "cv-pattern"                          # model = string (default)
trait-create "variant-type" model = option range = "variant-type"
trait-create "semantic-domain" model = option-collection range = "semantic-domain"
field-delete "editorial"
trait-delete "cv-pattern"
```

Design points:

- **Names stay quoted strings**, as language codes do: feature ids routinely contain
  spaces (`"Dialectal Variant"`, FLEx semantic domains `"1.5 Plant"`).
- **`in` names the feature set, `to` names the new name.** Neither has another
  meaning in the reference syntax.
- **`model` is a bare word** from a closed list, like `object` / `meta`: LIFT's own
  data-model names. Traits take `string` (default), `integer`, `datetime`, `option`,
  `option-collection`, `option-sequence`; fields take `multitext` (default),
  `multistring`. `range` is required with an `option…` model and forbidden
  otherwise.
- **Vocabulary.** The spec says *feature set* and *feature*, as lift-api does, and
  states once that LIFT calls them a *range* and a *range element*.
- **Identical in both syntaxes**, exactly as the language directives are today. A
  directive line is recognised by its keyword (Part 3, recognition rule 2).

**Alternative considered, not recommended: header items as components.**
`create feature-set(name = "variant-type")`,
`create feature(name = "Dialectal") under feature-set[name = "variant-type"]`,
`set name = … on feature[...]`, `delete feature[...]`. This reuses the verbs,
selectors and blocks, but:

- it puts the header into the component tree, which today has `entry` as its only
  root child (§2, Appendix A);
- it gives renaming the meaning of writing an identity property that every component
  of the dictionary depends on;
- it separates these declarations from `language-create`, which is a directive.

The directive family keeps the header out of the component model, where the
specification already keeps languages.

## 2. Semantics

Every new directive is, like `language-create`:

- **an operation** with an index (§12.4.1);
- **part of the script's transaction**: rolled back by a later failure (§12.2);
- **not scoped**: its effect outlives the enclosing block;
- **allowed in a block body**, and **checked against the dictionary as the preceding
  commands left it** (§12.1).

### 2.1 `language-delete K = "code"` (in §2.1.3)

| Condition | Error | Kind |
|---|---|---|
| the language is not in the list of kind K | `NO_SUCH_OBJECT_LANGUAGE` / `NO_SUCH_META_LANGUAGE` (existing) | dynamic |
| it is the only language of kind K (the list cannot be empty, §2) | `CANNOT_DELETE_LAST_LANGUAGE` | dynamic |
| a text of the dictionary is written in it, including a header text (a feature label…), or it is the default language bound in scope (`language-default`, `with`, `language-create`) | `LANGUAGE_IN_USE` | dynamic |

A language bound in scope counts as used, so that a deletion never leaves the default
of §2.1 naming a language that no longer exists.

### 2.2 Feature sets and features (§2.1.4)

- **Built-in feature sets.** The dictionary always has seven feature sets, used for
  the `type` of typed and ordered components and for categories: `grammatical-info`,
  `note-type`, `translation-type`, `lexical-relation`, `reversal-type`,
  `annotation-type` and `etymology`. They can be neither deleted nor renamed
  (`DECLARATION_IS_BUILT_IN`). Their features can be created, renamed and deleted like
  any others; renaming `"Noun"` in `grammatical-info` is how a part of speech is
  renamed.
- **`feature-set-create "s"`**: `DECLARATION_ALREADY_EXISTS` if `s` exists.
- **`feature-set-delete "s"`**: deletes the set with its features.
  - `NO_SUCH_DECLARATION` if `s` does not exist.
  - `DECLARATION_IS_BUILT_IN`.
  - `DECLARATION_IN_USE` if a trait definition takes it as its `range`, or one of its
    features is in use (below). What the set holds itself - the hierarchy of its
    features, the traits of its elements - goes with it and does not count, or a
    hierarchical set such as the semantic domains could never be deleted.
- **`feature-set-rename "s" to "t"`**:
  - `NO_SUCH_DECLARATION`, `DECLARATION_IS_BUILT_IN`;
  - `DECLARATION_ALREADY_EXISTS` if `t` exists;
  - `DECLARATION_IS_EXTERNAL` if `s` is defined in an external ranges file
    (`.lift-ranges`) that was not loaded with the dictionary: that file is not
    rewritten on save, and would go on naming the set by its old id.
  - The definitions whose `range` is `s` follow the rename. A ranges file that was
    loaded is rewritten with the new id.
- **`feature-create "f" in "s"`**: `NO_SUCH_DECLARATION` (set),
  `DECLARATION_ALREADY_EXISTS` (feature).
- **`feature-delete "f" in "s"`**:
  - `NO_SUCH_DECLARATION`.
  - `DECLARATION_IN_USE` while anything uses it: the `type` of a note, a relation, an
    annotation (also one on a form or a text), an etymology, a reversal or a
    translation; a category `value`; the value of an option trait; or the
    superordinate of another feature. Uses held by the header itself count too: the
    traits and annotations of a feature set or of a feature (FieldWorks puts traits on
    range elements).
- **`feature-rename "f" in "s" to "g"`**: `NO_SUCH_DECLARATION`,
  `DECLARATION_ALREADY_EXISTS`.
  - Renaming is a **dictionary-wide relabelling**: every component whose `type` (or
    category, or trait value) is `f` now has `g`, and later selectors must use `g`
    (`note^g`).
  - It reports one effect, not one per component, by the same rule as `delete`
    (§12.4.1: "the effect list describes what the script did").
- **Implicit creation is unchanged.** `create note(type = "x")` still adds `x` to
  `note-type`. The directives add *explicit* management; they do not make it
  mandatory.

### 2.3 Field and trait definitions (§2.1.5)

- **`field-create "n" [model = M]`** and **`trait-create "n" [model = M] [range = "s"]`**
  declare a definition in the header. Fields and traits share one namespace there, so
  `DECLARATION_ALREADY_EXISTS` is raised if any definition named `n` exists.
- **Static errors:**
  - `ILLEGAL_DATA_MODEL`: an unknown model, or a model of the other kind (`field-create
    "x" model = option`).
  - `ILLEGAL_RANGE`: `range` missing with an `option…` model, or given with another.
- **Dynamic error:** `NO_SUCH_DECLARATION` when the range does not exist.
- **`field-delete "n"` / `trait-delete "n"`**:
  - `NO_SUCH_DECLARATION`, also when `n` exists but is of the other kind.
  - `DECLARATION_IN_USE` while a field or trait of the dictionary uses it, including
    one held by a feature set or a feature of the header.
- **Implicit creation is unchanged.** `create field(type = "x")` still declares `x`
  with the default model when it is not declared; `create trait(...)` likewise.
- **Not offered: renaming a definition.** Fields are stored under their definition's
  name (lift-api keys them by name), and `field.type` / `trait.type` are read-only
  (§4.5), so a definition rename would be a re-keying of every field or trait using
  it. It can be added later with a lift-api operation that re-keys them.

### 2.4 Effects (§12.4.1)

- `languageDeleted`: `languageKind`, `language`. It mirrors `languageCreated`, which
  stays unchanged so that the existing corpus cases keep passing.
- `declarationCreated`, `declarationDeleted`, `declarationRenamed`, with the fields:
  - `declarationType`: `feature-set`, `feature`, `field` or `trait`;
  - `name`;
  - `featureSet`: the set, for a feature, else `null`;
  - `newName`: for a rename, else `null`.
- `summary` gains `languagesDeleted`, `declarationsCreated`, `declarationsDeleted`
  and `declarationsRenamed`.

### 2.5 Error codes (Appendix B)

| Code | Kind | Raised by |
|---|---|---|
| `CANNOT_DELETE_LAST_LANGUAGE` | dynamic | `language-delete` |
| `LANGUAGE_IN_USE` | dynamic | `language-delete` |
| `DECLARATION_ALREADY_EXISTS` | dynamic | `*-create`, `*-rename` |
| `NO_SUCH_DECLARATION` | dynamic | every feature / definition directive |
| `DECLARATION_IN_USE` | dynamic | `feature-set-delete`, `feature-delete`, `field-delete`, `trait-delete` |
| `DECLARATION_IS_BUILT_IN` | dynamic | `feature-set-delete`, `feature-set-rename` |
| `DECLARATION_IS_EXTERNAL` | dynamic | `feature-set-rename` |
| `ILLEGAL_DATA_MODEL` | static | `field-create`, `trait-create` |
| `ILLEGAL_RANGE` | static | `trait-create` |

The five `DECLARATION_*` codes are generic on purpose. They name one condition each,
whatever is declared; the message says which item.

---

## 3. Prerequisite in lift-api: done

The lift-api changes this plan needs are in lift-api `main`, commit `cd3998a`
(2026-10-18; plan `../lift-api/.claude/plan/feature-set-rename-and-feature-usage-2026-10.md`).
They are installed in the local Maven repository; run `mvn deploy` in `../lift-api` for
builds that resolve `0.1-SNAPSHOT` from GitHub Packages.

What lift-patchbox can now call, and how each refusal maps to this plan's codes:

| lift-api | Behaviour | Used by |
|---|---|---|
| `LiftHeader.renameFeatureSet(fs, newId)` | Re-keys the header; the set keeps its place; definitions taking it as range follow; one `ValueChanged` (`FEATURE_SET_ID`), revertible. `IllegalArgumentException` for a set not in the header, a built-in set, a blank or duplicate id; `IllegalStateException` for an unloaded external ranges file | `feature-set-rename` |
| `LiftHeader.isUsed(Feature)` / `countUses(Feature)` | The uses of §2.2, entries and header alike, found by scanning the dictionary | `feature-delete` |
| `FeatureSet.removeFeature(id)` | Now refuses a feature in use (`IllegalStateException`) | `feature-delete` |
| `LiftHeader.removeFeatureSet(fs)` | Refuses a built-in set (`IllegalArgumentException`), a set that is a definition's range or has a feature in use (`IllegalStateException`); what the set holds itself does not count | `feature-set-delete` |
| `LiftHeader.isUsed(LiftFieldAndTraitDefinition)` / `countUses(...)` | Now public; counts the fields and traits held by the header too | `field-delete`, `trait-delete` |
| `FeatureSet.changeFeatureId`, `addFeature`, `removeFeature`, `addFeatureSet`, `createFieldDefinition`, `createTraitDefinition`, `setDataModel`, `setResolvedRange`, `removeFieldOrTraitDefinition`, `addFieldOrTraitDefinition`, `LiftDictionaryLanguagesManager.removeLanguage` | Unchanged; already sufficient | the other directives, and every inverse in the journal |

**Rule for lift-patchbox:** the executor checks each condition of §2 itself, through
the methods above, *before* calling the mutating one, and raises the language's code.
An exception from lift-api is then a defect of this library (an
`UnsupportedByModelException`), never the way a script error is reported. The only
condition lift-api does not expose as a query is "built in": `LiftModel` keeps the seven
ids of §2.2.

Two effects of the lift-api change matter here:

- **The XML reader fix.** lift-api's reader now applies a definition declared after the
  traits of a range element to the existing definition instead of replacing it. A
  dictionary saved by lift-patchbox from the Tuwuli file reloads again (it failed for a
  moment with the stricter `isUsed`). `TuwuliDictionaryTest$Patching.appliesAndSaves`
  covers it.
- **Feature removal is refused while in use**, in every client. Nothing in
  lift-patchbox removes features today, so no current behaviour changes.

---

## 4. Specification (`specification.md`)

Line numbers are those of the current file.

- **§1.2** (l. 90 and l. 103): the lists of what a script contains name
  "`language-default`, `language-create`, and the `with` block" and "special
  instructions `language-default` and `language-create`". Replace both with "the
  language directives and the declaration directives (sections 2.1.3 to 2.1.5)".
- **§2.1.3**, retitled "Creating and deleting languages": add `language-delete`, with
  the rules of §2.1. Extend the closing paragraph ("`language-create` is the one
  directive that changes the dictionary") to every directive of §2.1.3–2.1.5.
- **New §2.1.4 "Managing dictionary feature and feature sets"**, after §2.1.3, as
  requested:
  - the vocabulary note (range, range element);
  - the list of built-in feature sets;
  - the six directives with their rules (§2.2 above) and examples, including the
    external ranges file and what a deleted set takes with it;
  - the relabelling semantics of `feature-rename`;
  - implicit creation by commands.
- **New §2.1.5 "Declaring fields and traits"**:
  - the four directives;
  - the data-model table (model, kind, value held; `range` for `option…`);
  - the defaults;
  - why definitions cannot be renamed (§4.5);
  - implicit creation.
- **§11 Blocks** (l. 2872–2876): the list of what a block body holds names
  "`language-default` directives". Add the declaration directives, which are
  operations and not scoped, like `language-create`.
- **§12.3** (l. 3017): add the seven new dynamic codes to the list of dynamic errors.
- **§12.4.1**:
  - **Operation numbering** (l. 3121): "`language-create` is an operation" becomes
    "every directive except `language-default` is an operation".
  - **The `summary` example** (l. 3105): add the four counters.
  - **The effects table** (l. 3136): add the four kinds. "exactly these seven `kind`
    values" becomes eleven.
- **Part 3 §1.1, recognition rule 2** (l. 3226): a directive line starts with one of
  the directive keywords. List them, with one example line per family.
- **Part 3 §3.3 indented blocks** (around l. 3750): the declaration directives are
  allowed on an indented line, like the language directives.
- **Appendix B**:
  - B.1: `ILLEGAL_DATA_MODEL`, `ILLEGAL_RANGE`.
  - B.2: `CANNOT_DELETE_LAST_LANGUAGE`, `LANGUAGE_IN_USE` and the five
    `DECLARATION_*` codes, each with its sections.
- **Appendix C.1**, replacing l. 5159:
  ```ebnf
  directive         ::= language-directive | declaration
  language-directive::= ( 'language-default' | 'language-create' | 'language-delete' )
                        lang-kind '=' string
  declaration       ::= 'feature-set-create' string
                      | 'feature-set-delete' string
                      | 'feature-set-rename' string 'to' string
                      | 'feature-create' string 'in' string
                      | 'feature-delete' string 'in' string
                      | 'feature-rename' string 'in' string 'to' string
                      | 'field-create' string [ 'model' '=' name ]
                      | 'trait-create' string [ 'model' '=' name ] [ 'range' '=' string ]
                      | ( 'field-delete' | 'trait-delete' ) string
  ```
  Add a note: `in`, `to`, `model` and `range` are keywords only in a directive. They
  remain legal as names, language codes (`to` is Tongan) and type keys.
- **Appendix C.2**: `directive` refers to the C.1 production, since a directive line
  is identical in both syntaxes.
- **Appendix D.1**: add `declarationType`, `name`, `featureSet` and `newName` to the
  exhaustive list of compared fields.
- **Appendix D.3**: new cases, in the spec and in `corpus.json` alike (D.4 rule 2):

| id | script (outline) | expected |
|---|---|---|
| C-094 | `language-create object = "hui"` then `language-delete object = "hui"` | ok: `languageCreated`, `languageDeleted` |
| C-095 | `language-delete object = "tww"` (the fixture has tww forms) | `LANGUAGE_IN_USE` |
| C-096 | `language-delete object = "xyz"` | `NO_SUCH_OBJECT_LANGUAGE` |
| C-097 | `feature-set-create "variant-type"`, `feature-create "Free" in "variant-type"`, `feature-rename "Free" in "variant-type" to "Free Variant"` | ok: three `declaration*` effects |
| C-098 | `feature-set-delete "grammatical-info"` | `DECLARATION_IS_BUILT_IN` |
| C-099 | `feature-delete "Noun" in "grammatical-info"` (fixture senses are nouns) | `DECLARATION_IN_USE` |
| C-100 | `feature-rename "Noun" in "grammatical-info" to "N"`, then `feature-create "N" in "grammatical-info"` | `DECLARATION_ALREADY_EXISTS`, command 2: the rename took effect (that the fixture's categories now read `N` is checked by a unit test, §5.5) |
| C-101 | `trait-create "variant-type" model = option` | `ILLEGAL_RANGE`, static |
| C-102 | `field-create "x" model = option` | `ILLEGAL_DATA_MODEL`, static |
| C-103 | `feature-set-create "variant-type"`, `trait-create "variant-type" model = option range = "variant-type"`, `feature-set-delete "variant-type"` | `DECLARATION_IN_USE`, command 3 |
| C-104 | a declaration followed by a failing command | the declaration is rolled back |


---

## 5. Code

### 5.1 ANTLR grammars

**`LiftPatchRef.g4`:**
- **Tokens:**
  - `LANGUAGE_DELETE`;
  - `FEATURE_SET_CREATE`, `FEATURE_SET_DELETE`, `FEATURE_SET_RENAME`;
  - `FEATURE_CREATE`, `FEATURE_DELETE`, `FEATURE_RENAME`;
  - `FIELD_CREATE`, `FIELD_DELETE`, `TRAIT_CREATE`, `TRAIT_DELETE`;
  - `IN`, `TO`, `MODEL`, `RANGE`.

  Declare the hyphenated keywords before `NAME`, as `LANGUAGE_CREATE` is. At equal
  length ANTLR takes the first rule; `feature-set-create` is longer than
  `feature-set`, so there is no clash.
- **Rules:** `directive : languageDirective | declaration ;` with
  `languageDirective` (the current rule plus `LANGUAGE_DELETE`) and `declaration` as
  in Appendix C.1. `dataModel : anyName ;` checks the value statically, not in the
  grammar.
- **`anyName`:** add `IN | TO | MODEL | RANGE` (and the new hyphenated keywords, for
  symmetry with `CREATE`…), so that `form@to`, `$range` and `note^model` still
  parse.

**`LiftPatchShort.g4`:**
- The same tokens.
- `directiveOnly : (languageDirective | declaration) EOF ;`.
- `IN`, `TO`, `MODEL` and `RANGE` added to the rules that accept a bare word (`lang`,
  `bareWord`, type keys).
- `TO` already exists if the concise `m … to` clause has it; reuse it.

### 5.2 AST (`ast/`)

- **`Directive`** becomes a sealed interface (`permits` the records below), on the
  pattern of `Command`:
  - `LanguageDefault(kind, language, position)`;
  - `LanguageCreate(kind, language, position)`;
  - `LanguageDelete(kind, language, position)`;
  - `FeatureSetCreate(name)`, `FeatureSetDelete(name)`, `FeatureSetRename(name, newName)`;
  - `FeatureCreate(name, featureSet)`, `FeatureDelete(name, featureSet)`,
    `FeatureRename(name, featureSet, newName)`;
  - `DefinitionCreate(DefinitionKind kind, name, Optional<String> model, Optional<String> range)`;
  - `DefinitionDelete(DefinitionKind kind, name)`;
  - `DefinitionKind { FIELD, TRAIT }`.
- Each record has a `position`, a `toString()` that renders the directive as written
  (the plan's operation text) and a `keyword()` (the plan's `verb`). `isOperation()`
  is `true` for all but `LanguageDefault`.
- **`LiftPatch.java`:** keep `languageDefault` / `languageCreate`; add a builder per
  record.
- **`Script.java`:** no change (`case Directive` stays exhaustive).

### 5.3 Parsing and validation

- **`RefScriptVisitor.visitDirective`** and **`ShortScriptParser.parseDirective`**
  build the records. They share a helper, since the two grammars' contexts differ
  only in class name. Move it to a small `DirectiveBuilder` in `parser/`.
- **`ShortLinePreprocessor.isDirective`**: recognise the new keywords, each followed
  by a space. For the declaration keywords, the next character is a quote; for
  `language-*`, `object` or `meta`.
- **`metamodel/DataModel.java`** (new): an enum of the eight LIFT data models
  (`string`, `integer`, `datetime`, `option`, `option-collection`, `option-sequence`
  for traits; `multitext`, `multistring` for fields), with `forTraits()` /
  `forFields()` and `needsRange()`. It mirrors `LiftFieldAndTraitDefinitionDataModel`
  without depending on lift-api in the validator.
- **`SemanticValidator`**:
  - count every directive whose `isOperation()` is true (already generic);
  - on `DefinitionCreate`, raise `ILLEGAL_DATA_MODEL` and `ILLEGAL_RANGE` statically.

### 5.4 Execution

- **`engine/Executor.executeDirective`**: a `switch` over the records. Each operation
  takes the next index, checks its dynamic conditions (§2), calls `LiftModel`,
  records one effect and adds an `Operation`, as `language-create` does now.
  - `LanguageDelete` asks `LanguageScope` whether the code is bound in scope. Add
    `isBound(kind, code)` there.
- **`engine/Effect`**:
  - `Kind` gains `languageDeleted`, `declarationCreated`, `declarationDeleted` and
    `declarationRenamed`;
  - the record gains `declarationType`, `name`, `featureSet` and `newName`;
  - add one factory per kind;
  - update every existing factory for the four new `null` fields.
- **`engine/Summary`**: the four counters and their getters.
- **`engine/Plan`**: write the new effect fields and counters in the JSON.
- **`error/ErrorCode`**: the nine codes, with their kinds.
- **`model/LiftModel`**: new methods, each recording its inverse in the journal:
  - **Languages:**
    - `deleteLanguage(kind, code)`, inverse `addLanguage`;
    - `languageInUse(kind, code)` from the manager's occurrence count.
  - **Feature sets:**
    - `createFeatureSet(id)`, inverse `removeFeatureSet`;
    - `deleteFeatureSet(id)`, inverse `addFeatureSet(fs)` with the same object, so it
      comes back with its features;
    - `renameFeatureSet(id, newId)` with `LiftHeader.renameFeatureSet`, inverse the
      rename back;
    - `isBuiltInFeatureSet(id)`: the seven ids of §2.2 (lift-api has no public query
      for it);
    - `isExternalUnloaded(id)`: `getHref().isPresent() && !isExternalContentLoaded()`;
    - "feature set in use" (a definition takes it as range, or one of its features is
      in use outside the set). `isUsed(Feature)` per feature does not leave out what
      the set holds itself, so it cannot be pre-checked with it.
      - **Recommended:** call `removeFeatureSet` and map its `IllegalStateException`
        to `DECLARATION_IN_USE`. This is the one refusal mapped instead of
        pre-checked; the removal is all-or-nothing, so a refused one leaves the
        dictionary unchanged, and no further lift-api change is needed.
      - Alternative: add `LiftHeader.isUsedOutside(FeatureSet)` to lift-api, a public
        form of the check `removeFeatureSet` already makes.
  - **Features:**
    - `createFeature(set, id)`, inverse `removeFeature`;
    - `deleteFeature(set, id)`, inverse `addFeature(feature)` with the same object;
    - `renameFeature(set, id, newId)` with `changeFeatureId`, inverse the rename
      back. `Feature` uses identity equality, so notes and translations keyed by it
      stay valid.
    - `featureInUse(Feature)`: `LiftHeader.isUsed(Feature)`.
  - **Definitions:**
    - `createDefinition(kind, name, model, range)` with `createFieldDefinition` /
      `createTraitDefinition`, `setDataModel` and `setResolvedRange`; inverse
      `removeFieldOrTraitDefinition`;
    - `deleteDefinition(kind, name)`, inverse `addFieldOrTraitDefinition(fd)`;
    - `definitionInUse(fd)`: `LiftHeader.isUsed(fd)`.
  - **Homophones:** none of these touches entry forms.

### 5.5 Tests

- **`ParserSmokeTest`:**
  - each new directive in both syntaxes;
  - `form@to`, `$range` and `note^model` still parse.
- **`ConstructsTest`**, new nested `Declarations`, one test per rule of §2:
  - success, each error code;
  - rollback by a later failure;
  - a directive inside a block outlives it;
  - a rename followed by a type-key selector using the new name;
  - `feature-rename "Noun" in "grammatical-info" to "N"`: the fixture's categories
    now read `N`;
  - a feature deleted then restored by rollback is the same object (a note still
    has it);
  - `feature-set-rename` of a set given an `href` whose content was not loaded is
    refused with `DECLARATION_IS_EXTERNAL` (set up through lift-api in the test: the
    standard fixture has no external ranges);
  - `feature-set-delete` of a set whose only uses are its own hierarchy succeeds;
  - `feature-delete` of a feature used only by a trait on a range element is refused.
- **`ConciseSyntaxTest`**: the recognition rule accepts the new directive lines and
  leaves a prose line beginning with "feature" alone.
- **`TuwuliDictionaryTest`**: `language-delete` of a used language is refused; a
  `feature-rename` of a real part of speech in plan mode reports one effect.
- **`corpus.json`**: §4, Appendix D.3.

---

## 6. Order of work

1. ~~lift-api: `renameFeatureSet` and the usage queries.~~ Done (§3); `mvn deploy` in
   `../lift-api` before a remote build.
2. Specification (§4), with the corpus cases.
3. Grammars and AST (§5.1, §5.2); parser and validator (§5.3);
   `ParserSmokeTest`.
4. `ErrorCode`, `Effect`, `Summary`, `Plan`, `LiftModel`, `Executor` (§5.4).
5. Tests (§5.5); `mvn clean test` against lift-api HEAD and the installed snapshot.

## 7. Not changed

- `language-default`, `with` and `language-create`, with their effects and codes.
- Implicit creation of features and definitions by commands.
- Renaming languages, definitions or field/trait kinds: not requested, and the
  last two would re-key components (§2.3).

## 8. Results (2026-10-18)

Implemented, uncommitted. 474 tests pass (440 before), against the lift-api snapshot
installed from `cd3998a`. Appendix D.3 and `corpus.json` are identical (105 cases).

What was built, by section of this plan:

- **§4, specification:**
  - §1.2 updated; §2.1.3 is now "Creating and deleting languages", with one paragraph of
    rules shared by every directive that changes the dictionary;
  - new §2.1.4 and §2.1.5;
  - updates to §11, §12.3, §12.4.1, Part 3 recognition rule 2 and indented blocks,
    Appendices B.1/B.2, C.1 (with notes), C.2 and D.1;
  - corpus cases C-094 to C-105.
- **§5.1, grammars:** both have `languageDirective` and `declaration`. The new keywords
  are in `anyName` / `bareWord`.
- **§5.2, AST:** `Directive` is a sealed interface of eleven records, with
  `DefinitionKind`; `LiftPatch` has a factory per directive.
- **§5.3, parsing and validation:**
  - `parser/DirectiveBuilder` is shared by both visitors;
  - `ShortLinePreprocessor` recognises a declaration keyword followed by a quote;
  - new `metamodel/DataModel`;
  - the validator checks the data model, the range and blank names.
- **§5.4, engine:**
  - `Executor.executeDirective` handles one directive per method;
  - `Effect` has eleven kinds and four new fields, `Summary` four counters, `Plan` the
    JSON for both;
  - `LanguageScope.isBound`;
  - the journaled `LiftModel` methods;
  - nine new `ErrorCode`s.
- **§5.5, tests:**
  - `ParserSmokeTest` (+2);
  - `ConstructsTest$Declarations` (13);
  - `ConciseSyntaxTest$Recognition` (+2);
  - `TuwuliDictionaryTest$Planning` (+2);
  - the effect renderers of `PatchTestSupport` and `ConformanceCorpusTest`.

Deviations and decisions made while implementing:

- **Blank names** in a declaration directive are a static `SYNTAX_ERROR` (spec §2.1.4
  and Appendix B.1). The plan did not cover them, and lift-api would have accepted some
  of them.
- **`language-create` binds the new language as the default**, so deleting it right
  away is refused (`LANGUAGE_IN_USE`, case C-105). C-094 therefore rebinds the default
  before the deletion. C-095 deletes `tpi`, which the fixture uses, rather than `tww`.
- **Defaults are written out.** `field-create` / `trait-create` without `model` record
  the default (`multitext` / `string`) as the declared model, so a saved file says it.
- **Definitions of unknown kind.** A definition whose kind the file left undeclared can
  be deleted by either `field-delete` or `trait-delete`; only one of the *other* declared
  kind is `NO_SUCH_DECLARATION`.
- **Grammar.** `model` and `range` are admitted on both `field-create` and
  `trait-create`, and a range on a field is `ILLEGAL_RANGE`. Appendix C.1 is written that
  way, with a note.
- **Rollback of `feature-set-delete`** puts the set back at the end of the header's list
  of sets (lift-api has no positional add). Only the order in a saved file can differ;
  no rule depends on it.

Not done:

- `mvn deploy` of lift-api, needed for a build that resolves `0.1-SNAPSHOT` from GitHub
  Packages.
