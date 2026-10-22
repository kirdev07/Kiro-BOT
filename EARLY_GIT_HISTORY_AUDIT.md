# Kiro-BOT Git history: early source provenance (2026-10-22)

Status: **verified repository-history observations, not a full copyright similarity opinion**.

## Verified commit chronology

- 2026-01-27: earliest visible repository commits; `55962d0bf18225b0342d2697b24455002ee2878e` contains only a README, whereas `32c02e71eec264b6181a5ba3a325fee1ce3486cc` adds an Android project with Java engine files.
- The January Android commit contains `app/src/main/java/com/vkbot/manager/botbrain/BotBrain.java`, `AnswerDatabase.java`, `AnswerElement.java`, `AndroidFileManager.java`, `Attachment.java`, `BotMessage.java`, `BotResponse.java`, `MessageSimilarity.java`, plus bundled `app/src/main/assets/answers.bin`.
- Early `BotBrain.java` is titled *Оптимизированный "мозг" бота v2.0*, describes LRU dialogue cache (up to 1000), DB indexing, and `findAnswerSmart` with `searchAnswers`. These are direct observations from the January commit, **not** proof of who originally authored each method.
- 2026-06-20: `c3e65b92ce608c8545eab02a735089d70fdece7a` explicitly titled *Kiro Bot 2.10 (Final) - 100% Kotlin Migration, txt Database, & UI Polish*. Commit files include removal of Java engine files and addition of corresponding Kotlin files, and `answer.txt` replacing earlier storage.
- 2026-09-26: `e1247dafd54a631a18614aede096397d4f80ef61` — Kiro Bot 2.20 beta, Telegram added alongside VK.
- 2026-10-20: `aa813a49781ee54d0ffebe0e84e4264f9549af48` — Kiro Bot 3.0, Telegram-only, new `conversation/` modules and updated brain, answer DB, assets.

## Provenance implications

- The engine existed as **Java inside the earliest Android source commit**, so Kotlin migration did not start from an empty implementation. This supports the maintainer's account of gradual modernization.
- The earliest Java source includes specific indexing/LRU logic and needs **side-by-side examination** against the original FP iHA Java archive to distinguish inherited expression, modifications, and independent additions.
- GitHub history **does not show** development prior to 2026-01-27 or establish that Dr. Failov licensed the source for redistribution. Do not equate public code availability with license permission.
- `answers.bin` is an additional **asset provenance concern**: identify whether it was derived from upstream database and whether it survives in releases/history. Removing it from HEAD does not remove old Git history.

## Remaining steps

1. Fetch the complete earliest Java engine and compare against all corresponding classes in the supplied Dr. Failov archive (manual control flow, comments, unique strings, token similarity).
2. Check `answers.bin` origin and rights, and any present `answer.txt` content.
3. Trace `AnswerDatabase.java` → Kotlin commit → current `AnswerDatabase.kt` for retained portions.
4. Obtain source license or permission from upstream rights holders before concluding redistribution is authorized.
5. Propose narrowly scoped notices and license changes, without removing copyright or making unsupported ownership claims.

## Direct links
- [First Android source commit](https://github.com/kirdev07/Kiro-BOT/commit/32c02e71eec264b6181a5ba3a325fee1ce3486cc)
- [Java-to-Kotlin migration](https://github.com/kirdev07/Kiro-BOT/commit/c3e65b92ce608c8545eab02a735089d70fdece7a)
- [Telegram-only 3.0](https://github.com/kirdev07/Kiro-BOT/commit/aa813a49781ee54d0ffebe0e84e4264f9549af48)
