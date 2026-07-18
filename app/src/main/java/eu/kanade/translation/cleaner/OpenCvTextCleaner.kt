package eu.kanade.translation.cleaner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import eu.kanade.translation.model.TranslationBlock
import logcat.logcat
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.photo.Photo
import java.util.concurrent.atomic.AtomicBoolean

class OpenCvTextCleaner(private val context: Context) {

    private val isInitialized = AtomicBoolean(false)

    fun clean(bitmap: Bitmap, blocks: List<TranslationBlock>): Bitmap {
        if (blocks.isEmpty()) return bitmap
        ensureInitialized()
        if (!isInitialized.get()) return bitmap

        val src = Mat()
        val mask = Mat(bitmap.height, bitmap.width, CvType.CV_8U, Scalar(0.0))
        val dst = Mat()

        try {
            Utils.bitmapToMat(bitmap, src)
            blocks.forEach { block ->
                val x1 = block.x.coerceAtLeast(0f).toDouble()
                val y1 = block.y.coerceAtLeast(0f).toDouble()
                val x2 = (block.x + block.width).coerceAtMost(bitmap.width.toFloat()).toDouble()
                val y2 = (block.y + block.height).coerceAtMost(bitmap.height.toFloat()).toDouble()
                if (x2 > x1 && y2 > y1) {
                    Core.rectangle(mask, Point(x1, y1), Point(x2, y2), Scalar(255.0), -1)
                }
            }

            Photo.inpaint(src, mask, dst, 3.0, Photo.INPAINT_TELEA)
            val cleanedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, cleanedBitmap)
            return cleanedBitmap
        } catch (error: Throwable) {
            logcat { "OpenCV inpainting failed: ${error.stackTraceToString()}" }
            return bitmap
        } finally {
            src.release()
            mask.release()
            dst.release()
        }
    }

    fun renderTranslatedText(bitmap: Bitmap, blocks: List<TranslationBlock>): Bitmap {
        if (blocks.isEmpty()) return bitmap

        val output = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textSize = maxOf(22f, minOf(bitmap.width.toFloat() / 18f, bitmap.height.toFloat() / 18f))
        }
        val strokePaint = Paint(textPaint).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = maxOf(3f, textSize / 10f)
        }

        blocks.forEach { block ->
            val text = block.translation.ifBlank { block.text }
            if (text.isBlank()) return@forEach

            val x = block.x.coerceAtLeast(0f) + 8f
            val y = block.y.coerceAtLeast(0f) + block.height / 2f + 8f
            canvas.drawText(text, x, y, strokePaint)
            canvas.drawText(text, x, y, textPaint)
        }

        return output
    }

    private fun ensureInitialized() {
        if (isInitialized.get()) return
        try {
            val loaded = OpenCVLoader.initDebug()
            isInitialized.set(loaded)
            if (!loaded) {
                logcat { "OpenCV could not be initialized; falling back to the original bitmap." }
            }
        } catch (error: Throwable) {
            logcat { "OpenCV initialization failed: ${error.stackTraceToString()}" }
        }
    }
}
