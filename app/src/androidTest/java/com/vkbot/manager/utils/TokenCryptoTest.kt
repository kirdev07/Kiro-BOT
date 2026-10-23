package com.vkbot.manager.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Шифрование работает только с настоящим Android Keystore — поэтому инструментальный тест. */
@RunWith(AndroidJUnit4::class)
class TokenCryptoTest {

    private val token = "123456789:TEST_telegram_token_абв"

    @Test
    fun encryptDecryptRoundTrip() {
        val encrypted = TokenCrypto.encrypt(token)
        assertTrue(TokenCrypto.isEncrypted(encrypted))
        assertFalse(encrypted.contains(token))
        assertEquals(token, TokenCrypto.decrypt(encrypted))
        // Случайный IV: два шифрования одного токена различаются
        assertNotEquals(encrypted, TokenCrypto.encrypt(token))
    }

    @Test
    fun plainTokenFromOldVersionIsReadAsIs() {
        assertEquals(token, TokenCrypto.decrypt(token))
    }

    @Test
    fun corruptedValueGivesEmptyToken() {
        assertEquals("", TokenCrypto.decrypt("enc:v1:AAAAbroken"))
    }

    @Test
    fun botsAreStoredEncryptedAndMigratedFromPlainText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "bots.json")
        val prefs = context.getSharedPreferences("vk_bot_settings", android.content.Context.MODE_PRIVATE)
        val backup = if (file.exists()) file.readText() else null
        val originalLegacyToken = prefs.getString("bot_1_token", null)
        val originalActiveIds = prefs.getString("active_bot_ids", null)
        val originalBotName = prefs.getString("bot_1_name", null)
        try {
            // Старый формат хранения токена (открытым текстом), но платформа уже Telegram
            file.writeText("""[{"id":1,"name":"Test","token":"$token","platform":"telegram"}]""")
            prefs.edit().putString("bot_1_token", token).commit()

            val loaded = BotDataManager.loadBots(context)
            assertEquals(token, loaded.single().token)

            val onDisk = file.readText()
            assertFalse("Токен остался открытым текстом", onDisk.contains(token))
            assertTrue(onDisk.contains("enc:v1:"))
            assertFalse(prefs.contains("bot_1_token"))

            assertEquals(token, BotDataManager.loadBots(context).single().token)
        } finally {
            if (backup != null) file.writeText(backup) else file.delete()
            prefs.edit().apply {
                if (originalLegacyToken == null) remove("bot_1_token") else putString("bot_1_token", originalLegacyToken)
                if (originalActiveIds == null) remove("active_bot_ids") else putString("active_bot_ids", originalActiveIds)
                if (originalBotName == null) remove("bot_1_name") else putString("bot_1_name", originalBotName)
            }.commit()
        }
    }
}
