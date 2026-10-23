# Third-party origin and attribution — Kiro Bot

> **Status: PROVENANCE REVIEW REQUIRED (2026-10-22).** This document is an attribution and review record, **not** proof of permission, a new license for upstream code, or a declaration that the entire project is independently authored.

## Historical origin

The author of Kiro Bot (KirDev / kirdev07) reports that the bot's original response engine ("мозг") was taken from the VK iHA bot project by **drfailov**, and later adapted and expanded for Kiro Bot.

Original project location supplied by the author:
- https://bitbucket.org/drfailov/vk-iha-bot-new
- Related author repository: https://github.com/drfailov/FP_iHA_bot

Historical VK post (16 February 2019), supplied as a screenshot by the Kiro Bot maintainer: the original developer announced publication of unfinished v5.0 source code and invited contributions. **This statement is not treated as a blanket redistribution/relicensing grant.** The original Bitbucket repository and its license terms have not yet been independently verified.

## Ownership and credit

- **Original VK iHA bot / FP iHA BOT code and materials:** rights remain with their respective original copyright holders, including drfailov and other contributors as applicable.
- **Kiro Bot changes and newly authored code:** rights belong to their respective contributors, including KirDev, only to the extent those contributors actually hold such rights.
- Kiro Bot is an independent, unofficial project; it is not an official release or endorsement by drfailov.
- Attribution is not a substitute for a valid license or permission to copy, modify, distribute, or sublicense the original code.

## Review scope: response engine

Inspect and compare, file by file, against the exact upstream version used:
- `app/src/main/java/com/vkbot/manager/botbrain/` — particularly `BotBrain.kt`, `AnswerDatabase.kt`, `AnswerElement.kt`, `TextAnalyzer.kt`, and related classes.
- `app/src/main/assets/answer.txt` and any bundled response databases, images, sounds, and media: establish origin, ownership, and permission to redistribute.
- Other packages and transitive dependencies; inspect history for earlier copies or renamed classes.

Record in a separate audit table for each component: Kiro Bot path, upstream path/commit, whether copied/adapted/reimplemented, copyright notices, exact license/permission evidence, and next action.

## Release gate

1. Obtain and preserve the original license and copyright notices **or** written permission from the relevant rights holders for the intended redistribution.
2. If permission cannot be established, do not claim the borrowed engine is licensed under the Kiro Bot EULA. Do not distribute a new release containing the disputed code until resolved; consider a clean-room replacement based on independently implemented functionality.
3. Review `LICENSE.md` and README: their current blanket restrictions must explicitly exclude upstream code and third-party dependencies, and must not conflict with their governing licenses.
4. Review the actual behavior of Telegram, AI providers, privacy/data handling, and dependency licenses separately.
5. Do not delete notices or rewrite history without approval.

## Suggested README notice (after verification)

> **Происхождение:** Kiro Bot развивался с использованием компонентов и идей проекта VK iHA bot / FP iHA BOT (drfailov и другие участники). Оригинальным авторам принадлежат права на их исходный код и материалы. KirDev отвечает за собственные изменения, интерфейс и новые функции. Проект не является официальной версией FP iHA BOT. Условия распространения унаследованных компонентов определяются их исходными лицензиями или разрешениями правообладателей.

**Important:** Do not publish the suggested text as the sole legal fix: determine actual licensing rights first.

## Public-source verification — 2026-10-23

### Verified from publicly accessible sources

- Official GitHub repository: https://github.com/drfailov/FP_iHA_bot — public, includes Android-app sources and historical materials. Public access alone does **not** grant a redistribution or derivative-work license.
- Original project's Google Play listing: https://play.google.com/store/apps/details?id=com.fsoft.ihabot — identifies FP iHA bot as a Telegram bot running on Android and links to the same public GitHub source. This supports attribution and project identity, **not** a license grant.
- GitHub's licensing guidance: https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository — absent a license, default copyright restrictions generally apply.

### Not verified / outstanding

- No specific license grant authorizing KirDev to redistribute or adapt the historical VK iHA bot engine has been established by this review. The historical Bitbucket source's license/permissions remain unverified.
- No written permission from the relevant upstream rights holders is on record.
- Exact copied/adapted code portions and their licenses have not been conclusively established at file/commit level.
- The response database's original authorship and redistribution rights remain unverified; see ANSWER_DATABASE_PROVENANCE_AUDIT.md.
- The fact that the upstream code is public, that Kiro Bot uses a different name/interface, or that AI tools assisted rewriting does **not** settle derivative-work rights.

### Required release decision

**Status: BLOCKED FOR NEW PUBLIC DISTRIBUTION of disputed components until rights are documented or the components are independently replaced.**

1. Ask the upstream author(s) for written permission covering modification, redistribution, source publication and APK distribution, including the relevant response data, or obtain the applicable original license text.
2. Record the scope of permission (which versions, files, media/database, attribution and conditions) in this document, without exposing private correspondence.
3. If permissions cannot be obtained, replace disputed implementations and data with independently authored materials, retaining evidence of separation and test coverage.
4. Review all third-party dependencies and notices before a release.

This review is a factual status record, not a legal opinion or claim that permission has already been granted.
