# Kiro Bot 3.0 — independent-engine migration plan

**Status:** audit in progress. **Branch:** `audit/independent-engine`. Do not publish a new APK/source release containing unlicensed material.

## Goal

Retain the user-facing Telegram functionality, current UI, settings, storage compatibility, and bot identity while replacing any source implementation or bundled content that lacks proven modification/redistribution rights. Rewriting code in Kotlin, changing names or using AI is not sufficient proof of independent authorship.

## Current source inventory (main, 2026-10-23)

| File | Current role | Audit priority | Finding / evidence needed |
| --- | --- | --- | --- |
| `botbrain/BotBrain.kt` (291 lines) | Conversation orchestration and fallback | HIGH | Historical lineage acknowledged; exact overlap with upstream `BotBrain.java` unresolved |
| `botbrain/AnswerDatabase.kt` (400 lines) | Search indexes, exact/regex/semantic matching | HIGH | Historical lineage acknowledged; compare against upstream `AnswerDatabase.java` |
| `botbrain/AnswerElement.kt` (46 lines) | Serializable response record | HIGH | Compare with upstream `AnswerElement.java`; preserve migration compatibility |
| `botbrain/TextAnalyzer.kt` (55 lines) | Text normalization, stemming, synonyms | MEDIUM | Independently check origin and implementation |
| `botbrain/conversation/` | Conversation pipeline and processors | HIGH | Inspect file-level history, copied expressions and third-party notices |
| `assets/answer.txt` | Bundled question/answer corpus | CRITICAL | 14,995 nonempty entries reported in prior audit; source rights unverified |
| `assets/answers.bin` (historical) | Old binary answer corpus | CRITICAL | Trace binary-to-text migration and ownership |
| Images, sounds, preset packs, libraries | App resources and dependencies | MEDIUM | Inventory license, notices and permission |

**This is a triage table, not a determination that each file infringes copyright.** The current four Kotlin files were inspected for structure, not subjected to a conclusive line-by-line upstream comparison.

## Non-destructive implementation strategy

1. **Freeze compatibility.** Keep `applicationId=com.vkbot.manager`, current bot token encryption, storage schema, Android permissions and settings behavior unless a separate migration is tested. Never delete existing user data to simplify refactoring.
2. **Inventory exact origins.** Map every inherited component to upstream file, commit, author, license notice and provenance evidence. Review original archive and repository history.
3. **Define independent behavior specifications.** Specify inputs/outputs, error handling, performance, message rules and data formats without copying expressive upstream source.
4. **Implement replacement behind interfaces.** Use new classes/packages and test against independently written specifications. Preserve old implementation only on the audit branch for comparison; do not ship unverified borrowed code.
5. **Replace uncertain bundled content.** Curate a newly authored response corpus with recorded authorship or load user-owned databases; don't paraphrase thousands of uncertain responses merely to disguise their source.
6. **Verify migration.** Test pre-existing user settings, database, backup/restore, token encryption, update installation, and conversation behavior on Android.
7. **Release gate.** Confirm rights for every shipped component and resource, record third-party notices, run CI + instrumentation + signed release build, and seek explicit maintainer approval before merge/release.

## First technical acceptance tests

- Exact, regex and fallback matching return stable answers for new test fixtures.
- User-created answers and context rules survive app updates.
- No unexpected reply in private/group chats; rate limiting and opt-in AI behavior remain correct.
- Database imports and exports preserve the documented format and handle invalid records.
- Android Keystore failures do not persist plaintext tokens or erase previously saved bot data.
- No network access is introduced for local-only answer selection.

## Evidence log / decision

- [x] Read current `BotBrain.kt`, `AnswerDatabase.kt`, `AnswerElement.kt`, `TextAnalyzer.kt` on `main`.
- [x] Confirm historical upstream origin remains documented.
- [ ] Retrieve and inspect exact upstream source files and copyright/license notices.
- [ ] Complete line-level provenance classification.
- [ ] Establish rights to the bundled response corpus or replace it.
- [ ] Implement and test independently authored replacement components where needed.
- [ ] Obtain release sign-off.

**Do not remove original attribution or claim that rights have been cleared.** Historical Git commits remain part of the repository and need separate review before distributing source snapshots.
