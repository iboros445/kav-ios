# Kav on iOS: migration plan

> Execution plan for an AI agent. Upstream: `ImNoammm/kav` @ `e945f0b` (Kav 2.2).
> Companion docs: `/UPSTREAM_SYNC.md` (porting upstream updates) and `/CLAUDE.md` (repo rules).

## Context

Kav (`ImNoammm/kav`, forked here as `iboros445/kav-ios`, same history up to `e945f0b`) is an
Android-only app: ~18.5k lines of Kotlin + Jetpack Compose. It talks to Moovit's servers, reads a
bundled GTFS timetable (`il.kav`) and draws an offline PMTiles map with MapLibre. The goal is to run
it on the user's iPhone **for free** and keep pulling upstream updates cheaply. A future AI agent
should be able to port upstream changes with minimal, mechanical work.

### Decision: native IPA built with Kotlin Multiplatform + Compose Multiplatform, sideloaded with SideStore

- **PWA / website: rejected.** The app calls `app4/app5.moovitapp.com` directly with Thrift/JSON.
  Moovit sends no CORS headers, so a browser can't make those calls without a proxy server, which
  costs money and breaks the "nothing phones home" promise. iOS Safari also evicts large storage
  (the map is 176 MB), and a web app gets no reliable background location or notifications. Every
  feature would also have to be rewritten in JS, so nothing from upstream would carry over.
- **IPA via KMP/CMP: chosen.** Compose Multiplatform for iOS has been stable since 1.8 (May 2025).
  ~85% of the files are plain Compose and pure Kotlin, and most UI imports are
  `androidx.compose.*`, which CMP publishes under the same package names. The **same upstream
  `.kt` files compile for iOS unchanged**, so an upstream merge mostly *is* the iOS update.
- **Distribution:** an unsigned IPA is built on free GitHub Actions macOS runners (the repo will be
  made public, which makes them unlimited). SideStore re-signs it on the phone with a free Apple ID
  (7-day refresh, 3 active apps, 10 App IDs per 7 days). A SideStore source JSON on GitHub Pages
  gives one-tap updates.

User decisions: repo goes **public**. **Payments come in a later phase** (Pay is stubbed in v1).
v1 trip status uses **notifications + background location**. A **Live Activity is the final,
optional phase**: a widget extension that costs a second App ID once, and Kav must then be
installed via SideStore directly, not inside LiveContainer.

---

## Status log (keep updated)

**Phase 0** (scaffolding + spikes): files written, waiting on the first green `ios.yml` run + the user installing the IPA.
- Pinned: Kotlin **2.4.0**, Compose Multiplatform **1.12.1**, Gradle wrapper **8.14.4**, coroutines 1.10.2
  (`ios/build.gradle.kts`, `ios/kav/build.gradle.kts`). `compose.material3` etc. use the deprecated accessors
  (still work; Gradle prints a deprecation warning).
- **Spike result: Kotlin/Native accepts `package java.*` and `package org.json` declarations**, so the
  "rewrite imports to `kavcompat.*`" fallback is NOT needed. (Verified by compiling the shims with
  `kotlinc-native 2.4.0 -p library` for linuxX64.)
- Local compile check that works on Linux: download `kotlin-native-prebuilt-linux-x86_64-2.4.0.tar.gz` from the
  JetBrains/kotlin GitHub release (download.jetbrains.com is blocked) and run
  `kotlinc-native <pure-Kotlin files> -p library -o x`. It type-checks pure-Kotlin shims in seconds; it cannot link
  (needs blocked downloads) and cannot see `platform.*` (iOS-only) APIs. Use it before spending a CI round trip.
- `String.format` shim (`jvmstd/JvmStd.kt.template`) was checked against `java.util.Formatter` on the JVM for 136 cases.
- `kav.upstreamScope` in `ios/gradle.properties` is `none` in Phase 0; Phase 1 sets `data`, Phase 2 sets `all`.
- `ios/tools/sync_check.py` is implemented (baseline: 144 uncovered platform APIs, which is the Phase 1-2 shim to-do list).
- Repo-root `.gitignore` is upstream-owned, so iOS ignores live in `ios/.gitignore`.

---

## Core architecture: "upstream-pristine + compat layer + overrides"

**Golden rule: nothing under `android/` (or any other upstream file) is ever edited.** All iOS work
lives in new paths: `ios/`, `.github/workflows/ios.yml`, `UPSTREAM_SYNC.md`, `CLAUDE.md`. Then
`git merge upstream/main` can never conflict.

```
ios/
  settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew (+wrapper)   # standalone Gradle build
  overrides.txt            # upstream files NOT compiled for iOS, with the upstream base SHA each was ported from
  kav/                     # KMP module, targets iosArm64 + iosSimulatorArm64, static framework "KavKit"
    build.gradle.kts       # Sync task copies upstream sources (minus overrides) into build/upstream/kotlin
    src/iosMain/kotlin/
      compat/              # shims that keep upstream Android/JVM APIs, in their ORIGINAL package names
        android/content/Context.kt, android/os/Build.kt, android/location/*.kt, ...
        androidx/activity/..., androidx/core/..., androidx/compose/ui/platform/LocalContext.kt, ...
        java/io/File.kt, java/net/HttpURLConnection.kt, java/util/*.kt, java/util/zip/GZIPInputStream.kt, ...
        org/json/JSONObject.kt, JSONArray.kt, JSONTokener.kt
      jvmstd/JvmStd.kt.template  # JVM-only stdlib bits (String.format, Charsets, String(bytes,cs), System,
                                 # synchronized, Volatile, ...), stamped into every upstream package by Gradle
      overrides/           # iOS replacements for files listed in overrides.txt, named <Name>.ios.kt
        uk/noammm/kav/ui/TileMap.ios.kt, TripService.ios.kt, Updates.ios.kt, ...
      platform/            # iOS-only glue: MainViewController(), bridges (interfaces Swift implements), TripTracker
    src/iosTest/kotlin/    # smoke tests run on the simulator in CI
  app/                     # Xcode app, generated by XcodeGen
    project.yml            # XcodeGen spec (no committed .pbxproj)
    Kav/KavApp.swift, ContentView.swift, MapBridge.swift (MapLibre via SPM), Info.plist, Assets.xcassets
  tools/
    sync_check.py          # Linux-runnable: lists upstream imports/FQNs not covered by compat/,
                           # and public symbols an override no longer matches
    make_source_json.py    # writes the SideStore/AltStore source JSON
  INSTALL.md               # phone setup (SideStore) for the user
UPSTREAM_SYNC.md           # the reusable "port upstream changes" runbook (deliverable)
CLAUDE.md                  # short rules for any AI working in this repo (points to UPSTREAM_SYNC.md)
.github/workflows/ios.yml  # build IPA, run sim tests, release, publish SideStore source to Pages
```

### How upstream code gets into the iOS build
In `ios/kav/build.gradle.kts`:
- `val syncUpstream by tasks.registering(Sync::class)` copies `../../android/app/src/main/java/**/*.kt`
  into `build/upstream/kotlin`, **excluding** every path in `ios/overrides.txt`. `iosMain` gets
  `kotlin.srcDir(syncUpstream)` + `src/iosMain/kotlin`. A Sync copy is used rather than
  `srcDir + exclude` because exclude patterns would also hit same-named override files, and the copy
  gives a hook for the fallback below.
- A second generated dir holds `JvmStd.kt` stamped once per distinct `package` found in upstream
  sources (today: `uk.noammm.kav`, `.data`, `.ui`). Same-package declarations beat default imports,
  so `String.format(...)`, `Charsets.UTF_8`, `String(bytes, Charsets.UTF_8)`, `System.currentTimeMillis()`
  and `synchronized {}` resolve without touching upstream. New upstream packages get covered automatically.
- **Fallback (only if the Phase 0 spike shows Kotlin/Native refuses `package java.*`):** add a
  `filter {}` to the Sync task that rewrites `java.` / `javax.` to `kavcompat.java.` /
  `kavcompat.javax.` in imports and FQNs, and move those shims to `compat/kavcompat/...`.

### Shim vs override: decision rule (also goes into UPSTREAM_SYNC.md)
- **Shim** (compat/) when the API surface is small and reused across files. Examples: `Context`,
  `SharedPreferences` (backed by NSUserDefaults, one suite per prefs name), `File` (NSFileManager),
  `HttpURLConnection`/`URL` (blocking NSURLSession + semaphore; upstream already calls it on
  `Dispatchers.IO`), `org.json` (pure-Kotlin parser), `UUID`, `Locale`, `SimpleDateFormat`/`Date`
  (NSDateFormatter), `GZIPInputStream` (platform.zlib, windowBits 31), `Base64`, `Log` (NSLog + a
  rolling log file in Documents, readable in the Files app), `Handler`/`Looper` (main queue),
  `SystemClock`, `Toast`, `Intent`/`Uri` (ACTION_VIEW opens via `UIApplication.openURL`), `Build`.
- **Override** (overrides/ + overrides.txt) when a file is mostly platform glue. Each override must
  keep **every non-private top-level declaration and signature** of the upstream file, so callers
  compile unchanged. The first line of each override is a header comment:
  `// iOS override of android/.../X.kt @ <upstream sha>. Keep public API identical.`
- **Never** put iOS logic in a copy of a mostly-portable file just to fix one call. Add a shim instead.

### Key behaviours to get right
- **`android.os.Build` shim returns fixed, plausible Android values** (e.g. `MANUFACTURER="Google"`,
  `PRODUCT="husky"`, `VERSION.RELEASE="14"`, `SDK_INT=34`). `Moovit.kt` registers as the Android
  Moovit client (`i32Field(6, 2)`), and mixing in iOS identifiers could break the session.
  `SDK_INT=34` also enables the Glass/Liquid paths, so the Liquid override must work (or set
  `liquidGlassReady=false` until it does).
- **HttpURLConnection + gzip:** NSURLSession decodes `Content-Encoding: gzip` transparently. The shim
  must hide that header (or return identity) so upstream code doesn't gunzip a second time.
  Preserve header case/semantics that `Moovit.adoptRevision` reads, plus redirects (MoovitLink resolves
  OneLink) and `instanceFollowRedirects`.
- **Activity shim:** `ComponentActivity` (LifecycleOwner with a LifecycleRegistry driven by UIApplication
  notifications) captures the `setContent {}` lambda. `MainActivity.kt` compiles **unchanged**. The
  entry point in `platform/` is `fun MainViewController(): UIViewController`: create `MainActivity()`,
  call `onCreate(null)`, return `ComposeUIViewController { activity.content() }`. `onOpenURL` in Swift
  calls `onNewIntent(Intent(ACTION_VIEW, Uri.parse(url)))`. `WindowCompat` insets controller
  `isAppearanceLightStatusBars` drives the UIStatusBarStyle via a callback. PiP APIs are no-ops.
  `LocalView` is a dummy CompositionLocal.
- **`rememberLauncherForActivityResult` shim** supports the contracts upstream uses: RequestPermission /
  RequestMultiplePermissions (location → CLLocationManager authorization; camera →
  AVCaptureDevice), CreateDocument / OpenDocument (UIDocumentPickerViewController, used by `.kav`
  backup export/import). An unknown contract logs and returns a failed result.
- **Location / heading:** the `LocationManager` + `Location` shims are backed by CLLocationManager
  (`getLastKnownLocation` = cached `location`, providers report "gps"). For `SensorManager`
  (`trackHeading`), synthesize a TYPE_ROTATION_VECTOR event from `CLHeading.trueHeading` as a
  yaw-only quaternion. Verify on device that the map turns the right way. Lock the app to portrait,
  so the `Surface` rotation is always 0.
- **Assets:** `ctx.assets.open(path)` reads `NSBundle.mainBundle/assets/<path>`. CI copies
  `android/app/src/main/assets/**` (map style, fonts, sprites) plus `il.kav` into the app bundle.
  `filesDir` = Application Support (excluded from iCloud backup). The 176 MB `israel.pmtiles` is
  downloaded by the unchanged `MapFile.kt` from upstream's public `map-1` release.
- **Map (`TileMap.kt` override):** upstream keeps the camera and gestures in Compose (`MapCamera`,
  `detectTransformGestures`) and uses MapLibre only as a renderer (`setAllGesturesEnabled(false)`,
  `driveTo`). The override keeps every Compose part verbatim and swaps only the MapLibre calls for a
  Kotlin `NativeMap` interface (in `platform/`). Swift implements it in `MapBridge.swift` with
  **MapLibre Native iOS via SPM** (`maplibre/maplibre-gl-native-distribution`; PMTiles `pmtiles://file://`
  is supported from 6.10). Bridge surface: `setStyleJson`, `setGeoJson(sourceId, json)`,
  `addLayer(json, below)`, `setLayerProperty(id, key, jsonExpr)`, `addImage(name, pngBytes)`,
  `setCamera(lat, lon, zoom, bearing, pitch, anchor)`, `isStyleLoaded`. Swift builds layers generically
  from MapLibre style-spec JSON (`NSExpression(mglJSONObject:)` + KVC on the matching `MLN*StyleLayer`),
  so new layer properties upstream need no Swift change. Embed with `UIKitView(..., properties =
  UIKitInteropProperties(isInteractive = false))` so touches reach the Compose gesture overlay.
  Pre-render the `modeMark`/arrow images to PNG via Skia (`ImageBitmap` → `Image.encodeToData`).
- **Trip in progress (`TripService.kt`, `TripNotice.kt` overrides):** an iOS `TripService` object with the
  same API. While a trip is active it turns on CLLocationManager background updates
  (`allowsBackgroundLocationUpdates`, `showsBackgroundLocationIndicator`) and keeps the step logic
  running. Upstream's alerts become `UNUserNotificationCenter` local notifications: departure and
  reminder alerts are pre-scheduled with time triggers so they fire with the app closed. Reuse
  upstream's pure text/decision logic wherever it lives outside the Android-only classes.
- **Updates (`data/Updates.kt` + `ui/Update.kt` overrides):** same public API. Check
  `iboros445/kav-ios` releases (tag `ios-v*`) and, when newer, offer "Open SideStore"
  (`sidestore://` deep link to the source; verify the exact URL scheme, else open the release page).
- **Liquid glass (`ui/Liquid.kt` override):** port the AGSL `RuntimeShader` to Skia
  `RuntimeEffect.makeForShader` + `ImageFilter.makeRuntimeShader`, then `.asComposeRenderEffect()`.
  AGSL and SkSL are nearly identical. Until it works, have the override report `liquidGlassReady=false`.
- **Pay (v1):** `ui/Pay.kt`, `ui/PayHistory.kt`, `ui/Qr.kt` are overridden with **stubs** that keep the
  same non-private symbols (`PayScreen`, `PaymentsSection` rendering nothing, `PayTicketsCard`, `Payer`,
  ...). `data/MoovitPay.kt` and `ui/PayPurchase.kt` stay upstream. (Phase 6 removes the stubs.)
- **Deep links / files (Info.plist):** `CFBundleURLTypes` for `moovit`. The `https://moovitapp.com/...`
  universal links can't be claimed (no AASA on their domain), which is a known limitation.
  `UTImportedTypeDeclarations` + `CFBundleDocumentTypes` for `.kav`, plus
  `LSSupportsOpeningDocumentsInPlace` and `UIFileSharingEnabled`.
  `NSLocationWhenInUseUsageDescription`, `NSLocationAlwaysAndWhenInUseUsageDescription`,
  `NSCameraUsageDescription`, `UIBackgroundModes: [location]`, `ITSAppUsesNonExemptEncryption=false`.
  Bundle ID `io.github.iboros445.kav` (SideStore re-suffixes it). Min iOS 16.0.

### Dependencies (pin the latest stable at execution time, checked via Maven Central metadata)
Baseline as of this plan: Kotlin ≥ 2.2.20, `org.jetbrains.compose` 1.9.x (CMP ≥ 1.8 is the iOS-stable
line), `org.jetbrains.compose.material3`, `org.jetbrains.compose.material:material-icons-extended:1.7.3`
(last published; works on newer CMP), `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` +
`lifecycle-viewmodel-compose` (same `androidx.lifecycle.*` packages; upstream's `viewModel { KavModel(...) }`
initializer form works on KMP), `kotlinx-coroutines-core`. MapLibre iOS comes in through SPM in
`project.yml`, not through Kotlin.

---

## Phases (commit + push to `claude/ios-migration-plan-4ojstt` at the end of each)

The Linux container **cannot compile Kotlin for iOS**. Every compile check runs on GitHub Actions
`macos-15`. Read the logs with `mcp__github__actions_list` / `get_job_logs`. Run
`ios/tools/sync_check.py` locally first to predict missing shims and cut down CI round-trips. Batch
fixes per CI run.

**Phase 0: scaffolding + spikes**
1. User actions (ask the user; Sonnet can't do them): make the repo **public**; Settings → Pages →
   Source: **GitHub Actions**.
2. `git remote add upstream https://github.com/ImNoammm/kav.git`.
3. `CLAUDE.md` and `UPSTREAM_SYNC.md` already exist (written with this plan). Create `ios/overrides.txt`
   in the format `UPSTREAM_SYNC.md` defines, and keep both docs accurate as the build takes shape.
4. Create the `ios/` Gradle build and the KMP module, the XcodeGen app with a "Hello" Compose screen,
   and `.github/workflows/ios.yml`:
   setup-java 21 → cache `~/.gradle` + `~/.konan` → fetch `il.kav` → `./gradlew :kav:linkReleaseFrameworkIosArm64`
   → `brew install xcodegen && xcodegen` → `xcodebuild -sdk iphoneos -configuration Release
   CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""` → `mkdir Payload && cp -r Kav.app
   Payload/ && zip -r Kav.ipa Payload` → upload artifact. Triggers: push (any branch), workflow_dispatch,
   tags `ios-v*`.
   - Getting `il.kav` (unlicensed GTFS, so never committed): `gh release download --repo ImNoammm/kav
     --pattern '*.apk'` then `unzip -p *.apk assets/il.kav`. Fallback: `tools/fetch.sh` +
     `KAV_REGION=il KAV_BBOX=national python3 tools/export_web_bundle.py` with `KAV_OUT` set to a temp dir.
   - Version: `CFBundleShortVersionString` = upstream `versionName` parsed from
     `android/app/build.gradle.kts`; `CFBundleVersion` = `github.run_number`.
   - App icon: generated from `.github/icon.png` with `sips` at build time.
5. **Spike** in the same first CI run: a compat file declaring `package java.io; class File(...)` and
   `package org.json`, plus a test that uses them. If `java.*` is refused, switch to the rewrite
   fallback now.
- ✅ Checkpoint: CI produces `Kav.ipa`. The user installs it via SideStore and sees "Hello".

**Phase 1: data layer**: compile all of `data/*.kt` (overrides: `Updates.kt` only, as a stub for now) plus
`ui/Strings.kt`, `ui/Theme.kt`. Write the Foundation shims (json, io, net, zip, text, util, Build, Context
skeleton). Add `iosTest` smoke tests run with `./gradlew :kav:iosSimulatorArm64Test`: `Net.read` on
the bundled `il.kav` (copy it into test resources in CI), org.json round-trips, Thrift encode/decode,
and gzip.
- ✅ Checkpoint: the simulator tests pass in CI.

**Phase 2: whole UI compiles and runs**: drop the `data/`-only restriction and compile everything except the
overrides. Add the Activity/Compose/AndroidX shims, the launcher shim, and the location/heading shims.
Initial overrides: `ui/TileMap.kt` (a placeholder box with the same API), `ui/Liquid.kt`
(`liquidGlassReady=false`), `ui/Qr.kt`, `ui/Pay.kt`, `ui/PayHistory.kt` (stubs), `TripService.kt`,
`TripNotice.kt` (no-op), `ui/Update.kt`/`data/Updates.kt` (no-op), plus anything else the rule sends
to override (likely `ui/StopPhotos.kt` only if its Bitmap/LruCache use is too broad to shim).
- ✅ Checkpoint: on the phone, onboarding → trip planning against Moovit → results → Lines/Stations/Live
  lists work in Hebrew (RTL) and English. Settings, favourites, `.kav` backup export/import all work.

**Phase 3: map**: real `TileMap.ios.kt` + `MapBridge.swift`. Test the map download flow, offline
rendering, route lines/dots/markers, live bus markers with the halo pulse, follow mode with tilt,
twist-to-rotate, light/dark styles.
- ✅ Checkpoint: the map matches Android behaviour on the phone, including in airplane mode.

**Phase 4: trip in progress + notifications**: iOS `TripService`/`TripNotice` overrides, background
location during an active trip, local notifications (permission prompt), reminders with the app
closed, moovit:// deep links, `.kav` open-in.
- ✅ Checkpoint: start a real trip, lock the phone, and the alerts arrive. The steps have advanced when you reopen.

**Phase 5: release pipeline + updates + polish**: the tag `ios-vX` workflow creates a GitHub Release with
`Kav.ipa`. `make_source_json.py` writes `source.json` (AltStore/SideStore source format: name,
identifier, apps[] with bundleIdentifier, developerName, iconURL, versions[{version, buildVersion,
date, downloadURL, size, localizedDescription}]) and the workflow deploys it to Pages at
`https://iboros445.github.io/kav-ios/source.json`. Real `Updates.ios.kt`/`Update.ios.kt`. Liquid-glass
Skia port. `ios/INSTALL.md` for the user: install SideStore (one-time computer step with the SideStore
installer + pairing file), turn on Developer Mode, LocalDevVPN, add the source URL, install Kav,
refresh within 7 days (Shortcuts automation for auto-refresh). Finalize `UPSTREAM_SYNC.md` with the
real override list and the lessons learned.

**Phase 6 (later): payments**: replace the Pay/PayHistory/Qr stubs. Compile upstream `Pay.kt` and
`PayHistory.kt` unchanged if possible, with shims for `KeyStore`/`KeyGenerator`/`Cipher`/
`GCMParameterSpec` backed by a Swift `CryptoBridge` (CryptoKit AES-GCM, key stored in Keychain),
`WindowManager.FLAG_SECURE` (no-op or a secure-text-field screenshot guard) and the material3
`DateRangePicker` (available in CMP). `Qr.kt` override: AVFoundation QR scanning via a
`UIKitView` camera preview. Ticket QR drawing already uses zxing → swap to a pure-Kotlin QR
encoder (e.g. `io.github.alexzhirkevich:qrose`) via a small override or shim of the zxing
classes in use.

**Phase 7 (optional, last): Live Activity**: add a WidgetKit extension target in `project.yml` (ActivityKit,
iOS 16.1+). The Kotlin `TripService.ios.kt` posts start/update/end through a Swift `LiveActivityBridge`
(local updates only, no push). Document in INSTALL.md: costs 1 extra App ID once, and Kav must be
installed through SideStore directly (not LiveContainer).

---

## Spec for `UPSTREAM_SYNC.md` (the reusable runbook: already written at the repo root; finalize in Phase 5)

Audience: an AI agent with this repo cloned and no prior context. Sections:
1. **Invariants**: never edit upstream paths (`android/`, `tools/`, root `README.md`, `LICENSE`,
   `.gitignore`, `.github/icon.png`). The iOS-owned paths are listed. Overrides keep the upstream
   public API identical.
2. **Fetch & merge**: `git fetch upstream && git merge upstream/main` (no rebase; it keeps the history
   shared). If a merge conflict happens anyway, the conflicting file must be upstream-owned: take
   theirs (`git checkout --theirs`).
3. **Triage the delta**: `git diff <old-upstream-head>..upstream/main --stat -- android/` and
   classify every changed file: (a) compiled as-is → nothing to do unless new platform APIs appear;
   (b) listed in `ios/overrides.txt` → port it; (c) new file → compiled automatically, check it for
   platform APIs. Also check for new assets, permissions in `AndroidManifest.xml` (map them to Info.plist),
   dependencies in `build.gradle.kts` (find KMP equivalents or shim them), and the `versionName` bump.
4. **Predict breakage locally**: `python3 ios/tools/sync_check.py` prints (i) imports/FQNs under
   `android.*`, `java.*`, `javax.*`, `org.json`, `androidx.{core,activity,lifecycle,camera}` and other
   non-KMP libraries with no compat shim, (ii) for each override, the upstream non-private symbols
   it's missing or whose signatures changed, (iii) overrides whose upstream file changed since the
   recorded base SHA.
5. **Port overrides**: for each one, read `git diff <base-sha>..upstream/main -- <upstream path>`,
   apply the same behavioural change to `ios/kav/src/iosMain/kotlin/overrides/...<Name>.ios.kt`,
   keeping the Compose parts verbatim, then bump the SHA in the header and in `overrides.txt`.
6. **Extend shims**: add only the members actually used. Prefer extending compat over adding an
   override (the decision rule from this plan).
7. **Build & verify**: push → watch `ios.yml` → fix → repeat. `iosSimulatorArm64Test` must pass. Then
   tag `ios-v<upstreamVersion>[.<n>]` to publish the release and SideStore source.
8. **Report**: summarize which upstream features landed on iOS, which are stubbed, and any new
   limitations, and append to the "Sync log" table at the bottom (date, upstream SHA, iOS tag, notes).
9. **Known iOS differences** (kept current): no PiP, no universal links for moovitapp.com, Pay
   status, Live Activity status, APK self-update replaced by SideStore.

`CLAUDE.md` (root, ~15 lines): the golden rule, where the iOS code lives, that iOS compiles only in CI,
and "for upstream updates follow UPSTREAM_SYNC.md".

---

## Critical files
- Upstream, read-only, compiled for iOS: `android/app/src/main/java/uk/noammm/kav/**` (notably
  `MainActivity.kt`, which holds `KavModel`, `Prefs`, `Root`/`Shell`, and location helpers, and must
  compile unchanged), `data/Moovit.kt`, `data/Net.kt`, `data/MapFile.kt`.
- Initial override set: `ui/TileMap.kt`, `ui/Liquid.kt`, `ui/Qr.kt`, `ui/Pay.kt`, `ui/PayHistory.kt`,
  `TripService.kt`, `TripNotice.kt`, `data/Updates.kt`, `ui/Update.kt`.
- Assets reused as-is: `android/app/src/main/assets/map/**`. The timetable comes from the upstream APK.
- New: everything under `ios/`, `.github/workflows/ios.yml`, `UPSTREAM_SYNC.md`, `CLAUDE.md`.

## Verification
- **Every push:** the `ios.yml` run is green: the framework links, the IPA is built and attached as an
  artifact, and `iosSimulatorArm64Test` passes (bundle parsing, JSON/Thrift/gzip shims).
- **Locally (Linux):** `python3 ios/tools/sync_check.py` reports nothing missing.
  `git diff upstream/main -- android tools README.md LICENSE .gitignore` is **empty** (the golden rule holds).
- **On device, per phase checkpoint** (the user installs via SideStore): onboarding + map download;
  plan a trip; Live, Lines and Stations; Hebrew RTL; backup round-trip; offline map in airplane mode;
  an active trip with the screen locked delivers notifications; a `moovit://` link opens Kav.
  Debugging without a Mac: the `Log` shim's rolling file in Files → Kav.
- **Sync tabletop test (Phase 5):** have `sync_check.py` accept `--from <sha> --to <sha>`. Run it
  on the real historic delta `5a181ae..e945f0b` (Kav 2.1 → 2.2) and confirm it classifies the changed
  files correctly (as-is / override / new) and flags their platform APIs. The first real sync is the
  next upstream release, done strictly by following `UPSTREAM_SYNC.md`.
