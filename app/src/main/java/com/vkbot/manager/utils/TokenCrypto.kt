package com.vkbot.manager.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Шифрование токенов ключом из Android Keystore (AES-256-GCM).
 * Ключ не покидает устройство: после переноса данных на другой телефон токен придётся ввести заново.
 */
object TokenCrypto {
    private const val TAG = "TokenCrypto"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "kiro_bot_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc:v1:"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val MAX_CACHED = 32

    fun isEncrypted(value: String) = value.startsWith(PREFIX)

    fun encrypt(plain: String): String {
        if (plain.isEmpty() || isEncrypted(plain)) return plain
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    /**
     * Уже расшифрованные значения: список ботов обновляется на экране раз в секунду,
     * а Keystore — медленная операция. Ключ кэша — сама зашифрованная строка.
     */
    private val decrypted = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Незашифрованное значение (старые версии) возвращается как есть; при ошибке — пустая строка. */
    fun decrypt(value: String): String {
        if (!isEncrypted(value)) return value
        decrypted[value]?.let { return it }
        return decryptWithKeystore(value).also {
            if (it.isNotEmpty()) {
                if (decrypted.size >= MAX_CACHED) decrypted.clear()
                decrypted[value] = it
            }
        }
    }

    private fun decryptWithKeystore(value: String): String {
        return try {
            val data = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, data, 0, IV_SIZE))
            String(cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось расшифровать токен", e)
            ""
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
