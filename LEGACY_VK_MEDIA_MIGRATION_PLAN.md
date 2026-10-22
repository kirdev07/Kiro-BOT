# Legacy VK media migration plan — 2026-10-22

## Verified facts

- The audit-branch `answer.txt` contains **693** attachment-only legacy VK records: **614 photo**, **30 video**, **28 audio**, **21 clip**.
- Example: `Мем||photo-188633896_457264520|2`.
- Current `Attachment.parse` recognizes `photo-..._...` and other legacy IDs and converts them to `https://vk.com/<legacy-code>`. It does not resolve the actual media download URL or prove that the linked object is publicly accessible.
- The `AndroidFileManager.kt` parser supports empty answer text with attachments in the third field.
- The answer database validator GitHub Action run [39782519633](https://github.com/kirdev07/Kiro-BOT/actions/runs/39782519633) completed **successfully**. This verifies static format checks only, not network/media delivery.

## Risks

1. VK pages may require authentication, have expired/deleted media, or block automated fetches.
2. Telegram photo upload requires usable media bytes or a suitable directly accessible URL; a VK page URL is not necessarily one.
3. Audio/video/clip objects may need different delivery methods and codec/size handling.
4. A bulk migration without verifying permissions could republish media owned by third parties.

## Safe migration procedure

1. Export an inventory (record line, question, legacy type, ID, replacement URL, permission status, verification result) without exposing user tokens.
2. Manually test representative photo/video/audio/clip records in a controlled Telegram bot chat. Record whether each link resolves, downloads, and displays correctly.
3. For media you own or have permission to redistribute, replace VK IDs with stable HTTPS URLs serving the actual content; preserve existing `question|answer|attachment|limit` fields.
4. For unavailable or unlicensed items, **flag for review** rather than silently deleting or mirroring them.
5. Add tests for `Attachment.parse` and Telegram sending, including URL failures, oversized media, and fallback to a text link.
6. Rerun static validation and Android tests, then review changes before merging to main.

**No live Telegram delivery test, authenticated VK access, or media migration has been performed. No credentials were requested or used.**
