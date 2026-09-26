package com.vkbot.manager

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Скачанный медиафайл. [contentType] — настоящий тип (по содержимому, если сайт прислал неточный). */
class DownloadedMedia(val bytes: ByteArray, val contentType: String) {
    val isGif get() = contentType == "image/gif"
    val isImage get() = contentType.startsWith("image/")

    val fileName: String
        get() {
            val ext = contentType.substringAfter("/").substringBefore("+").substringBefore(";")
                .replace("jpeg", "jpg").ifEmpty { "bin" }
            return "file.$ext"
        }
}

/** Ответ сервера: тело (до лимита), тип из заголовка и итоговый адрес после перенаправлений. */
class HttpResult(val bytes: ByteArray, val headerType: String, val finalUrl: String) {
    val isHtml get() = headerType.contains("html") || MediaFiles.looksLikeHtml(bytes)
}

/** Скачивание по ссылке и отправка файлом (multipart) — общее для VK и Telegram. */
object MediaFiles {
    private const val TIMEOUT_MS = 20_000
    const val MAX_SIZE_BYTES = 20L * 1024 * 1024
    private const val MAX_REDIRECTS = 5

    /** Заголовки обычного браузера: часть сайтов не отдаёт картинки «ботам» и запросам без Referer. */
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /**
     * GET с ручными перенаправлениями (HttpURLConnection сам не переходит http ↔ https).
     * @throws IOException с понятной причиной (HTTP 403, слишком большой файл и т.п.)
     */
    fun get(url: String, maxBytes: Long = MAX_SIZE_BYTES): HttpResult = try {
        getOrThrow(url, maxBytes)
    } catch (e: java.net.UnknownHostException) {
        throw IOException("сайт недоступен (${URL(url).host})")
    } catch (e: java.net.SocketTimeoutException) {
        throw IOException("сайт не ответил вовремя")
    } catch (e: java.net.ConnectException) {
        throw IOException("не удалось подключиться к сайту")
    }

    private fun getOrThrow(url: String, maxBytes: Long): HttpResult {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", BROWSER_UA)
                connection.setRequestProperty("Accept", "image/avif,image/webp,image/*,text/html;q=0.9,*/*;q=0.8")
                connection.setRequestProperty("Referer", originOf(current) + "/")
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: throw IOException("перенаправление без адреса")
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code !in 200..299) throw IOException("сайт ответил HTTP $code")
                if (connection.contentLengthLong > maxBytes) throw IOException("файл больше ${maxBytes / 1024 / 1024} МБ")

                val out = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        if (out.size() > maxBytes) throw IOException("файл больше ${maxBytes / 1024 / 1024} МБ")
                    }
                }
                val type = connection.contentType?.substringBefore(";")?.trim()?.lowercase().orEmpty()
                return HttpResult(out.toByteArray(), type, current)
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("слишком много перенаправлений")
    }

    /** Тип картинки по первым байтам файла или null, если это не картинка. */
    fun sniffImageType(bytes: ByteArray): String? {
        fun at(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
        fun ascii(from: Int, text: String) = text.indices.all { at(from + it) == text[it].code }
        return when {
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg"
            at(0) == 0x89 && ascii(1, "PNG") -> "image/png"
            ascii(0, "GIF8") -> "image/gif"
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp"
            ascii(0, "BM") && bytes.size > 26 -> "image/bmp"
            ascii(4, "ftyp") && (ascii(8, "avif") || ascii(8, "avis")) -> "image/avif"
            ascii(4, "ftyp") && (ascii(8, "heic") || ascii(8, "heix") || ascii(8, "mif1")) -> "image/heic"
            else -> null
        }
    }

    fun looksLikeHtml(bytes: ByteArray): Boolean {
        val head = String(bytes, 0, minOf(bytes.size, 512), Charsets.ISO_8859_1).trimStart().lowercase()
        return head.startsWith("<!doctype html") || head.startsWith("<html") || head.contains("<head")
    }

    private fun originOf(url: String): String {
        val u = URL(url)
        return "${u.protocol}://${u.host}" + if (u.port != -1) ":${u.port}" else ""
    }

    /** POST multipart/form-data: текстовые поля + один файл. Возвращает тело ответа. */
    fun postMultipart(uploadUrl: String, fields: Map<String, String>, fileField: String, file: DownloadedMedia): String {
        val boundary = "----KiroBot${System.nanoTime()}"
        val connection = URL(uploadUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.outputStream.use { out ->
                for ((name, value) in fields) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    out.write(value.toByteArray(Charsets.UTF_8))
                    out.write("\r\n".toByteArray())
                }
                out.write(("--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"$fileField\"; filename=\"${file.fileName}\"\r\n" +
                    "Content-Type: ${file.contentType}\r\n\r\n").toByteArray())
                out.write(file.bytes)
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val stream = if (connection.responseCode >= 400) connection.errorStream else connection.inputStream
            return stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            connection.disconnect()
        }
    }
}
