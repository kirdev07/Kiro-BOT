# FP iHA BOT → Kiro Bot: source comparison findings (phase 2)

Date: 2026-10-22. Status: **partial evidence, not a full cross-language similarity audit or legal clearance**.

## Sources
- Maintainer-supplied archive: `drfailov-vk-iha-bot-new-7d0c8648a557(1).zip`, 348 entries, 158 Java source files.
- Current Kiro Bot: `kirdev07/Kiro-BOT` main, selected `botbrain/*.kt` files inspected via GitHub.
- Public upstream reference: https://github.com/drfailov/FP_iHA_bot (a related project, **not proof of a license for the supplied archive**).

## Verified observations

1. The supplied archive contains **no filename matching LICENSE, COPYING, or NOTICE**. No blanket reuse permission was established from the inspected files.
2. Original `AnswerInfrastructure/BotBrain.java` has **448 lines**, header `Created by Dr. Failov on 05.08.2014`; current `botbrain/BotBrain.kt` coordinates a new-looking `ConversationPipeline`, history, mood and AI fallback. Historical reuse is confirmed by maintainer, but retained expressive code has not been quantified.
3. Original `AnswerInfrastructure/AnswerDatabase/AnswerDatabase.java` has **2,631 lines** and imports `JaroWinkler`, `MessagePreparer`; current `botbrain/AnswerDatabase.kt` uses exact/keyword indexes, IDF, regex tiers, and Kotlin data structures. Distinct techniques are visible; this does **not** establish clean-room authorship.
4. Original `AnswerInfrastructure/AnswerDatabase/AnswerElement.java` has **422 lines** and JSON-style metadata including authors and dates; current `botbrain/AnswerElement.kt` is a much smaller Kotlin data class with response text, attachments, repetition/context and usage fields.
5. Original `AnswerInfrastructure/MessageComparison/MessagePreparer.java` has **185 lines**, describes pair-of-letter comparison; current `botbrain/TextAnalyzer.kt` uses simplified Russian stemming, synonyms and tokenization. A full algorithmic comparison is still required.
6. **Concrete search markers:** among the 158 original Java files, exact strings `answer.txt`, `<pipe>`, `getRandomAnswer`, `searchAnswers` do not appear. The current code uses `answer.txt`, `<pipe>`, `getRandomAnswer`, `searchAnswers`. This is evidence of changed naming/file format, **not proof of non-copying**.
7. The original Java files contain 81 instances of a `Created by Dr. Failov` marker across distinct source files (as observed in the archive). Copyright notices must be preserved where required; do not imply KirDev wrote those original portions.
8. The current `botbrain/AndroidFileManager.kt` reads/writes pipe-separated `answer.txt`; original Java code in the supplied archive does not refer to `answer.txt` by that exact string. Current storage includes legacy Android shared-storage handling, which deserves a separate privacy/security review.
9. The current `botbrain/Attachment.kt` explicitly supports legacy VK object IDs; it is a compatibility signal, not a license grant.
10. `README.md` and `LICENSE.md` currently claim blanket prohibitions on modifications. Such language must be scoped to code the maintainer actually has rights to license, and must not supersede any upstream terms.

## Important limitations

- **No full current Git history retrieved or examined**; cannot attribute which specific changes came from Kiro IDE, Antigravity, Codex or Claude.
- No automated Java→Kotlin AST, token-normalized or control-flow similarity analysis of the complete two codebases was completed.
- Only selected current files were read; uninspected files and resources may contain copied code or copyrighted assets.
- No legal advice: absence of a license does not mean public domain; permission to view source does not itself grant redistribution rights.

## Next actions for Claude Code (do not edit app code yet)

- [ ] Fetch full current Git history and earliest Java/Kotlin snapshots, record earliest appearance of each derived component.
- [ ] Compare full original 158 Java files with all current source using cross-language normalized tokens and manual control-flow analysis; save evidence with exact file/line pairs.
- [ ] Audit `app/src/main/assets/answer.txt` and other assets for copied response databases.
- [ ] Identify any direct original comments, strings, unique algorithms and constants still present.
- [ ] Obtain explicit upstream license or written authorization for intended derivative redistribution. If unavailable, recommend a properly documented independent rewrite.
- [ ] Once permissions are verified, propose scoped `LICENSE.md`, `THIRD_PARTY_NOTICES.md` and README changes for owner approval.
- [ ] Do not erase history, delete author credits, change licenses, or publish releases automatically.

## Release decision

**NOT CLEARED**: historical upstream code ancestry is acknowledged, and the supplied original has no identified reuse license. A new public release of uncertain derived portions should wait for licensing resolution.
