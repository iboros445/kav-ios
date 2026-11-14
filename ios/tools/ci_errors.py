#!/usr/bin/env python3
"""Summarise a GitHub Actions job log saved by the GitHub MCP `get_job_logs` tool (or a plain text log).

  python3 ios/tools/ci_errors.py <saved-log-file> [--all]

Prints the Kotlin compiler errors (`e: file:///...`) with the CI path prefix removed and duplicates collapsed, the
failing Gradle task, and test failures. --all also prints warnings. Pass `return_content=true, tail_lines=3000` to
get_job_logs; when the result is too large the tool saves it to a file and tells you the path.
"""
import json, re, sys

path = sys.argv[1]
raw = open(path, encoding="utf-8", errors="replace").read()
try:
    d = json.loads(raw)
    raw = d.get("logs_content") or d.get("content") or raw
except Exception:
    pass
text = raw.replace("\\n", "\n")
lines = [re.sub(r"^\S+Z ", "", l) for l in text.split("\n")]

seen, errors, warns = set(), [], []
for l in lines:
    m = re.match(r"\s*([ew]): file://(?:/Users/runner/work/[^/]+/[^/]+/)?(.*)", l)
    if not m: continue
    kind, rest = m.groups()
    rest = rest.replace("ios/kav/build/upstream/kotlin/", "upstream:").replace("ios/kav/src/iosMain/kotlin/", "")
    if rest in seen: continue
    seen.add(rest)
    (errors if kind == "e" else warns).append(rest[:260])

print(f"== {len(errors)} compiler errors")
for e in errors: print("  " + e)
if "--all" in sys.argv:
    print(f"== {len(warns)} warnings")
    for w in warns[:80]: print("  " + w)
for l in lines:
    if re.search(r"Execution failed for task|What went wrong|BUILD (FAILED|SUCCESSFUL)|FAILED$|AssertionError|ld: |Undefined symbols|error: |Test .* FAILED|org.jetbrains.kotlin.gradle.tasks.*Exception", l):
        print("  >> " + l.strip()[:240])
