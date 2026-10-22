# Kiro Bot 3.0.1 — legacy component replacement checklist

Status: audit only. This is **not** a statement that any upstream code or data is licensed for redistribution.

## Priority 0 — rights and provenance (release blocker)
- [ ] Obtain the actual license or written permission for FP IHA BOT / vk-iha-bot-new from the relevant rights holder, or replace inherited implementation and data with independently created, appropriately licensed material.
- [ ] Review `EARLY_JAVA_UPSTREAM_METHOD_COMPARISON.md`, `COMPONENT_PROVENANCE_COMPARISON.md`, and `ANSWER_DATABASE_PROVENANCE_AUDIT.md` for potentially inherited algorithms, methods and content. Kotlin translation, refactoring, and AI-assisted rewriting alone do **not** establish independent ownership.
- [ ] Record per-component source, permission/license evidence, copyright notices, and replacement decisions before choosing a project-wide license.
- [ ] Treat `app/src/main/assets/answer.txt` as a separate rights review: even if code is rewritten, inherited text/content may still require permission.

## Priority 1 — legacy VK media (functional blocker for clean Telegram experience)
- [ ] Review the **693** attachment-only legacy VK records identified in `ANSWER_DATABASE_VALIDATION_REPORT.md` (614 photo, 30 video, 28 audio, 21 clip).
- [ ] Replace with media the project has rights to host or with valid direct URLs/Telegram file IDs, preserving question-to-answer associations.
- [ ] Do not automatically delete entries or scrape/rehost VK content without checking rights.
- [x] Recognize legacy VK object-page URLs and send them as text links rather than attempting to download them as binary media (`LegacyVkMedia.kt`, `TelegramBot.kt`).
- [ ] Manually test valid direct media, inaccessible URLs, and mixed text+attachment replies against a test Telegram bot.

## Priority 2 — legacy naming and Android metadata (non-blocking, migration-sensitive)
- [ ] Inventory old internal identifiers before changing: `com.vkbot.manager` package paths, `VKBotApplication` class, manifest application name, persistence keys, and any exported Android components.
- [ ] Keep Android application ID unchanged for an update unless deliberately publishing a **new** app: changing it can break upgrade compatibility and stored user data.
- [ ] Rename internal symbols only after checking manifest, tests, reflection references, backups, and persisted preferences.
- [ ] Search resources, notifications, help text, screenshots, and UI labels for obsolete VK-facing product descriptions; preserve historical upstream attribution in legal docs.

## Priority 3 — verification and release gate
- [x] GitHub Actions answer database validation passes.
- [x] GitHub Actions Android JVM unit tests pass (run 39784556198).
- [ ] Android instrumented/device smoke tests: startup, bot token validation, message handling, notifications/background service, network reconnect, attachments, privacy/data export.
- [ ] Review release notes, README, privacy policy, third-party notices, dependencies, and links for accuracy.
- [ ] Only merge after functional checks **and** resolution of the upstream licensing/redistribution issue.

## Suggested order
1. Obtain upstream rights evidence; concurrently identify independent replacements for uncertain components.
2. Migrate the legacy media database safely, with regression tests.
3. Audit UI and naming without breaking app upgrades.
4. Perform device-level Telegram tests.
5. Prepare a release candidate; do not claim legal clearance solely from passing CI.
