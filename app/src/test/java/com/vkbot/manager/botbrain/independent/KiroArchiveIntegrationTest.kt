package com.vkbot.manager.botbrain.independent

import org.junit.Assert.*
import org.junit.Test

class KiroArchiveIntegrationTest {
    @Test fun archiveImportRoundTripAndMedia() {
        val source = "фото|Вот фото|https://example.com/photo.jpg\nпривет *|Привет!"
        val restored = KiroAnswerArchive.parse(
            KiroAnswerArchive.export(KiroAnswerArchive.importLegacy(source))
        )
        val engine = KiroArchiveSwitch(restored)
        val media = engine.replyRich(100L, "фото")
        assertEquals("Вот фото", media?.text)
        assertEquals(listOf("https://example.com/photo.jpg"), media?.attachments)
        assertEquals("Привет!", engine.reply(100L, "привет мир"))
    }

    @Test fun sessionsKeepSeparateContextsWithSharedIndex() {
        val rows = KiroAnswerArchive.importLegacy(
            "начало|готово|||||режим\n" +
                "проверка|в режиме||||режим|\n" +
                "проверка|обычный"
        )
        val engine = KiroArchiveSwitch(rows)
        assertEquals("готово", engine.reply(1L, "начало"))
        assertEquals("в режиме", engine.reply(1L, "проверка"))
        assertEquals("обычный", engine.reply(2L, "проверка"))
        engine.forget(1L)
        assertEquals("обычный", engine.reply(1L, "проверка"))
    }

    @Test fun unsafeMediaRowIsNotSelected() {
        val rows = KiroAnswerArchive.importLegacy(
            "файл|опасно|file:///etc/passwd\nфайл|безопасно"
        )
        assertEquals("безопасно", KiroArchiveSwitch(rows).reply(1L, "файл"))
    }
}
