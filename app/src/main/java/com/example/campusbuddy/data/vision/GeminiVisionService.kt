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
 * Uses structured JSON output for reliable field extraction.
 * Input images should already be cropped to the viewfinder area (by the caller)
 * to eliminate background noise and maximize text resolution.
 */
class GeminiVisionService(private val apiKey: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Send an ID card bitmap to Gemini Flash API and extract structured student data.
     *
     * @param bitmap The captured ID card image (should be viewfinder-cropped, full resolution)
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

                // 1. Compress bitmap to JPEG base64 — NO downscaling, keep max quality
                val base64Image = bitmapToBase64(bitmap)
                if (base64Image == null) {
                    return@withContext Result.failure(Exception("Failed to compress image"))
                }

                // 2. Build the Gemini API request with structured JSON schema
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
     * Does NOT downscale — keeps full viewfinder-cropped resolution.
     * Uses high-quality JPEG compression (90%) for best OCR accuracy.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String? {
        val outputStream = ByteArrayOutputStream()
        // JPEG at 90% quality — high quality for text readability, reasonable size
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Build the Gemini API request JSON body with structured output instruction.
     * The prompt uses a few-shot example to guide extraction and includes
     * explicit field-level instructions for each of the 6 fields.
     */
    private fun buildJsonString(base64Image: String): String {
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
                        "text": "You are analyzing a student ID card photo for data extraction. Extract the following fields from this image. Return ONLY valid JSON with exactly these 6 keys (use empty string for any field you cannot find): fullName, registrationNumber, collegeName, department, year, email.

Field instructions:
- fullName: The student's full name as printed on the card. Include middle names if present.
- registrationNumber: The student ID / roll number / enrollment number — usually a combination of letters and digits.
- collegeName: The name of the college, institute, or university.
- department: The department, branch, programme, or major (e.g., 'Computer Science', 'BCA', 'BBA', 'Mechanical Engineering').
- year: The year of study, batch year, or semester (e.g., '1st', '2nd', '3rd', '4th', '2024', '2026').
- email: The student's email address printed on the card, if visible.

CRITICAL RULES:
1. Read the text exactly as printed — do not guess or infer values not written on the card.
2. If a field is not visible or not printed on the card, use an empty string.
3. Output ONLY a raw JSON object — no markdown, no code fences, no explanation.
4. Example output: {\"fullName\":\"John Doe\",\"registrationNumber\":\"2021BCS1234\",\"collegeName\":\"ABC College of Engineering\",\"department\":\"Computer Science\",\"year\":\"3rd\",\"email\":\"john.doe@college.edu\"}"
                    }
                ]
            }],
            "generationConfig": {
                "temperature": 0.1,
                "topP": 1.0,
                "topK": 1,
                "maxOutputTokens": 256
            }
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

            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
            }

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
     */
    private fun parseGeminiResponse(responseJson: String): StudentIdData {
        val geminiResponse = json.decodeFromString<GeminiApiResponse>(responseJson)

        val rawText = geminiResponse.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?: return StudentIdData()

        // Strip markdown code fences if present
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
                email = extracted.email?.trim() ?: "",
                frontScanned = true,
                backScanned = true
            )
        } catch (e: Exception) {
            StudentIdData()
        }
    }

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

    @Serializable
    data class ExtractedStudentData(
        val fullName: String? = null,
        val registrationNumber: String? = null,
        val collegeName: String? = null,
        val department: String? = null,
        val year: String? = null,
        val email: String? = null
    )
}
