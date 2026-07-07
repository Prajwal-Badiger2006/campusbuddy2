package com.example.campusbuddy.ui.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class OcrProcessor {

    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Process a bitmap through ML Kit and return the raw recognized text.
     * Uses viewfinder-cropped images (pre-cropped by caller) for cleaner input.
     * Parsing is delegated to [StudentIdParser].
     */
    suspend fun recognizeText(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        useFullPreprocessing: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        // Step 1: Light pre-processing — grayscale + CLAHE contrast enhancement
        val lightBitmap = ImagePreprocessor.quickPreprocess(bitmap)
        val lightResult = runRecognition(lightBitmap, rotationDegrees)

        // Step 2: If light pre-processing yields text, return immediately
        if (lightResult.isNotBlank()) {
            return@withContext lightResult
        }

        // Step 3: Fall back to aggressive binarization for difficult cases
        if (useFullPreprocessing) {
            val fullBitmap = ImagePreprocessor.preprocessForOcr(bitmap)
            val fullResult = runRecognition(fullBitmap, rotationDegrees)
            if (fullResult.isNotBlank()) {
                return@withContext fullResult
            }
        }

        // Step 4: Last resort — run on the original bitmap without any processing
        runRecognition(bitmap, rotationDegrees)
    }

    /**
     * Run ML Kit text recognition on a pre-processed bitmap.
     */
    private suspend fun runRecognition(bitmap: Bitmap, rotationDegrees: Int): String {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener {
                    continuation.resume("")
                }
        }
    }

    /**
     * Parse raw OCR text and extract student ID fields for the front side.
     * Delegates to [StudentIdParser].
     */
    fun parseFrontSide(rawText: String): StudentIdData =
        StudentIdParser.parseFrontSide(rawText)

    /**
     * Parse raw OCR text and extract student ID fields for the back side.
     * Delegates to [StudentIdParser].
     */
    fun parseBackSide(rawText: String, existingData: StudentIdData): StudentIdData =
        StudentIdParser.parseBackSide(rawText, existingData)

    /**
     * Full parse: run both front and back parsers and merge results.
     * Delegates to [StudentIdParser].
     */
    fun parseFull(rawText: String): StudentIdData =
        StudentIdParser.parseFull(rawText)

    fun close() {
        recognizer.close()
    }
}
