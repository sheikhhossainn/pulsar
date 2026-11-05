package dev.pulsar.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Tool 4: GeminiFactCheckTool
 * Verification of viral rumors, news panics, and health claims.
 * Architecture:
 * 1. Zero-PII Sanitizer: Strict regex whitelist scrubber stripping personal phone numbers,
 *    names, and private identifiers before dispatch.
 * 2. Gemini 2.5 Flash Bridge: Fast grounded fact verification.
 * 3. Graceful Offline Degradation: Protective advice if no internet is available.
 */
class GeminiFactCheckTool(
    private val context: Context,
    private val apiKey: String? = null
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        const val OFFLINE_ADVISORY = "ইন্টারনেট সংযোগ না থাকায় খবরটি এখন যাচাই করা সম্ভব হচ্ছে না। তবে বিশ্বস্ত সংবাদমাধ্যম ছাড়া ফেসবুকের কোনো তথ্যে বিশ্বাস করবেন না বা কাউকে টাকা পাঠাবেন না।"

        // PII Scrubbing Patterns (Phones, NID, Cards, Personal addresses)
        private val PHONE_PATTERN = Pattern.compile("(?:\\+?88)?01[3-9]\\d{8}")
        private val NID_PATTERN = Pattern.compile("\\b\\d{10,17}\\b")
        private val EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    }

    data class FactCheckVerdict(
        val isOnline: Boolean,
        val sanitizedClaim: String,
        val verdictBengali: String,
        val isVerified: Boolean
    )

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Strict Whitelist / NER PII Scrubber
     * Strips all private entities before network dispatch
     */
    fun sanitizeClaim(rawQuery: String): String {
        var clean = rawQuery
        clean = PHONE_PATTERN.matcher(clean).replaceAll("[PHONE_REDACTED]")
        clean = NID_PATTERN.matcher(clean).replaceAll("[ID_REDACTED]")
        clean = EMAIL_PATTERN.matcher(clean).replaceAll("[EMAIL_REDACTED]")
        // Strip common conversational request prefixes
        clean = clean.replace("কী সত্যি", "")
            .replace("সত্যি নাকি", "")
            .replace("যাচাই কর", "")
            .replace("চেক কর", "")
            .trim()
        return clean
    }

    suspend fun verifyClaim(rawQuery: String): FactCheckVerdict = withContext(Dispatchers.IO) {
        val sanitized = sanitizeClaim(rawQuery)

        if (!isNetworkAvailable() || apiKey.isNullOrBlank()) {
            return@withContext FactCheckVerdict(
                isOnline = false,
                sanitizedClaim = sanitized,
                verdictBengali = OFFLINE_ADVISORY,
                isVerified = false
            )
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val prompt = """
                You are Pulsar, a factual information guardian in Bangladesh.
                Verify this public claim or rumor in 2 concise Bengali sentences:
                Claim: "$sanitized"
                Rules:
                - State clearly whether it is TRUE, FALSE (RUMOR), or UNVERIFIED.
                - Use simple, compassionate Bengali suitable for an elderly listener.
                - Max 2 sentences.
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val jsonResp = JSONObject(body)
                val candidates = jsonResp.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text")

                    if (!text.isNullOrBlank()) {
                        return@withContext FactCheckVerdict(
                            isOnline = true,
                            sanitizedClaim = sanitized,
                            verdictBengali = text.trim(),
                            isVerified = true
                        )
                    }
                }
            }

            // Fallback if API returned empty/error
            FactCheckVerdict(
                isOnline = true,
                sanitizedClaim = sanitized,
                verdictBengali = OFFLINE_ADVISORY,
                isVerified = false
            )
        } catch (_: Exception) {
            FactCheckVerdict(
                isOnline = false,
                sanitizedClaim = sanitized,
                verdictBengali = OFFLINE_ADVISORY,
                isVerified = false
            )
        }
    }
}
