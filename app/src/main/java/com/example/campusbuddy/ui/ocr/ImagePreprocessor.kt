package com.example.campusbuddy.ui.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Image pre-processing pipeline designed specifically to improve OCR accuracy
 * for printed text on student ID cards.
 *
 * Techniques used:
 * - Viewfinder-aligned cropping to eliminate background noise
 * - Grayscale conversion
 * - Contrast Limited Adaptive Histogram Equalization (CLAHE) approximation
 * - Adaptive thresholding (Sauvola-like local binarization)
 * - Median filtering for noise reduction
 */
object ImagePreprocessor {

    /**
     * Full pre-processing pipeline. Run this before passing the image to ML Kit OCR.
     * Skips cropping since ML Kit works on the viewfinder-cropped image from the caller.
     *
     * @param bitmap The captured bitmap (already cropped to viewfinder area)
     * @return Pre-processed bitmap with enhanced text clarity
     */
    suspend fun preprocessForOcr(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        var processed = bitmap

        // Step 1: Convert to grayscale
        processed = toGrayscale(processed)

        // Step 2: Apply median blur to reduce sensor noise while preserving edges
        processed = medianBlur(processed, kernelSize = 3)

        // Step 3: Enhance local contrast (CLAHE approximation)
        processed = enhanceContrast(processed, tileSize = 8, clipLimit = 3.0f)

        // Step 4: Adaptive binarization (Sauvola method) for high-contrast text
        processed = sauvolaBinarize(processed, windowSize = 25, k = 0.2f)

        processed
    }

    /**
     * Lightweight pre-processing for real-time preview / ML Kit analysis.
     * Only grayscale + contrast — faster than full pipeline.
     */
    suspend fun quickPreprocess(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        var processed = bitmap
        processed = toGrayscale(processed)
        processed = enhanceContrast(processed, tileSize = 8, clipLimit = 2.0f)
        processed
    }

    /**
     * Crop a full-camera-frame bitmap to the viewfinder rectangle area.
     *
     * The viewfinder is centered in the camera preview with width = 0.85f of the preview,
     * and aspect ratio 1.6f (height = width / 1.6f).
     *
     * @param fullFrame The full-resolution camera frame bitmap
     * @param viewfinderWidthFraction Fraction of total width the viewfinder occupies (e.g., 0.85f)
     * @param viewfinderAspectRatio Height/width ratio of the viewfinder (e.g., 1.6f)
     * @return Cropped bitmap containing only the viewfinder area
     */
    fun cropToViewfinder(
        fullFrame: Bitmap,
        viewfinderWidthFraction: Float = 0.85f,
        viewfinderAspectRatio: Float = 1.6f
    ): Bitmap {
        val frameW = fullFrame.width
        val frameH = fullFrame.height

        // Viewfinder is centered — calculate its pixel dimensions
        val vfWidth = (frameW * viewfinderWidthFraction).toInt()
        val vfHeight = (vfWidth.toFloat() / viewfinderAspectRatio).toInt()

        // Center in the frame
        val left = (frameW - vfWidth) / 2
        val top = (frameH - vfHeight) / 2

        // Clamp to valid bounds
        val cropLeft = max(0, left)
        val cropTop = max(0, top)
        val cropWidth = min(vfWidth, frameW - cropLeft)
        val cropHeight = min(vfHeight, frameH - cropTop)

        if (cropWidth <= 0 || cropHeight <= 0) return fullFrame

        return Bitmap.createBitmap(fullFrame, cropLeft, cropTop, cropWidth, cropHeight)
    }

    /**
     * Convert a bitmap to grayscale using luminance weighting.
     * Uses the standard ITU-R BT.601 formula: Y = 0.299R + 0.587G + 0.114B
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = IntArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            result[i] = Color.rgb(gray, gray, gray)
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Median blur filter for noise reduction while preserving edges.
     */
    private fun medianBlur(bitmap: Bitmap, kernelSize: Int): Bitmap {
        if (kernelSize < 3 || kernelSize % 2 == 0) return bitmap

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = IntArray(width * height)
        val halfKernel = kernelSize / 2
        val neighborhoodSize = kernelSize * kernelSize
        val grayValues = IntArray(neighborhoodSize)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var idx = 0
                for (ky in -halfKernel..halfKernel) {
                    for (kx in -halfKernel..halfKernel) {
                        val px = (x + kx).coerceIn(0, width - 1)
                        val py = (y + ky).coerceIn(0, height - 1)
                        grayValues[idx++] = Color.red(pixels[py * width + px])
                    }
                }
                grayValues.sort()
                val median = grayValues[neighborhoodSize / 2]
                result[y * width + x] = Color.rgb(median, median, median)
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Contrast enhancement using a CLAHE (Contrast Limited Adaptive Histogram Equalization)
     * approximation. Works on local tiles to enhance text-background contrast.
     */
    private fun enhanceContrast(bitmap: Bitmap, tileSize: Int, clipLimit: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height) { Color.red(pixels[it]) }

        val tilesX = (width + tileSize - 1) / tileSize
        val tilesY = (height + tileSize - 1) / tileSize

        val tileCdfs = Array(tilesY) { Array(tilesX) { FloatArray(256) } }

        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                val histogram = IntArray(256)
                var totalPixels = 0

                for (y in ty * tileSize until min((ty + 1) * tileSize, height)) {
                    for (x in tx * tileSize until min((tx + 1) * tileSize, width)) {
                        val v = gray[y * width + x]
                        histogram[v]++
                        totalPixels++
                    }
                }

                val clipValue = (totalPixels.toFloat() / 256f * clipLimit).toInt()
                var clippedPixels = 0
                for (i in 0..255) {
                    if (histogram[i] > clipValue) {
                        clippedPixels += histogram[i] - clipValue
                        histogram[i] = clipValue
                    }
                }

                val redistribution = clippedPixels / 256
                for (i in 0..255) {
                    histogram[i] += redistribution
                }

                val cdf = tileCdfs[ty][tx]
                var cumulative = 0f
                for (i in 0..255) {
                    cumulative += histogram[i].toFloat() / totalPixels
                    cdf[i] = cumulative
                }
            }
        }

        val result = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val v = gray[y * width + x]

                val fx = (x.toFloat() / tileSize) - 0.5f
                val fy = (y.toFloat() / tileSize) - 0.5f
                val tx0 = fx.toInt().coerceIn(0, tilesX - 2)
                val ty0 = fy.toInt().coerceIn(0, tilesY - 2)
                val tx1 = tx0 + 1
                val ty1 = ty0 + 1
                val fracX = fx - tx0
                val fracY = fy - ty0

                val cdf00 = tileCdfs[ty0][tx0][v]
                val cdf10 = tileCdfs[ty0][tx1][v]
                val cdf01 = tileCdfs[ty1][tx0][v]
                val cdf11 = tileCdfs[ty1][tx1][v]

                val cdfTop = cdf00 * (1f - fracX) + cdf10 * fracX
                val cdfBottom = cdf01 * (1f - fracX) + cdf11 * fracX
                val equalized = (cdfTop * (1f - fracY) + cdfBottom * fracY * 255f).toInt().coerceIn(0, 255)

                result[y * width + x] = Color.rgb(equalized, equalized, equalized)
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Sauvola binarization — local adaptive thresholding for varying illumination.
     */
    private fun sauvolaBinarize(bitmap: Bitmap, windowSize: Int, k: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height) { Color.red(pixels[it]) }
        val halfWindow = windowSize / 2
        val R = 128f

        val integral = Array(height + 1) { LongArray(width + 1) }
        val integralSq = Array(height + 1) { LongArray(width + 1) }

        for (y in 0 until height) {
            var rowSum = 0L
            var rowSumSq = 0L
            for (x in 0 until width) {
                val v = gray[y * width + x].toLong()
                rowSum += v
                rowSumSq += v * v
                integral[y + 1][x + 1] = integral[y][x + 1] + rowSum
                integralSq[y + 1][x + 1] = integralSq[y][x + 1] + rowSumSq
            }
        }

        val result = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val y0 = max(0, y - halfWindow)
                val y1 = min(height - 1, y + halfWindow)
                val x0 = max(0, x - halfWindow)
                val x1 = min(width - 1, x + halfWindow)

                val area = ((x1 - x0 + 1) * (y1 - y0 + 1)).toLong()

                val sum = integral[y1 + 1][x1 + 1] - integral[y0][x1 + 1]
                        - integral[y1 + 1][x0] + integral[y0][x0]

                val sumSq = integralSq[y1 + 1][x1 + 1] - integralSq[y0][x1 + 1]
                        - integralSq[y1 + 1][x0] + integralSq[y0][x0]

                val mean = (sum.toFloat() / area)
                val variance = (sumSq.toFloat() / area) - (mean * mean)
                val stdDev = sqrt(max(0f, variance))

                val threshold = mean * (1f + k * (stdDev / R - 1f))
                val binaryValue = if (gray[y * width + x].toFloat() < threshold) 0 else 255

                result[y * width + x] = Color.rgb(binaryValue, binaryValue, binaryValue)
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, width, 0, 0, width, height)
        return output
    }
}
