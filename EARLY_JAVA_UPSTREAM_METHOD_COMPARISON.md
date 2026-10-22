# Early Java vs FP iHA archive: method-level comparison

Date: 2026-10-22. Sources: user-supplied `drfailov-vk-iha-bot-new-7d0c8648a557(1).zip` and Kiro-BOT commit `32c02e71eec264b6181a5ba3a325fee1ce3486cc`. **Scope: selected source inspection, not a complete token-by-token similarity scan or license clearance.**

## Concrete findings

| Subsystem | FP iHA original | Kiro Bot earliest Android commit (Jan 27, 2026) | Evidence and caveat |
|---|---|---|---|
| Bot coordinator | `AnswerInfrastructure/BotBrain.java` (448 lines), `processMessage`, command marks, treatment, VK-specific modules | `botbrain/BotBrain.java`, `processMessage`, `findAnswerSmart`, LRU cache for 1000 users, indexed search, response placeholders | Both are bot-brain coordinators; early Kiro implementation visibly includes different LRU and candidate-ranking architecture. This does **not** rule out reuse of logic in other methods. |
| Answer DB | `AnswerInfrastructure/AnswerDatabase/AnswerDatabase.java` (2631 lines), `processMessage`, `findAnswers`, `importAnswers`, command handling and JaroWinkler/MessagePreparer dependencies | `botbrain/AnswerDatabase.java` present in first Android commit; January BotBrain calls `searchAnswers` | Database subsystems exist in both; need a complete diff of implementation details and historical intermediary sources. |
| Answer record | `AnswerInfrastructure/AnswerDatabase/AnswerElement.java` (422 lines), `toJson`, `fromJson`, author/date metadata and attachment checks | `botbrain/AnswerElement.java` present in first Android commit | Class identity and purpose overlap; copying of implementation not established by filename alone. |
| Similarity | `MessageComparison/MessagePreparer.java` (185 lines), phonetic substitutions and repeated-symbol removal; upstream DB imports `JaroWinkler` | `botbrain/MessageSimilarity.java` implements Levenshtein distance and Jaccard-like keyword membership | These are **different named algorithms** in inspected files. Both are standard text matching techniques; this is not evidence of copyright ownership or independent authorship. |
| Response representation | `AnswerInfrastructure/Answer.java` and `Message.java` | `botbrain/BotResponse.java`, `BotMessage.java` | Both represent messages and responses, with different platform-specific fields; manual comparison still needed. |
| Assets | Original archive includes upstream databases/resources | January Kiro source commit includes `app/src/main/assets/answers.bin` | Asset origin **unresolved**; check whether content was copied and whether it is shipped today. |

## Verified history

- `32c02e71eec264b6181a5ba3a325fee1ce3486cc`: first full Android project source contains Java `botbrain` implementation.
- `c3e65b92ce608c8545eab02a735089d70fdece7a`: migration of Java brain classes to Kotlin.
- `aa813a49781ee54d0ffebe0e84e4264f9549af48`: new Telegram-only conversation pipeline and major redesign.

## Legal/release status

The maintainer states that FP iHA code was used as the starting point. Neither changes to architecture nor Java→Kotlin conversion establish permission to redistribute inherited expression. No license grant was identified in the provided original archive. **Status: not cleared for a new release on the basis of this audit.** Credit the original authors, but attribution is not a substitute for permission.

## Next exact checks

1. Full local clone of `Kiro-BOT` with all historical snapshots and a full source comparison against all 158 upstream Java files.
2. Verify `answers.bin` and current `answer.txt` provenance.
3. Inspect original Bitbucket license and archived commit history, or obtain permission from Dr. Failov/rightsholders.
4. Only after evidence, prepare corrected scoped license and notices.

This report does not provide a fabricated code-similarity percentage.
