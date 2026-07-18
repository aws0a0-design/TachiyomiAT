package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import logcat.logcat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class GeminiTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    private val apiKey: String,
    private val modelName: String,
    private val maxOutputToken: Int,
    private val temp: Float,
) : TextTranslator {

    private val okHttpClient = OkHttpClient()
    private val resolvedModelName = modelName.ifBlank { "gemini-2.5-flash" }

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Gemini API key is missing")
        }

        try {
            val payload = pages.mapValues { (_, page) -> page.blocks.map { block -> block.text } }
            val json = JSONObject(payload)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = buildJsonObject {
                put("contents", buildJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", "Translate the following JSON object to ${toLang.label} and preserve the same object structure. Replace any watermark/site-link text with RTMTH. Return a JSON object only.\n$json")
                        }
                    }
                })
                putJsonObject("generationConfig") {
                    put("temperature", temp)
                    put("topP", 0.5f)
                    put("topK", 30)
                    put("maxOutputTokens", maxOutputToken)
                    put("responseMimeType", "application/json")
                }
            }.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$resolvedModelName:generateContent?key=$apiKey")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).await()
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Gemini request failed: $responseBody")
            }

            val responseJson = JSONObject(responseBody)
            val text = responseJson
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()

            val resJson = JSONObject(text)
            for ((pageName, page) in pages) {
                page.blocks.forEachIndexed { index, block ->
                    val fallback = resJson.optJSONArray(pageName)?.optString(index, "NULL")
                    block.translation = if (fallback == null || fallback == "NULL") block.text else fallback
                }
                page.blocks = page.blocks.filterNot { it.translation.contains("RTMTH") }.toMutableList()
            }
        } catch (e: Exception) {
            logcat { "Gemini translation error: ${e.stackTraceToString()}" }
            throw e
        }
    }

    override fun close() {
    }
}
