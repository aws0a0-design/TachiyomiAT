package eu.kanade.translation.model

import android.graphics.Bitmap
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class PageTranslation(
    var blocks: MutableList<TranslationBlock> = mutableListOf(),
    var imgWidth: Float = 0f,
    var imgHeight: Float = 0f,
) {
    @Transient
    var cleanedBitmap: Bitmap? = null

    @Transient
    var renderedBitmap: Bitmap? = null
    companion object {
        val EMPTY = PageTranslation()
    }
}

@Serializable
data class TranslationBlock(
    var text: String,
    var translation: String = "",
    var width: Float,
    var height: Float,
    var x: Float,
    var y: Float,
    var symHeight: Float,
    var symWidth: Float,
    val angle: Float,

)
