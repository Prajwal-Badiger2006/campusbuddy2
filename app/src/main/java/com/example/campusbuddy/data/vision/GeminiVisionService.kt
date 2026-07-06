package com.example.campusbuddy.data.vision

import android.graphics.Bitmap
import android.util.Base64
import com.example.campusbuddy.ui.ocr.StudentIdData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service that uses Google Gemini Flash API to extract structured student ID data
 * from a captured ID card image.
 *
 * This provides significantly better accuracy than ML Kit + Regex because Gemini
 * understands context — it can infer the correct field even if the text is misread
 * (e.g., "Nane: Prjwal" → correctly extracted as "Name: Prajwal").
 *
 * Usage requires a Gemini API key from https://aistudio.google.com/apikey
 */
class GeminiVisionService(private val apiKey: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Send an ID card bitmap to Gemini Flash API and extract structured student data.
     *
     * @param bitmap The captured ID card image (will be compressed before sending)
     * @return Result containing StudentIdData on success, or an error message on failure
     */
    suspend fun extractStudentIdData(bitmap: Bitmap): Result<StudentIdData> =
        withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(
                        Exception("Gemini API key not configured. Get one at https://aistudio.google.com/apikey")
                    )
                }

                // 1. Compress bitmap to JPEG base64
                val base64Image = bitmapToBase64(bitmap)
                if (base64Image == null) {
                    return@withContext Result.failure(Exception("Failed to compress image"))
                }

                // 2. Build the Gemini API request
                val requestBody = buildJsonString(base64Image)

                // 3. Make the API call
                val responseJson = callGeminiApi(requestBody)

                // 4. Parse the response
                val extracted = parseGeminiResponse(responseJson)
                Result.success(extracted)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Compress and encode a bitmap as a JPEG base64 string.
     * Resizes to max 1024px on the longest side to reduce API costs and latency.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String? {
        // Scale down for faster upload while maintaining quality for OCR
        val maxDimension = 1024
        val scale = minOf(
            maxDimension.toFloat() / bitmap.width,
            maxDimension.toFloat() / bitmap.height,
            1f
        )
        val scaledBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        // JPEG at 85% quality — good balance of size and readability
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()

        if (bitmap !== scaledBitmap) {
            scaledBitmap.recycle()
        }

        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Build the Gemini API request JSON body.
     */
    private fun buildJsonString(base64Image: String): String {
        // Base64 charset (A-Za-z0-9+/) and padding (=) are all safe in JSON strings,
        // so no escaping is needed.
        return """
        {
            "contents": [{
                "parts": [
                    {
                        "inline_data": {
                            "mime_type": "image/jpeg",
                            "data": "$base64Image"
                        }
                    },
                    {
                        "text": "Extract the following fields from this Student ID card image. Return ONLY valid JSON with these exact keys, no markdown formatting, no code fences: fullName, registrationNumber, collegeName, department, year. Use empty strings for fields you cannot find. Do not include any explanation or markdown formatting in your response."
                    }
                ]
            }]
        }
        """.trimIndent()
    }

    /**
     * Call the Gemini Flash API via HTTPS.
     */
    private fun callGeminiApi(requestBody: String): String {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        )
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000

            // Write request body
            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            // Read response
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } else {
                val errorBody = connection.errorStream?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: "Unknown error"
                throw Exception("Gemini API error ($responseCode): $errorBody")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parse the Gemini API response JSON and extract StudentIdData fields.
     *
     * Gemini response structure:
     * {
     *   "candidates": [{
     *     "content": {
     *       "parts": [{
     *         "text": "{\"fullName\": \"...\", \"registrationNumber\": \"...\", ...}"
     *       }]
     *     }
     *   }]
     * }
     *
     * The LLM returns a JSON string embedded in the text field.
     */
    private fun parseGeminiResponse(responseJson: String): StudentIdData {
        // First, parse the outer Gemini response envelope
        val geminiResponse = json.decodeFromString<GeminiApiResponse>(responseJson)

        // Extract the text content from the first candidate
        val rawText = geminiResponse.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?: return StudentIdData()

        // Strip markdown code fences if present (the prompt tells Gemini not to use them,
        // but it sometimes does anyway)
        val cleanedJson = rawText
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        // Parse the inner JSON into our extracted data structure
        return try {
            val extracted = json.decodeFromString<ExtractedStudentData>(cleanedJson)
            StudentIdData(
                fullName = extracted.fullName?.trim() ?: "",
                registrationNumber = extracted.registrationNumber?.trim() ?: "",
                collegeName = extracted.collegeName?.trim() ?: "",
                department = extracted.department?.trim() ?: "",
                year = extracted.year?.trim() ?: "",
                frontScanned = true,
                backScanned = true
            )
        } catch (e: Exception) {
            // If parsing fails, return empty data so the app can fall back
            StudentIdData()
        }
    }

    // ── Gemini API response DTOs ──

    @Serializable
    data class GeminiApiResponse(
        val candidates: List<Candidate>? = null
    )

    @Serializable
    data class Candidate(
        val content: Content? = null
    )

    @Serializable
    data class Content(
        val parts: List<Part>? = null
    )

    @Serializable
    data class Part(
        val text: String? = null
    )

    /**
     * Internal DTO for the JSON that Gemini returns inside the text field.
     * All fields are nullable so that partial extraction doesn't crash parsing.
     */
    @Serializable
    data class ExtractedStudentData(
        val fullName: String? = null,
        val registrationNumber: String? = null,
        val collegeName: String? = null,
        val department: String? = null,
        val year: String? = null
    )
}
