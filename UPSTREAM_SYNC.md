# Syncing Kav iOS with upstream

A runbook for bringing changes from the Android app (`ImNoammm/kav`) into this
iOS fork (`iboros445/kav-ios`). It is written for an AI agent that has the repo
cloned and no other context. Follow it top to bottom every time.

How the fork is built is described in `ios/MIGRATION_PLAN.md`. In short: the
upstream Kotlin files are compiled **unchanged** for iOS through a compat layer
of shims. The few files that can't work that way have iOS replacements
("overrides"). Merging upstream therefore updates most of the iOS app with no
porting work at all.

---

## 0. Invariants (never break these)

- **Never edit an upstream-owned path.** Upstream owns everything that exists in
  `upstream/main`: `android/**`, `tools/**`, `README.md`, `LICENSE`,
  `.gitignore`, `.github/icon.png`, and anything else upstream adds later.
  If something there seems to need a change, fix it in a shim or an override instead.
- The iOS fork owns: `ios/**`, `.github/workflows/ios.yml`, `UPSTREAM_SYNC.md`,
  `CLAUDE.md`. Upstream never touches these, so merges never conflict.
- An override keeps **every non-private top-level declaration of its upstream
  file, with the same signature**. Other upstream files call those declarations.
- Prefer extending a shim over adding an override (see §6).
- Never commit the GTFS timetable (`il.kav`). It has no licence, so CI fetches it.

Check the first invariant at any time (it must print nothing):

```sh
git fetch upstream
git diff upstream/main -- $(git ls-tree -r --name-only upstream/main)
```

## 1. Set up (once per clone)

```sh
git remote get-url upstream 2>/dev/null || git remote add upstream https://github.com/ImNoammm/kav.git
git fetch upstream --tags
```

## 2. Find what changed

The last synced upstream commit is the newest `upstream=` entry in the
**Sync log** at the bottom of this file. Call it `OLD`. `NEW` is `upstream/main`.

```sh
OLD=<sha from the sync log>
NEW=$(git rev-parse upstream/main)
git log --oneline $OLD..$NEW                 # read every commit message, they explain intent
git diff --stat $OLD..$NEW
```

If `OLD == NEW`, there is nothing to do. Stop.

## 3. Merge

Work on the branch you were told to use. Otherwise use `sync/upstream-<short NEW>`.

```sh
git merge --no-ff upstream/main -m "Merge upstream $(git rev-parse --short upstream/main)"
```

- Never rebase or squash. The shared history is what keeps future merges clean.
- A conflict can only hit an upstream-owned file, which means someone broke §0.
  Resolve with `git checkout --theirs <file>`, then move the local change into
  a shim or override.

## 4. Triage every changed upstream file

For each path in `git diff --name-status $OLD..$NEW`, decide which case applies:

| Case | How to recognise it | What to do |
|---|---|---|
| **A. Compiled as-is** | `.kt` under `android/app/src/main/java/**`, not listed in `ios/overrides.txt` | Nothing, unless §5 reports new platform APIs |
| **B. Overridden** | listed in `ios/overrides.txt` | Port the change by hand (§7) |
| **C. New Kotlin file** | status `A`, `.kt` | Compiled automatically, so check it with §5. If it is mostly platform glue, override it (§6) |
| **D. Deleted / renamed Kotlin file** | status `D`/`R` | If it was overridden, delete or rename the override and its `overrides.txt` line |
| **E. Assets** | `android/app/src/main/assets/**` | Bundled by CI automatically. New *kinds* of asset may need `ios.yml` changes |
| **F. Android resources** | `android/app/src/main/res/**` | New `R.*` ids used from Kotlin go into the `R` shim. App-icon changes: regenerate in CI from `.github/icon.png` |
| **G. Manifest** | `AndroidManifest.xml` | Map new permissions, intent filters and services to `ios/app/Kav/Info.plist` (table below) |
| **H. Gradle deps** | `android/app/build.gradle.kts` | Find a KMP equivalent and add it to `ios/kav/build.gradle.kts`, or shim the few classes used. The `versionName` bump is picked up by CI |
| **I. Tools / README** | `tools/**`, `README.md` | Usually nothing. If the `il.kav` format or name changed, update the fetch step in `ios.yml` |

Manifest → iOS mapping:

| Android | iOS |
|---|---|
| `ACCESS_FINE/COARSE_LOCATION` | `NSLocationWhenInUseUsageDescription` |
| foreground service with `location` | `UIBackgroundModes: location` + `NSLocationAlwaysAndWhenInUseUsageDescription` |
| `POST_NOTIFICATIONS` | `UNUserNotificationCenter.requestAuthorization` (code, not plist) |
| `CAMERA` | `NSCameraUsageDescription` |
| `intent-filter` with a custom scheme | `CFBundleURLTypes` |
| `intent-filter` for `https://<host>` | not possible without the host's AASA file. Record it under Known differences |
| `intent-filter` for a file extension | `UTImportedTypeDeclarations` + `CFBundleDocumentTypes` |
| `REQUEST_INSTALL_PACKAGES` / self-update | replaced by the SideStore source (see `Updates.ios.kt`) |

## 5. Predict compile breakage locally (Linux, no Mac needed)

```sh
python3 ios/tools/sync_check.py --from $OLD --to $NEW
```

It reports:
1. **Uncovered APIs:** imports and fully-qualified names under `android.*`,
   `androidx.{core,activity,lifecycle,camera,...}`, `java.*`, `javax.*`,
   `org.json.*` and other non-KMP libraries that no file in
   `ios/kav/src/iosMain/kotlin/compat/` declares. It also flags JVM-only stdlib
   calls (`String.format`, `Charsets`, `synchronized`, `System.*`, ...) that
   `jvmstd/JvmStd.kt.template` lacks.
2. **Override drift:** for each override, the upstream non-private declarations
   it is missing or whose signatures changed.
3. **Stale overrides:** overrides whose upstream file changed after the base SHA
   recorded in `ios/overrides.txt`. Every one of these needs §7.

Keep going until report 1 and report 2 are empty. Report 3 lists the porting
work left to do.

Then type-check the iOS Kotlin on Linux, in seconds:

```sh
ios/tools/klib_check.sh        # shims, JvmStd template, the data layer and iosTest, against the real iOS platform klibs
```

It downloads the Kotlin/Native toolchain once (from the JetBrains/kotlin GitHub release) and compiles for
`ios_simulator_arm64` without Xcode. It cannot see Compose, Skia, or AndroidX lifecycle (Google Maven is not
reachable from the cloud sandbox), so any shim file whose first line is `//@cmp` is skipped and only CI
checks it. Everything else, including every Foundation, UIKit, CoreLocation and Security call, is checked here.
Real compile errors for the rest come from CI (§8).

## 6. Shim or override: the decision rule

- **Extend a shim** (in `compat/`, using the **original package name**,
  e.g. `compat/android/content/Context.kt`) when the new API is a class, function
  or member that has a reasonable iOS equivalent and may be reused. Implement only
  the members that are actually used. Match Android semantics, including
  nullability, exceptions and threading.
- **Add an override** only when most of a file is platform glue (views, services,
  OS integrations). Then:
  1. Copy the upstream file to `ios/kav/src/iosMain/kotlin/overrides/<same package path>/<Name>.ios.kt`.
  2. First line: `// iOS override of android/app/src/main/java/<path>/<Name>.kt @ <upstream sha>. Keep public API identical.`
  3. Keep everything portable verbatim (especially Compose UI) and replace only the platform parts.
  4. Add a line to `ios/overrides.txt` (format below).
- Never override a file to change one call. Shim that call instead.
- If CMP itself adds a declaration that collides with a shim (redeclaration
  error), delete the shim and use CMP's version.

`ios/overrides.txt` format, one line per file, with the path relative to
`android/app/src/main/java/`:

```
# path                              base-sha   reason
uk/noammm/kav/ui/TileMap.kt         e945f0b    MapLibre iOS via MapBridge.swift
```

## 7. Port the changes into overrides

For each stale override:

```sh
BASE=<base-sha from overrides.txt>
git diff $BASE..$NEW -- android/app/src/main/java/<path>
```

- Apply the **same behavioural change** to the `.ios.kt` file. Compose code
  usually transfers line for line. Platform calls need their iOS counterpart
  (the entry point lives in `ios/kavkit/src/iosMain/kotlin/`, other bridges in `ios/kav/src/iosMain/kotlin/platform/` and Swift in
  `ios/app/Kav/`).
- If the upstream change needs new native capability, add it to the Kotlin
  bridge interface and implement it in Swift. For the map, prefer passing
  MapLibre style-spec JSON through the generic `addLayer` and `setLayerProperty`
  calls rather than adding a new Swift method.
- Update the SHA in the file's header comment **and** in `overrides.txt` to `NEW`.
- A feature that can't work on iOS yet gets a visible, graceful stub (hide the
  entry point or show "Not available on iPhone yet"), never a crash. Record it
  under **Known differences**.

## 8. Build and verify in CI

```sh
git push -u origin <branch>
```

- `.github/workflows/ios.yml` runs on every push. It links the Kotlin framework,
  runs `iosSimulatorArm64Test`, builds the unsigned `Kav.ipa`, and attaches it as
  a workflow artifact.
- Read failures with the GitHub MCP tools (`actions_list` → `get_job_logs` with `return_content=true`,
  `tail_lines=3000`). The result is saved to a file: run `python3 ios/tools/ci_errors.py <that file>` for the
  compiler errors alone. Fix all the errors from one log in one commit before pushing again. Every round
  trip costs about 10 minutes; the `Compile Kotlin` step fails first and fast, before tests and linking.
- "Flaky" is not a diagnosis. Find the real cause.
- Done means a green run on the merge commit and an empty §0 invariant check.

## 9. Release

Only after CI is green, and only if the user wants a release:

```sh
VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' android/app/build.gradle.kts)
git tag ios-v$VERSION        # add .1, .2 ... for an iOS-only rebuild of the same upstream version
git push origin ios-v$VERSION
```

The tag workflow creates the GitHub Release with `Kav.ipa` and republishes the
SideStore source (`https://iboros445.github.io/kav-ios/source.json`). The phone
offers the update in SideStore.

## 10. Report and log

Tell the user:
- the upstream versions/commits brought in (`$OLD..$NEW`) and what they add,
- which of those work on iOS, which are stubbed, and why,
- whether a release was tagged.

Then add a row to the Sync log below, update Known differences, and commit both
with the merge or right after it.

---

## Known differences from Android

Keep this list current. It is the user-facing truth about the iOS build.

| Feature | iOS status |
|---|---|
| Picture-in-picture during navigation | Not available (iOS PiP is for video). The Live Activity is the planned substitute |
| Opening `https://moovitapp.com/...` links | Not possible (needs Moovit's AASA file). `moovit://` links work |
| Self-update from GitHub | Replaced by the SideStore source |
| Paying for rides (Moovit Pay) | Stubbed until migration Phase 6 |
| Ongoing-trip notification | Local notifications + background location. Live Activity in Phase 7 |
| The rider's proxy (Settings, 4.3) | Kav's own requests go through it (HTTP/HTTPS and SOCKS, with a login). The pages Kav shows (Street View, Rav-Pass, the report form) don't: WKWebView only takes a proxy from iOS 17, and none with a login |
| GovMap layers (4.3) | MapLibre iOS sends its own requests, without the Referer GovMap's tile server wants, so it may refuse them (`GovMapTiles.ios.kt`) |
| A trip's map picture in the share card (4.3) | Not yet: `MapSnapshotter` fails at once until MapBridge.swift wraps MLNMapSnapshotter. The card goes without it |
| Sharing straight to WhatsApp/Telegram (4.3) | iOS doesn't let an app list or address other apps: Copy link and More (the system sheet) only |
| 3D buildings leaning in with the tilt (4.3) | The base style's `buildings_3d` layer isn't one Kav made, so `getLayer` doesn't find it on iOS: they show at the style's own opacity |
| The launch screen's arrow flying away (4.3) | iOS shows its own launch screen; Kav fades in after it |

## Sync log

| Date | upstream= | iOS tag | Notes |
|---|---|---|---|
| 2026-10-07 | e945f0b | — | Fork baseline (Kav 2.2). iOS migration planned |
| 2026-10-08 | dcd6ab5 | — | Kav 2.3 + 2.3.1. Moovit CLIENT_VERSION 5.201.5.1810. Ported `QrScanner(onFail)`; `TripService` API unchanged (base bumped) |
| 2026-11-04 | ede72bd | — | Kav 3.2-nightly to 3.3.1 (own providers, signed timetable, Rav-Pass). Never built: GitHub locked the account's Actions (billing) |
| 2026-11-13 | 449cd8a | — | Kav 4.3. Upstream replaced its history with one commit, so the merge is `--allow-unrelated-histories` taking upstream's tree for every upstream path; later syncs merge normally. New overrides: RiderProxy, GoogleSession, GovMapTiles, KavApp; MoovitWebPage gone. Built on the IdanPrivateSpace fork while the account's Actions are locked |
