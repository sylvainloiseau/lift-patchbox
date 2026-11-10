# lift-patchbox-language

The two surface syntaxes of the LiftPatch mutation language, over the command
model of [`lift-patchbox-core`](../lift-patchbox-core/README.md):

- **LiftPatchRef**, the verbose and unambiguous reference syntax:
  `create entry(form = "mami") { create sense(gloss = "pig") }`;
- **LiftPatchShort**, the concise, line-oriented syntax for fast note-taking:
  `c e("mami", s("pig"))`, `p /mami/pig`.

It holds their ANTLR grammars and parsers, the short-code tables of the concise
syntax, the pragma rules, and the command-line tool. A parsed script is a
`SourceScript`: the core's command model, and what the text said about itself
(its pragma, its syntax, the number of lines recognized as commands).

```xml
<dependency>
  <groupId>io.github.sylvainloiseau</groupId>
  <artifactId>lift-patchbox-language</artifactId>
  <version>…</version>
</dependency>
```

## Usage

```java
LiftPatchBox box = new LiftPatchBox()
    .withDefaultObjectLanguage("tww")
    .withDefaultMetaLanguage("en");

Plan plan = box.plan(dictionary, text, Syntax.CONCISE, "edits.liftpatchs");
if (plan.isOk()) {
    box.apply(dictionary, text, Syntax.CONCISE, "edits.liftpatchs");
}
```

`LiftPatchBox` parses, then hands the command model to the core's
`LiftPatchEngine` (`box.engine()`), and reports the warnings the text calls for —
`NO_COMMAND_RECOGNIZED` — ahead of those of execution. The error codes only a
parser raises are the constants of `SyntaxErrorCode`.

The command-line tool is described in the [repository README](../README.md).

## Specification

`specification.md`, at the root of the repository, is normative. This module
implements the version pragma (Part 1, section 1.1), the reference syntax (Part
2, section 7), the concise syntax (Part 3), and the grammars of Appendix C. The
conformance corpus of Appendix D is
`src/test/resources/fr/cnrs/lacito/liftpatchbox/language/corpus.json`, and every
fenced example of the specification is a test case.

## Packages

| Package | What it holds |
|---|---|
| `fr.cnrs.lacito.liftpatchbox.language` | `LiftPatchBox`, `SourceScript`, `Syntax`, `Pragma`. |
| `…language.parser` | The two grammars, the concise-syntax preprocessor, the short codes, the visitors. |
| `…language.error` | The error and warning codes only a parser raises. |
| `…language.cli` | The command-line tool. |
