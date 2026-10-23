# Kiro Bot 3.0 — final stabilization audit (2026-10-23)

Branch: `final/kiro-bot-3.0`. This is a source review, **not** evidence of a successful Android build or device test.

## Verified
- Telegram-only: `BotDataManager.kt` filters bots by `platform=telegram`, and `MAX_BOTS=1`.
- `TokenCrypto.kt` implements Android Keystore AES/GCM token encryption.
- **P0 security issue:** `BotDataManager.kt` function `encryptOrPlain` catches Keystore failures and returns the plaintext token. `saveBots` persists that result into internal `bots.json`. This contradicts unconditional encrypted-at-rest claims. A fix must fail closed, preserve existing encrypted records, surface an error, and avoid losing a user's token or bot configuration. Add instrumented tests for Keystore failure, migration, and restoration before changing production code.
- `AndroidManifest.xml` requests `MANAGE_EXTERNAL_STORAGE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, foreground service permissions and `largeHeap=true`. Audit necessity and Android behavior before removing anything.
- `app/build.gradle` retains applicationId `com.vkbot.manager`; do **not** rename casually because it changes Android app identity and upgrade compatibility.
- Answer database cleanup commit `b6cbed8561de0a0e49a7d988a5601d0d5bb23eca` updated 16 obsolete self-description/project-link records while preserving 14,995 nonempty records. Format was checked structurally, not via Android parser.

## Release blockers
1. Resolve historical FP iHA BOT engine and response database redistribution permissions; see `LICENSE.md`, `UPSTREAM_ATTRIBUTION_REVIEW.md`, `COMPONENT_PROVENANCE_COMPARISON.md` and `ANSWER_DATABASE_PROVENANCE_AUDIT.md`.
2. Fix plaintext token fallback and test failed Keystore operations.
3. Verify backup/restore and existing-install upgrade, especially token migration and answer database preservation.
4. Run Gradle unit/instrumentation tests, release build and real-device Telegram private/group/foreground/background checks.
5. Verify privacy policy, data deletion, Android permissions, dependencies and secret scan against code.
6. Do not publish a new APK or declare release complete until the above are resolved.

## Branch status
At time of audit, `audit/compliance-review-2026-10-22` was 3 commits behind `main`, and `feature/add-tests` was 64 commits behind `main`, with neither branch ahead. No merge was required.

## Scope
No token values were read or exposed. No runtime tests or Gradle builds were executed in this review. Production security code was not modified pending regression-test coverage.
