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

Plan plan = box.plan(dictionary, text, Syntax.CONCISE, "edits.liftpatchshort");
if (plan.isOk()) {
    box.apply(dictionary, text, Syntax.CONCISE, "edits.liftpatchshort");
}
```

`LiftPatchBox` parses, then hands the command model to the core's
`LiftPatchEngine` (`box.engine()`), and reports the warnings the text calls for —
`NO_COMMAND_RECOGNIZED` — ahead of those of execution. The error codes only a
parser raises are the constants of `SyntaxErrorCode`.

Parsing does not stop at the first syntax error, in either syntax: it resumes at
the next command (LiftPatchRef) or the next command line (LiftPatchShort), and
`parse` throws one `LiftPatchException` carrying every syntax error, sorted by
position. To get every syntax error and every static error of a script in one
call, without an exception and without a dictionary, use `check`:

```java
ScriptCheck check = box.checkFile(Path.of("edits.liftpatchshort"), null);
for (LiftPatchError error : check.errors()) {   // syntax and static, sorted by position
    System.err.println(error.format());
}
```

Static validation then runs on the commands that parsed. When some did not, an
`UNKNOWN_LABEL` is not reported for a label the script binds somewhere, and the
operation index of the static errors is `0`, since the commands left out shift
the numbering; their source position is exact.

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
| `fr.cnrs.lacito.liftpatchbox.language` | `LiftPatchBox`, `SourceScript`, `ScriptCheck`, `Syntax`, `Pragma`. |
| `…language.parser` | The two grammars, the concise-syntax preprocessor, the short codes, the visitors. |
| `…language.error` | The error and warning codes only a parser raises. |
| `…language.cli` | The command-line tool. |
