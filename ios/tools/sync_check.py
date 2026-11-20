#!/usr/bin/env python3
"""Predict iOS compile breakage from the Linux side (Kotlin/Native cannot build iOS here).

Reports, for the upstream Android sources that the iOS build compiles:
  1. UNCOVERED  platform APIs (android.*, java.*, javax.*, org.json, androidx core/activity/camera,
                MapLibre, ...) that no shim under ios/kav/src/iosMain/kotlin/compat declares, plus
                JVM-only stdlib names that jvmstd/JvmStd.kt.template lacks.
  2. DRIFT      per override: upstream non-private top-level declarations the override is missing or
                declares with a different header.
  3. STALE      overrides whose upstream file changed after the base sha recorded in overrides.txt.

Reads the working tree. --from/--to (git revisions) limit reports 1 and 3 to the files that changed
in that range, which is what UPSTREAM_SYNC.md §5 wants. Exit status 1 if report 1 or 2 is non-empty.

  python3 ios/tools/sync_check.py                      # everything
  python3 ios/tools/sync_check.py --from OLD --to NEW  # only what the upstream delta touches
"""
import argparse, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
UPSTREAM = os.path.join(ROOT, "android/app/src/main/java")
IOS_MAIN = os.path.join(ROOT, "ios/kav/src/iosMain")
COMPAT = os.path.join(IOS_MAIN, "kotlin/compat")
OVERRIDES_DIR = os.path.join(IOS_MAIN, "kotlin/overrides")
TEMPLATE = os.path.join(IOS_MAIN, "jvmstd/JvmStd.kt.template")
OVERRIDES_TXT = os.path.join(ROOT, "ios/overrides.txt")

# Imports in these namespaces are platform APIs that need a shim unless a dependency provides them.
PLATFORM = re.compile(r"^(android\.|java\.|javax\.|org\.json\.|org\.maplibre\.|com\.google\.|"
                      r"androidx\.(core|activity|camera|annotation|test|appcompat)\b)")
# Provided by Kotlin/Compose Multiplatform dependencies, never needs a shim.
PROVIDED = re.compile(r"^(kotlin\.|kotlinx\.|androidx\.compose\.|androidx\.lifecycle\.|uk\.noammm\.kav\.)")
# Fully-qualified uses in code (not imports): android.content.Context, java.util.Locale.US, ...
FQN = re.compile(r"(?<![\w.])((?:android|java|javax|org\.json|org\.maplibre)(?:\.[a-z]\w*)+(?:\.[A-Z]\w*)+)")
# JVM-only stdlib names that are not imports: name -> regex that detects its use in upstream code.
JVM_NAMES = {
    "System": r"\bSystem\.\w+", "Math": r"\bMath\.\w+", "Charsets": r"\bCharsets\.\w+",
    "synchronized": r"\bsynchronized\s*\(", "Volatile": r"@Volatile\b", "Thread": r"\bThread\b[.(]",
    "Runtime": r"\bRuntime\.getRuntime", "String.format": r"\bString\.format\(|\"\.format\(",
    "StringBuffer": r"\bStringBuffer\b", "ThreadLocal": r"\bThreadLocal\b",
}
DECL = re.compile(r"^(?:(?:public|internal|data|enum|sealed|open|abstract|const|inline|suspend|annotation|"
                  r"value|fun|infix|operator|tailrec|external|expect|actual|lateinit)\s+)*"
                  r"(class|interface|object|typealias|fun|val|var)\s+(?:<[^>]*>\s*)?([A-Za-z_][\w.]*)")


def git(*a):
    return subprocess.run(["git", "-C", ROOT, *a], capture_output=True, text=True)


def kt_files(base):
    for d, _, fs in os.walk(base):
        for f in fs:
            if f.endswith(".kt") or f.endswith(".template"):
                yield os.path.join(d, f)


def declared_fqns():
    """Every top-level name the compat layer (and the template's std shims) declares, as FQNs."""
    out, simple = set(), set()
    for path in list(kt_files(COMPAT)) + [TEMPLATE]:
        text = open(path, encoding="utf-8").read()
        pkg = (re.search(r"^package\s+(\S+)", text, re.M) or [None, ""])[1]
        for line in text.splitlines():
            if line[:1] in " \t":
                continue
            m = DECL.match(line)
            if m and not line.startswith("private"):
                name = m.group(2).split(".")[-1]
                out.add(f"{pkg}.{name}" if pkg and pkg != "__PACKAGE__" else name)
                simple.add(name)
            m = re.match(r"^typealias\s+(\w+)", line)
            if m:
                simple.add(m.group(1))
    return out, simple


def covered(name, declared, packages):
    if name.endswith(".*"):
        return name[:-2] in packages
    parts = name.split(".")
    return any(".".join(parts[:i]) in declared for i in range(len(parts), 1, -1))


def read_overrides():
    rows = []
    if os.path.exists(OVERRIDES_TXT):
        for line in open(OVERRIDES_TXT, encoding="utf-8"):
            line = line.split("#", 1)[0].strip()
            if line:
                p = line.split()
                rows.append((p[0], p[1] if len(p) > 1 else None))
    return rows


def decls(path):
    """name -> normalised header of each non-private top-level declaration."""
    out = {}
    for line in open(path, encoding="utf-8").read().splitlines():
        if not line or line[0] in " \t/@}*" or line.startswith("private"):
            continue
        m = DECL.match(line)
        if m:
            header = re.split(r"\s*[{=]\s*|\s*$", line, maxsplit=1)[0]
            out[m.group(2)] = re.sub(r"\s+", " ", header).strip()
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--from", dest="old", help="old upstream revision")
    ap.add_argument("--to", dest="new", help="new upstream revision (default HEAD when --from is given)")
    ap.add_argument("--quiet", action="store_true", help="only print counts per report")
    args = ap.parse_args()

    overrides = read_overrides()
    overridden = {p for p, _ in overrides}
    rel = lambda p: os.path.relpath(p, UPSTREAM)

    only = None
    if args.old:
        r = git("diff", "--name-only", f"{args.old}..{args.new or 'HEAD'}", "--", "android/app/src/main/java")
        if r.returncode != 0:
            sys.exit(r.stderr.strip())
        only = {l[len("android/app/src/main/java/"):] for l in r.stdout.split() if l.endswith(".kt")}

    declared, simple = declared_fqns()
    packages = {n.rsplit(".", 1)[0] for n in declared if "." in n}

    # ---- 1. uncovered ----
    uncovered = {}
    for path in sorted(kt_files(UPSTREAM)):
        r = rel(path)
        if r in overridden or (only is not None and r not in only):
            continue
        text = open(path, encoding="utf-8").read()
        for ln, line in enumerate(text.splitlines(), 1):
            m = re.match(r"import\s+([\w.]+(?:\.\*)?)(?:\s+as\s+\w+)?\s*$", line)
            names = [m.group(1)] if m else []
            if not m and not line.lstrip().startswith(("//", "*", "/*")):
                names = FQN.findall(line)
            for n in names:
                if PROVIDED.match(n) or not PLATFORM.match(n):
                    continue
                if not covered(n, declared, packages):
                    uncovered.setdefault(n, []).append(f"{r}:{ln}")
        stripped = re.sub(r"^import .*$", "", text, flags=re.M)
        for name, rx in JVM_NAMES.items():
            if re.search(rx, stripped) and name.split(".")[0] not in simple and name not in simple \
                    and name not in open(TEMPLATE, encoding="utf-8").read():
                uncovered.setdefault(f"(jvm-std) {name}", []).append(r)

    # ---- 2. drift + 3. stale ----
    drift, stale = [], []
    for path, base in overrides:
        up = os.path.join(UPSTREAM, path)
        ov = os.path.join(OVERRIDES_DIR, path[:-3] + ".ios.kt")
        if not os.path.exists(up):
            drift.append(f"{path}: upstream file no longer exists, delete the override and its overrides.txt line")
            continue
        if not os.path.exists(ov):
            drift.append(f"{path}: override file missing ({os.path.relpath(ov, ROOT)})")
            continue
        want, have = decls(up), decls(ov)
        for name, header in want.items():
            if name not in have:
                drift.append(f"{path}: override lacks `{header}`")
            elif have[name] != header:
                drift.append(f"{path}: `{name}` differs\n      upstream: {header}\n      override: {have[name]}")
        if base and git("cat-file", "-e", f"{base}^{{commit}}").returncode == 0:
            d = git("diff", "--quiet", f"{base}..{args.new or 'HEAD'}", "--", f"android/app/src/main/java/{path}")
            if d.returncode == 1 and (only is None or path in only):
                stale.append(f"{path}: upstream changed since base {base}")

    def show(title, items, fmt):
        print(f"\n== {title}: {len(items)}")
        if not args.quiet:
            for i in items:
                print("  " + fmt(i))

    show("1. UNCOVERED platform APIs", sorted(uncovered),
         lambda n: f"{n}   <- {', '.join(uncovered[n][:3])}{' ...' if len(uncovered[n]) > 3 else ''}")
    show("2. OVERRIDE DRIFT", drift, str)
    show("3. STALE OVERRIDES (port with UPSTREAM_SYNC.md §7)", stale, str)
    sys.exit(1 if uncovered or drift else 0)


if __name__ == "__main__":
    main()
