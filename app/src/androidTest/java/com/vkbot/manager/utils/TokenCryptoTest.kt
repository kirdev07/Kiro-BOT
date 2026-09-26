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

    private val token = "vk1.a.TEST_token_1234567890_абв"

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
        try {
            // Файл и prefs в формате старой версии: токен открытым текстом
            file.writeText("""[{"id":1,"name":"Test","token":"$token"}]""")
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
        }
    }
}
