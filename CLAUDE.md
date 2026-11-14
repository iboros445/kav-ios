# Kav iOS fork: rules for AI agents

This repo is `ImNoammm/kav` (an Android app) plus an iOS build of the same code.

- **Never edit upstream-owned files**: `android/**`, `tools/**`, `README.md`,
  `LICENSE`, `.gitignore`, `.github/icon.png`, or anything else that exists in
  `upstream/main`. That keeps `git merge upstream/main` conflict-free.
- iOS code lives only in `ios/` and `.github/workflows/ios.yml`. Upstream Kotlin
  compiles for iOS unchanged through shims in
  `ios/kav/src/iosMain/kotlin/compat/` (declared in the original Android/JVM
  package names). The few files that can't are replaced by
  `ios/kav/src/iosMain/kotlin/overrides/**/<Name>.ios.kt`, listed in
  `ios/overrides.txt`.
- Kotlin/Native can't build iOS on Linux. Check locally with
  `python3 ios/tools/sync_check.py`, then compile for real by pushing and reading
  the `ios.yml` GitHub Actions logs.
- Building the iOS port from scratch: follow `ios/MIGRATION_PLAN.md`.
- Bringing in upstream updates: follow `UPSTREAM_SYNC.md` step by step.
