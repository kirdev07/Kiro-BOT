package com.vkbot.manager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Перекодирование работает только на настоящем Android (BitmapFactory). */
@RunWith(AndroidJUnit4::class)
class AndroidImageConverterTest {

    private fun encode(format: Bitmap.CompressFormat, width: Int = 64, height: Int = 48): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return ByteArrayOutputStream().also { bitmap.compress(format, 90, it) }.toByteArray()
    }

    @Test
    fun webpBecomesJpegWithSameSize() {
        @Suppress("DEPRECATION")
        val webp = encode(Bitmap.CompressFormat.WEBP)
        assertEquals("image/webp", MediaFiles.sniffImageType(webp))

        val result = AndroidImageConverter(DownloadedMedia(webp, "image/webp"))
        assertEquals("image/jpeg", result.contentType)
        assertEquals("image/jpeg", MediaFiles.sniffImageType(result.bytes))
        val decoded = BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size)
        assertEquals(64, decoded.width)
        assertEquals(48, decoded.height)
    }

    @Test
    fun jpegAndPngAreUntouched() {
        val jpeg = DownloadedMedia(encode(Bitmap.CompressFormat.JPEG), "image/jpeg")
        assertSame(jpeg, AndroidImageConverter(jpeg))
        val png = DownloadedMedia(encode(Bitmap.CompressFormat.PNG), "image/png")
        assertSame(png, AndroidImageConverter(png))
    }

    @Test
    fun garbageIsRejected() {
        try {
            AndroidImageConverter(DownloadedMedia(ByteArray(100) { 3 }, "image/avif"))
            fail("мусор не должен превратиться в картинку")
        } catch (_: ImageFetcher.NotAnImage) {
        }
    }
}
