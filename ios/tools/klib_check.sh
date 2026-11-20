#!/usr/bin/env bash
# Type-check the iOS Kotlin sources on Linux, in seconds, with no Mac and no CI round trip.
#
#   ios/tools/klib_check.sh            # shims + the upstream files in scope "data" (default)
#   KAV_SCOPE=none ios/tools/klib_check.sh
#   KAV_TESTS=0    ios/tools/klib_check.sh   # skip the iosTest sources
#
# How: the Linux Kotlin/Native distribution ships the iOS platform klibs (UIKit, Foundation, ...), so
# `kotlinc-native -target ios_simulator_arm64 -p library` can compile a klib without Xcode. It cannot link or
# run anything, and Compose cannot be resolved here (Google Maven is blocked), so only code that does not
# touch Compose is checked: compat/, the stamped JvmStd template, the data layer (Compose runtime is
# replaced by klib_check_stubs/). The full UI is checked by CI (`:kav:compileKotlinIosSimulatorArm64`).
#
# Needs: curl, tar, java. Downloads once into $KAV_KLIB_CACHE (default ~/.cache/kav-klib-check).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$HERE/../.." && pwd)"
KN_VERSION="${KN_VERSION:-2.4.0}"
CACHE="${KAV_KLIB_CACHE:-$HOME/.cache/kav-klib-check}"
SCOPE="${KAV_SCOPE:-data}"
TARGET="${KAV_TARGET:-ios_simulator_arm64}"
KN="$CACHE/kotlin-native-prebuilt-linux-x86_64-$KN_VERSION"
mkdir -p "$CACHE"

if [ ! -x "$KN/bin/kotlinc-native" ]; then
  echo ">> fetching Kotlin/Native $KN_VERSION (GitHub release; download.jetbrains.com may be blocked)"
  curl -fsSL -o "$CACHE/kn.tgz" "https://github.com/JetBrains/kotlin/releases/download/v$KN_VERSION/kotlin-native-prebuilt-linux-x86_64-$KN_VERSION.tar.gz"
  tar -xzf "$CACHE/kn.tgz" -C "$CACHE" && rm -f "$CACHE/kn.tgz"
fi
fetch() { [ -f "$CACHE/$2" ] || { echo ">> fetching $2"; curl -fsSL -o "$CACHE/$2" "$1"; }; }
M=https://repo1.maven.org/maven2
fetch "$M/org/jetbrains/kotlinx/kotlinx-coroutines-core-iossimulatorarm64/1.10.2/kotlinx-coroutines-core-iossimulatorarm64-1.10.2.klib" coroutines.klib
fetch "$M/org/jetbrains/kotlinx/atomicfu-iossimulatorarm64/0.26.1/atomicfu-iossimulatorarm64-0.26.1.klib" atomicfu.klib

W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
SRC="$ROOT/android/app/src/main/java"
UP="$W/upstream"; mkdir -p "$UP"
case "$SCOPE" in
  none) ;;
  data) mkdir -p "$UP/uk/noammm/kav/data" "$UP/uk/noammm/kav/ui"
        cp "$SRC"/uk/noammm/kav/data/*.kt "$UP/uk/noammm/kav/data/"
        cp "$SRC"/uk/noammm/kav/ui/Strings.kt "$UP/uk/noammm/kav/ui/"
        # these use Prefs / ActiveJourney from MainActivity.kt and android.graphics.Bitmap (Compose/Skia), which only CI
        # can compile
        rm -f "$UP/uk/noammm/kav/data/Backup.kt" "$UP/uk/noammm/kav/data/JourneyFile.kt" "$UP/uk/noammm/kav/data/RecentRoutes.kt" \
          "$UP/uk/noammm/kav/data/PlacePhotos.kt" ;;
  *) echo "KAV_SCOPE must be none or data"; exit 2 ;;
esac
# honour overrides.txt: drop upstream files an override replaces
if [ -f "$ROOT/ios/overrides.txt" ]; then
  sed 's/#.*//' "$ROOT/ios/overrides.txt" | awk 'NF{print $1}' | while read -r p; do rm -f "$UP/$p"; done
fi
# stamp the JvmStd template once per package
STAMP="$W/stamp"; mkdir -p "$STAMP"
{ echo uk.noammm.kav.compat.selftest; grep -rhm1 '^package ' "$UP" 2>/dev/null | sed 's/^package //'; } | sort -u | while read -r pkg; do
  sed "s/__PACKAGE__/$pkg/" "$ROOT/ios/kav/src/iosMain/jvmstd/JvmStd.kt.template" > "$STAMP/JvmStd_${pkg//./_}.kt"
done

# the real iOS sources, minus anything that needs Compose
IOS="$ROOT/ios/kav/src/iosMain/kotlin"
TESTS=""; [ "${KAV_TESTS:-1}" = 1 ] && TESTS=$(find "$ROOT/ios/kav/src/iosTest/kotlin" -name '*.kt' | grep -v SpikeTest || true)
# files whose first line is //@cmp need Compose, Skia or AndroidX lifecycle, none of which resolve here: CI checks those
FILES=$(find "$IOS/compat" "$IOS/overrides" "$IOS/platform" -name '*.kt' 2>/dev/null | xargs grep -L '^//@cmp' || true)
echo ">> compiling for $TARGET (scope=$SCOPE): $(echo "$FILES" | wc -w) shim files, $(echo "$TESTS" | wc -w) test files, $(find "$UP" "$STAMP" -name '*.kt' | wc -l) upstream/stamped files"
set +e
OUT=$("$KN/bin/kotlinc-native" -target "$TARGET" -p library -nowarn -Xallow-kotlin-package \
  -l "$CACHE/coroutines.klib" -l "$CACHE/atomicfu.klib" \
  -opt-in=kotlin.experimental.ExperimentalNativeApi -opt-in=kotlinx.cinterop.ExperimentalForeignApi \
  -opt-in=kotlin.time.ExperimentalTime -opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi \
  -o "$W/out.klib" $FILES "$STAMP"/*.kt "$HERE"/klib_check_stubs/*.kt \
  $(find "$UP" -name '*.kt') $TESTS 2>&1)
RC=$?
set -e
echo "$OUT" | grep -v 'JAVA_TOOL_OPTIONS' | sed "s#$W/##" || true
if [ $RC -eq 0 ]; then echo ">> OK"; else echo ">> FAILED"; exit 1; fi
