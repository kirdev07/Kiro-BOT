package com.vkbot.manager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Перекодирует в JPEG картинки, которые VK/Telegram могут не принять как фото (WebP, AVIF, HEIC, BMP).
 * Декодирует сам Android (AVIF — с Android 12). JPEG, PNG и GIF отправляются без изменений.
 */
object AndroidImageConverter : (DownloadedMedia) -> DownloadedMedia {
    private val SUPPORTED = setOf("image/jpeg", "image/png", "image/gif")
    private const val MAX_SIDE = 4096
    private const val JPEG_QUALITY = 90

    override fun invoke(media: DownloadedMedia): DownloadedMedia {
        if (media.contentType in SUPPORTED) return media

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(media.bytes, 0, media.bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ImageFetcher.NotAnImage("формат ${media.contentType} телефон не умеет открывать")
        }
        // Огромные картинки уменьшаем, чтобы не упереться в память
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2

        val bitmap = BitmapFactory.decodeByteArray(media.bytes, 0, media.bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw ImageFetcher.NotAnImage("не удалось открыть ${media.contentType}")
        try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            return DownloadedMedia(out.toByteArray(), "image/jpeg")
        } finally {
            bitmap.recycle()
        }
    }
}
