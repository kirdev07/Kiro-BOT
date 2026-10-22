# Kiro Bot ↔ FP iHA BOT: preliminary component provenance comparison

**Date:** 2026-10-22  
**Original supplied by maintainer:** `drfailov-vk-iha-bot-new-7d0c8648a557(1).zip` (348 archive entries).  
**Current target:** `kirdev07/Kiro-BOT`, `main`.  
**Status:** PARTIAL CODE COMPARISON — NOT A LEGAL CLEARANCE OR COMPLETE CODE SIMILARITY SCAN.

## Key facts

- The supplied upstream archive contains **no top-level LICENSE/COPYING file identified in its 348 entries**. Absence of a license file does **not** imply public domain or permission to redistribute.
- Upstream `BotBrain.java` explicitly credits **Dr. Failov (05.08.2014)**; `AnswerDatabase.java` credits **Dr. Failov (06.08.2014)**; `AnswerElement.java` credits **Dr. Failov (21.04.2017)**.
- Maintainer confirms that Kiro Bot initially used Java classes from FP iHA BOT, later transformed with Kiro IDE, Google Antigravity, Codex and Claude Code. The current project must be treated as potentially derivative until provenance and permissions are resolved.
- Current `LICENSE.md` claims blanket restrictions over modification and redistribution; these cannot override upstream rights. Its privacy text also needs factual verification.
- This comparison is based on examining upstream Java file structures and selected current Kotlin source; **it does not prove independent rewriting, percentage similarity, or copyright status**.

## Component mapping

| Upstream (FP iHA BOT) | Current Kiro-BOT | Observations | Preliminary provenance | Next action |
|---|---|---|---|---|
| `AnswerInfrastructure/BotBrain.java` (448 lines) | `botbrain/BotBrain.kt` | Both coordinate incoming messages and answer selection; Kotlin uses a separate `ConversationPipeline`, dialogue history, AI fallback and newer processors. | **Confirmed historical ancestry by maintainer; exact retained expression not established** | Method-by-method comparison of original logic, control flow, comments and constants |
| `AnswerInfrastructure/AnswerDatabase/AnswerDatabase.java` (2631 lines) | `botbrain/AnswerDatabase.kt` | Both store/search answers; upstream describes JSON-lines and extensive metadata, current Kotlin uses exact/keyword indexes, IDF weighting, regex tiers. | **Likely derived subsystem, extent unknown** | Compare search algorithms and format parsers, detect transplanted fragments |
| `AnswerInfrastructure/AnswerDatabase/AnswerElement.java` (422 lines) | `botbrain/AnswerElement.kt` | Same conceptual entity; original has many author/date/gender fields, current Kotlin data class is smaller and includes context and usage. | **Historical ancestry possible, code similarity not established** | Compare method implementations, serialization, copyright-bearing text |
| `AnswerInfrastructure/AnswerDatabase/Attachment.java` | `botbrain/Attachment.kt` | Both represent attachments; current stores URLs and translates legacy VK identifiers. | **Possible compatibility/derivation** | Compare conversion and parsing logic |
| `AnswerInfrastructure/Message.java` | `botbrain/BotMessage.kt` | Both model incoming messages; current is a small Kotlin data class with Telegram chat ID. | **Conceptual match; copying not proven** | Compare fields and methods |
| `AnswerInfrastructure/Answer.java` | `botbrain/BotResponse.kt` | Both model generated responses; current Kotlin adds a builder. | **Conceptual match; copying not proven** | Compare builder, attachment handling and response formatting |
| `AnswerInfrastructure/AnswerDatabase/AnswerMicroElement.java` and search routines | `botbrain/SearchResult.kt` | Both support answer selection; data representations differ. | **Uncertain** | Compare match scoring, regex capture, ranking |
| `Modules/FileManager.java` and upstream DB I/O | `botbrain/AndroidFileManager.kt` | Current uses `answer.txt` pipe-separated format, app-local/legacy folders, backups, usage counters; upstream DB description is JSON-lines. | **Significant design differences visible; independence unproven** | Compare file format compatibility and any copied helpers |
| `MessageComparison/MessagePreparer.java`, `SynonimousProvider.java`, `JaroWinkler.java` | `botbrain/TextAnalyzer.kt`, `AnswerDatabase.kt` | Current has simplified stemming, synonyms, tokenization and IDF; upstream has separate comparison modules. | **Functional overlap, textual reuse unknown** | Compare normalization rules, synonym parsing, similarity methods |
| `Functions/FunctionProcessor.java`, `Functions/Modules/*.java` | `botbrain/conversation/functions/*.kt` | Both implement bot functions (time, random, choice, math, etc.). | **Function-by-function review needed** | Compare code, literal strings, templates, edge-case handling |
| `UnknownMessagesDatabase.java` | `botbrain/UnansweredLog.kt` | Both track questions without answers. | **Conceptual overlap** | Compare storage formats, pruning, data handling |
| No corresponding system identified in selected upstream files | `botbrain/conversation/ConversationPipeline.kt`, `DialogHistory.kt`, `Mood.kt` and AI integrations | These are prominent modern structures in current code. | **Appears substantially newer, authorship not conclusively established** | Inspect Git history and all source before assigning copyright |

Paths for original are under `app/src/main/java/com/fsoft/vktest/`. Current files are under `app/src/main/java/com/vkbot/manager/`.

## Priority follow-up

1. **P0 — Permission:** obtain upstream license or written permission covering copying, modifications and distribution of derivative versions, including the desired commercial/noncommercial terms. Archive any primary evidence.
2. **P0 — Release gate:** until rights are established, do not claim the upstream-derived engine is exclusively KirDev's; hold any new public distribution containing uncertain code.
3. **P1 — Full diff:** download current repository, perform systematic token-normalized similarity comparisons (Java/Kotlin cross-language) plus manual logic comparison; inspect history and earlier Java revisions for clearer evidence.
4. **P1 — Asset provenance:** audit bundled `app/src/main/assets/answer.txt`, response texts, media and other original resources separately from code.
5. **P1 — Documentation:** after permission is verified, update `README.md`, `LICENSE.md`, `THIRD_PARTY_NOTICES.md` and source file notices in compliance with actual upstream conditions. Do not assign a speculative license to Dr. Failov.
6. **P2 — Other dependencies:** check Gradle dependencies, Telegram/AI service terms and the accuracy of privacy statements.

## Proposed attribution (not a license)

> Kiro Bot is an independent KirDev Studio project developed from components of the historical VK iHA bot / FP iHA BOT project by Dr. Failov and contributors. Original authors retain rights to their contributions. KirDev's new features and modifications are separately attributed to their actual contributors. This is an unofficial project. Reuse of original components is governed by the original license or permission from their rights holders.

**Important:** Attribution alone does not grant permission to distribute derivative code.

## Evidence limits

This document deliberately does not give similarity percentages, does not assert that every current file is original, and does not certify compliance. Legal interpretation should be reviewed by a qualified professional if redistribution rights remain uncertain.
