# Kiro Bot answer database provenance and migration audit

Date: 2026-10-22. Scope: current `app/src/main/assets/answer.txt`, original Dr. Failov archive contents, and repository history.

## Confirmed current database observations

- GitHub `main` contains a UTF-8 text file `app/src/main/assets/answer.txt`.
- It has **14,995 nonempty lines** and approximately **1,239,323 characters** in the fetched representation.
- The current file uses `question|answer|...` records, including Kiro Bot-specific placeholders such as `{name}` and `{time}`.
- Several entries are still VK-themed despite the current Telegram-only app. Examples include phrases claiming the bot answers "во ВКонтакте" and lives "на сервере ВКонтакте". These are content inconsistencies, not proof of copied upstream material.
- The current database has no exactly duplicated full lines in this fetched representation; this does not rule out semantically duplicated questions/answers or copied content.

## Historical database observations

- The earliest full Android commit (`32c02e71eec264b6181a5ba3a325fee1ce3486cc`) includes binary `app/src/main/assets/answers.bin`.
- The June 20 Kotlin migration (`c3e65b92ce608c8545eab02a735089d70fdece7a`) introduces `answer.txt`.
- The supplied Dr. Failov archive includes resource files such as `negative_answers`, `positive_answers`, `pattern_answers`, `cities_database`, and `symbols_database`. It does **not** contain a file named `answers.bin` or `answer.txt` in its visible archive paths.
- **No content-level comparison of the historical binary database with upstream response resources was completed.** Its origin is unknown.

## Recommended cleanup, pending approval

1. Replace obsolete VK-specific *bot identity* phrases in `answer.txt` with Telegram-neutral or Kiro Bot-neutral text. Do not globally replace all VK mentions: factual answers about VK may remain valid.
2. Preserve existing question/answer delimiter format and placeholders, create a backup, and run parser/tests after edits.
3. Before claiming the database is entirely original, compare upstream text resources and any earlier `answers.bin` contents with current `answer.txt`; examine original encoding and binary format safely.
4. Keep source provenance notes and original copyright attribution separate from end-user dialogue content.
5. Do not rewrite or delete `LICENSE.md` until permission to redistribute upstream-derived components is established.

## Legal status

The present data audit **does not** establish copyright ownership or permission for the engine or response data. Upstream archive had no identified LICENSE/COPYING file. Public availability and attribution alone do not constitute a redistribution license.
