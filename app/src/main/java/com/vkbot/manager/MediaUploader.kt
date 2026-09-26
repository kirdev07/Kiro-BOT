package com.vkbot.manager

import android.util.Log
import com.vkbot.manager.botbrain.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Превращает вложения ответа в то, что понимает messages.send:
 * - объекты VK отправляются как есть;
 * - картинка по ссылке скачивается и загружается в VK (фото; GIF — документом, чтобы анимировалась);
 * - прочие ссылки (YouTube и т.п.) добавляются в текст — VK сам покажет превью.
 *
 * Загруженные картинки кэшируются, чтобы не загружать одну и ту же ссылку при каждой команде.
 *
 * @param api вызов метода VK API, возвращает тело ответа (JSON)
 */
class MediaUploader(
    private val api: suspend (method: String, params: Map<String, String>) -> String,
    private val onLog: (String) -> Unit = {},
    private val fetcher: ImageFetcher = ImageFetcher(convert = AndroidImageConverter)
) {
    /** Результат: строка для параметра attachment и ссылки, которые нужно дописать в текст. */
    data class Resolved(val vkAttachments: List<String>, val links: List<String>)

    private val uploadCache = ConcurrentHashMap<String, String>()
    /** Ссылки, которые оказались не картинками (страницы, YouTube) — больше их не проверяем. */
    private val notImages = ConcurrentHashMap.newKeySet<String>()

    suspend fun resolve(attachments: List<Attachment>, peerId: Int): Resolved {
        val vk = mutableListOf<String>()
        val links = mutableListOf<String>()
        for (attachment in attachments) {
            if (!attachment.isExternal) {
                vk.add(attachment.toVkString())
                continue
            }
            val uploaded = when {
                attachment.url in notImages -> null
                else -> uploadCache[attachment.url] ?: uploadIfImage(attachment.url, peerId)?.also {
                    uploadCache[attachment.url] = it
                }
            }
            if (uploaded != null) vk.add(uploaded) else links.add(attachment.url)
        }
        return Resolved(vk, links)
    }

    private suspend fun uploadIfImage(url: String, peerId: Int): String? = withContext(Dispatchers.IO) {
        try {
            val file = fetcher.fetch(url)
            if (file.isGif) uploadDoc(file, peerId) else uploadPhoto(file, peerId)
        } catch (e: ImageFetcher.NotAnImage) {
            notImages.add(url)
            onLog("Ссылка отправлена текстом ($url): ${e.message}")
            null
        } catch (e: Exception) {
            onLog("Не удалось загрузить картинку $url: ${e.message} — отправлена ссылкой")
            Log.e(TAG, "Upload failed: $url", e)
            null
        }
    }

    private suspend fun uploadPhoto(file: DownloadedMedia, peerId: Int): String {
        val server = apiResponse("photos.getMessagesUploadServer", mapOf("peer_id" to peerId.toString()))
        val uploaded = JSONObject(MediaFiles.postMultipart(server.getString("upload_url"), emptyMap(), "photo", file))
        val saved = apiResponseArray(
            "photos.saveMessagesPhoto",
            mapOf(
                "server" to uploaded.get("server").toString(),
                "photo" to uploaded.getString("photo"),
                "hash" to uploaded.getString("hash")
            )
        ).getJSONObject(0)
        return vkString("photo", saved)
    }

    private suspend fun uploadDoc(file: DownloadedMedia, peerId: Int): String {
        val server = apiResponse("docs.getMessagesUploadServer", mapOf("peer_id" to peerId.toString(), "type" to "doc"))
        val uploaded = JSONObject(MediaFiles.postMultipart(server.getString("upload_url"), emptyMap(), "file", file))
        val saved = apiResponse("docs.save", mapOf("file" to uploaded.getString("file"), "title" to "animation.gif"))
        return vkString("doc", saved.getJSONObject("doc"))
    }

    private fun vkString(type: String, obj: JSONObject): String {
        val base = "$type${obj.get("owner_id")}_${obj.get("id")}"
        val key = obj.optString("access_key")
        return if (key.isEmpty()) base else "${base}_$key"
    }

    private suspend fun apiResponse(method: String, params: Map<String, String>): JSONObject =
        checkError(method, api(method, params)).getJSONObject("response")

    private suspend fun apiResponseArray(method: String, params: Map<String, String>) =
        checkError(method, api(method, params)).getJSONArray("response")

    private fun checkError(method: String, body: String): JSONObject {
        val json = JSONObject(body)
        json.optJSONObject("error")?.let { throw IllegalStateException("$method: ${it.optString("error_msg")}") }
        return json
    }


    companion object {
        private const val TAG = "MediaUploader"
    }
}
