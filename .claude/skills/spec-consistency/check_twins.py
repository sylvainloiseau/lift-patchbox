#!/usr/bin/env python3
"""Check the parts of specification.md that have a twin in the code base.

Run from the repository root:  python3 .claude/skills/spec-consistency/check_twins.py

1. Appendix A (the metamodel JSON)  == src/main/resources/.../metamodel-1.0.json
2. Appendix D.3 (the corpus)         == src/test/resources/.../corpus.json, case ids in sequence
3. Appendix B (error codes)          == error/ErrorCode.java, kinds included
4. Section 4.1 property table        agrees with Appendix A (qualifier, required, identity, writable)
5. Part 3 unnamed-argument table     agrees with Appendix A's unnamedArguments

Exit status 0 when everything agrees, 1 otherwise. Each disagreement is printed.
"""
import json
import re
import sys

SPEC = "specification.md"
METAMODEL = "src/main/resources/fr/cnrs/lacito/liftpatchbox/metamodel-1.0.json"
CORPUS = "src/test/resources/fr/cnrs/lacito/liftpatchbox/corpus.json"
ERROR_CODES = "src/main/java/fr/cnrs/lacito/liftpatchbox/error/ErrorCode.java"

problems = []


def section(text, start, end):
    i = text.index(start)
    return text[i:text.index(end, i)]


spec = open(SPEC, encoding="utf-8").read()

# 1. Appendix A == metamodel resource -----------------------------------------
appendix_a = section(spec, "## Appendix A.", "## Appendix B.")
blocks = re.findall(r"```json\n(.*?)```", appendix_a, re.S)
metamodel = json.load(open(METAMODEL, encoding="utf-8"))
if len(blocks) != 1:
    problems.append(f"Appendix A: expected one json block, found {len(blocks)}")
else:
    appendix = json.loads(blocks[0])
    if appendix != metamodel:
        for key in sorted(set(appendix) | set(metamodel)):
            if appendix.get(key) != metamodel.get(key):
                if key == "components":
                    for c in sorted(set(appendix[key]) | set(metamodel[key])):
                        if appendix[key].get(c) != metamodel[key].get(c):
                            problems.append(f"Appendix A vs {METAMODEL}: component `{c}` differs")
                else:
                    problems.append(f"Appendix A vs {METAMODEL}: `{key}` differs")

# 2. Appendix D.3 == corpus.json ----------------------------------------------
corpus_text = open(CORPUS, encoding="utf-8").read()
start = spec.index('  { "id": "C-001"')
end = spec.index("\n]\n```", start)
in_spec = spec[start:end]
in_file = corpus_text[corpus_text.index('  { "id": "C-001"'):corpus_text.rindex("\n]")]
if in_spec != in_file:
    problems.append(f"Appendix D.3 and {CORPUS} are not identical")
cases = json.loads(corpus_text)
ids = [c["id"] for c in cases]
expected = [f"C-{n:03d}" for n in range(1, len(ids) + 1)]
if ids != expected:
    problems.append(f"corpus ids are not C-001..C-{len(ids):03d} in order")

# 3. Appendix B == ErrorCode.java ---------------------------------------------
appendix_b = section(spec, "## Appendix B.", "## Appendix C.")
b1 = section(appendix_b, "### B.1", "### B.2")
b2 = section(appendix_b, "### B.2", "### B.3")
spec_codes = {c: "STATIC" for c in re.findall(r"^\| `([A-Z_]+)` \|", b1, re.M)}
spec_codes.update({c: "DYNAMIC" for c in re.findall(r"^\| `([A-Z_]+)` \|", b2, re.M)})
java = open(ERROR_CODES, encoding="utf-8").read()
java_codes = dict(re.findall(r"^\s+([A-Z_]+)\(ErrorKind\.(STATIC|DYNAMIC)\)", java, re.M))
for code in sorted(set(spec_codes) | set(java_codes)):
    if code not in java_codes:
        problems.append(f"Appendix B code `{code}` is missing from ErrorCode.java")
    elif code not in spec_codes:
        problems.append(f"ErrorCode.{code} is missing from Appendix B (B.1 or B.2)")
    elif spec_codes[code] != java_codes[code]:
        problems.append(f"`{code}` is {spec_codes[code]} in Appendix B, {java_codes[code]} in ErrorCode.java")
# A code no corpus case raises is allowed (D.4 rule 3 is a SHOULD), but reported.
raised = {c["expect"].get("code") for c in cases}
unraised = sorted(c for c in spec_codes if c not in raised)

# 4. Section 4.1 property table vs Appendix A ---------------------------------
table = section(spec, "| Component | Property | Datatype |", "\n\n")
qualifier = {"O": "object", "M": "meta", "—": None}
for line in table.splitlines()[2:]:
    cells = [c.strip() for c in line.strip("|").split("|")]
    if len(cells) != 7:
        continue
    comp, prop = cells[0].lower(), cells[1].strip("`")
    defn = metamodel["components"].get(comp, {}).get("properties", {}).get(prop)
    if defn is None:
        problems.append(f"4.1 table: `{comp}.{prop}` is not in Appendix A")
        continue
    if qualifier.get(cells[3], "?") != defn.get("qualifier"):
        problems.append(f"4.1 table: qualifier of `{comp}.{prop}` disagrees with Appendix A")
    required = cells[4].startswith("yes")
    if required != defn.get("required", False):
        problems.append(f"4.1 table: `Required` of `{comp}.{prop}` disagrees with Appendix A")
    identity = not cells[5].startswith("no")
    if identity != (prop in metamodel["components"][comp].get("naturalIdentity", [])):
        problems.append(f"4.1 table: `Natural identity` of `{comp}.{prop}` disagrees with Appendix A")
    writable = cells[6].startswith("yes")
    if writable != defn.get("writable", True):
        problems.append(f"4.1 table: `Writable` of `{comp}.{prop}` disagrees with Appendix A")
listed = {(c[0].lower(), c[1].strip("`")) for c in
          ([x.strip() for x in l.strip("|").split("|")] for l in table.splitlines()[2:]) if len(c) == 7}
for comp, d in metamodel["components"].items():
    for prop in d.get("properties", {}):
        if (comp, prop) not in listed:
            problems.append(f"4.1 table: `{comp}.{prop}` of Appendix A has no row")

# 5. Part 3 unnamed-argument table vs Appendix A ------------------------------
unnamed = section(spec, "| Component | Number of unnamed argument | Property mapping |", "\n\n")
for line in unnamed.splitlines()[2:]:
    cells = [c.strip() for c in line.strip("|").split("|")]
    if len(cells) != 3:
        continue
    comp = cells[0].lower()
    if comp not in metamodel["components"]:
        problems.append(f"unnamed-argument table: `{comp}` is not in Appendix A")
        continue
    declared = metamodel["components"][comp].get("unnamedArguments", [])
    written = [] if cells[1].startswith("—") else [
        p.strip() for p in re.sub(r"\(.*?\)", "", cells[2]).split(",")]
    if written != declared:
        problems.append(f"unnamed-argument table: `{comp}` maps {written}, Appendix A declares {declared}")

# -----------------------------------------------------------------------------
for p in problems:
    print("MISMATCH:", p)
print(f"{len(cases)} corpus cases, {len(spec_codes)} error codes, "
      f"{len(metamodel['components'])} component types checked.")
if unraised:
    print("Codes no corpus case raises (D.4 rule 3, a SHOULD):", ", ".join(unraised))
sys.exit(1 if problems else 0)
