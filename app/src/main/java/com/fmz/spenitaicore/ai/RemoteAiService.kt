package com.fmz.spenitaicore.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.HttpURLConnection
import java.time.LocalDate

/**
 * Remote AI service that supports Gemini, OpenAI, and OpenAI-compatible (OpenRouter, etc.) APIs.
 * All vision-capable providers are supported: user provides an API key + selects provider.
 *
 * Provider configurations:
 * - **gemini**: https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={KEY}
 *   Default model: gemini-2.0-flash
 * - **openai**: https://api.openai.com/v1/chat/completions (Bearer token)
 *   Default model: gpt-4o-mini
 * - **custom**: user-provided base URL (OpenAI-compatible /v1/chat/completions endpoint, e.g. OpenRouter)
 *   Default model: provided by user or "openai/gpt-4o-mini"
 */
class RemoteAiService(private val context: Context) {

    companion object {
        private const val TAG = "RemoteAiService"
        private const val TIMEOUT_MS = 60_000

        // Default models per provider
        val DEFAULT_MODELS = mapOf(
            "gemini" to "gemini-flash-lite-latest",
            "openai" to "gpt-4o-mini"
        )

        // Provider display names
        val PROVIDER_NAMES = mapOf(
            "aicore" to "On-device (AICore)",
            "gemini" to "Google Gemini",
            "openai" to "OpenAI",
            "custom" to "Custom (OpenAI-compatible)"
        )

        val PROVIDER_KEYS = listOf("aicore", "gemini", "openai", "custom")
    }

    /**
     * Check if the device has internet connectivity.
     */
    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            Log.w(TAG, "Connectivity check failed", e)
            false
        }
    }

    /**
     * Send a vision request to the configured AI provider and return the raw JSON response string.
     */
    suspend fun sendVisionRequest(
        provider: String,
        apiKey: String,
        model: String,
        imageBitmap: Bitmap,
        textPrompt: String,
        customUrl: String = ""
    ): String? = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                "gemini" -> callGeminiApi(apiKey, model, imageBitmap, textPrompt)
                "openai" -> callOpenAiApi(apiKey, model, imageBitmap, textPrompt)
                "custom" -> callCustomApi(apiKey, model, imageBitmap, textPrompt, customUrl)
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remote AI request failed for $provider", e)
            null
        }
    }

    /**
     * Send a text-only request (for insights, no image).
     */
    suspend fun sendTextRequest(
        provider: String,
        apiKey: String,
        model: String,
        textPrompt: String,
        customUrl: String = ""
    ): String? = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                "gemini" -> callGeminiTextApi(apiKey, model, textPrompt)
                "openai" -> callOpenAiTextApi(apiKey, model, textPrompt)
                "custom" -> callCustomTextApi(apiKey, model, textPrompt, customUrl)
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remote AI text request failed for $provider", e)
            null
        }
    }

    // ── Gemini API ─────────────────────────────────────────────────────────

    private fun callGeminiApi(
        apiKey: String,
        model: String,
        bitmap: Bitmap,
        prompt: String
    ): String? {
        val base64 = bitmapToBase64(bitmap)
        val modelName = model.ifBlank { DEFAULT_MODELS["gemini"]!! }
        val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val json = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", prompt)
                        }
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "image/jpeg")
                                put("data", base64)
                            }
                        }
                    }
                }
            }
        }

        val response = httpPost(urlStr, json.toString())
        if (response == null) {
            Log.w(TAG, "Gemini API returned null response")
            return null
        }

        val jsonElement = Json.parseToJsonElement(response).jsonObject
        val candidates = jsonElement["candidates"]?.jsonArray
        val text = candidates?.firstOrNull()
            ?.jsonObject
            ?.let { extractGeminiText(it) }
        if (text.isNullOrBlank()) {
            // Check for error
            val error = jsonElement["error"]?.jsonObject
            Log.w(TAG, "Gemini error: $error")
        }
        return text?.trim()
    }

    private fun callGeminiTextApi(apiKey: String, model: String, prompt: String): String? {
        val modelName = model.ifBlank { DEFAULT_MODELS["gemini"]!! }
        val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val json = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", prompt)
                        }
                    }
                }
            }
        }

        val response = httpPost(urlStr, json.toString()) ?: return null
        val jsonElement = Json.parseToJsonElement(response).jsonObject
        val text = jsonElement["candidates"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.let { extractGeminiText(it) }
        return text?.trim()
    }

    private fun extractGeminiText(candidate: JsonObject): String? {
        val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: return null
        val texts = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
        return texts.joinToString("\n").ifBlank { null }
    }

    // ── OpenAI API ─────────────────────────────────────────────────────────

    private fun callOpenAiApi(
        apiKey: String,
        model: String,
        bitmap: Bitmap,
        prompt: String
    ): String? {
        val base64 = bitmapToBase64(bitmap)
        val modelName = model.ifBlank { DEFAULT_MODELS["openai"]!! }

        val json = buildJsonObject {
            put("model", modelName)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,$base64")
                            }
                        }
                    }
                }
            }
            put("max_tokens", 4096)
        }

        val response = httpPost("https://api.openai.com/v1/chat/completions", json.toString(), apiKey)
        return extractOpenAiText(response)
    }

    private fun callOpenAiTextApi(apiKey: String, model: String, prompt: String): String? {
        val modelName = model.ifBlank { DEFAULT_MODELS["openai"]!! }

        val json = buildJsonObject {
            put("model", modelName)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
            put("max_tokens", 4096)
        }

        val response = httpPost("https://api.openai.com/v1/chat/completions", json.toString(), apiKey)
        return extractOpenAiText(response)
    }

    private fun extractOpenAiText(response: String?): String? {
        if (response == null) return null
        return try {
            val obj = Json.parseToJsonElement(response).jsonObject
            obj["choices"]?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse OpenAI response", e)
            null
        }
    }

    // ── Custom (OpenAI-compatible, e.g., OpenRouter) ──────────────────────

    private fun callCustomApi(
        apiKey: String,
        model: String,
        bitmap: Bitmap,
        prompt: String,
        customUrl: String
    ): String? {
        val base64 = bitmapToBase64(bitmap)
        val baseUrl = customUrl.ifBlank { "https://openrouter.ai/api" }
        val urlStr = "${baseUrl.trimEnd('/')}/chat/completions"
        val modelName = model.ifBlank { "openai/gpt-4o-mini" }

        val json = buildJsonObject {
            put("model", modelName)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,$base64")
                            }
                        }
                    }
                }
            }
            put("max_tokens", 4096)
        }

        val response = httpPost(urlStr, json.toString(), apiKey)
        return extractOpenAiText(response)
    }

    private fun callCustomTextApi(
        apiKey: String,
        model: String,
        prompt: String,
        customUrl: String
    ): String? {
        val baseUrl = customUrl.ifBlank { "https://openrouter.ai/api" }
        val urlStr = "${baseUrl.trimEnd('/')}/chat/completions"
        val modelName = model.ifBlank { "openai/gpt-4o-mini" }

        val json = buildJsonObject {
            put("model", modelName)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
            put("max_tokens", 4096)
        }

        val response = httpPost(urlStr, json.toString(), apiKey)
        return extractOpenAiText(response)
    }

    // ── HTTP Helper ────────────────────────────────────────────────────────

    private fun httpPost(urlStr: String, jsonBody: String, bearerToken: String? = null): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            if (bearerToken != null) {
                conn.setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            conn.doOutput = true
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS

            conn.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "HTTP $responseCode from $urlStr: $errorBody")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP POST failed: ${e.message}", e)
            null
        }
    }

    // ── Image Processing ───────────────────────────────────────────────────

    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Resize if too large (Gemini has 20MB limit, we keep it small)
        val maxDim = 2048f
        val width = bitmap.width
        val height = bitmap.height
        val scale = maxOf(width, height).coerceAtLeast(1).toFloat()
        val scaledBitmap = if (scale > maxDim) {
            val factor = maxDim / scale
            Bitmap.createScaledBitmap(
                bitmap,
                (width * factor).toInt().coerceAtLeast(1),
                (height * factor).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }

        val output = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        if (scaledBitmap !== bitmap) scaledBitmap.recycle()
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
}
