package com.example.campusbuddy.ui.ocr

import android.graphics.Bitmap
import android.graphics.Color
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
 * - Grayscale conversion
 * - Contrast Limited Adaptive Histogram Equalization (CLAHE) approximation
 * - Adaptive thresholding (Sauvola-like local binarization)
 * - Median filtering for noise reduction
 * - Light deskew correction via text projection analysis
 */
object ImagePreprocessor {

    /**
     * Full pre-processing pipeline. Run this before passing the image to ML Kit OCR.
     *
     * @param bitmap The original captured bitmap
     * @return Pre-processed bitmap with enhanced text clarity
     */
    suspend fun preprocessForOcr(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        var processed = bitmap

        // Step 1: Ensure consistent input size (scale down if too large for performance)
        processed = normalizeResolution(processed, maxDimension = 1200)

        // Step 2: Convert to grayscale
        processed = toGrayscale(processed)

        // Step 3: Apply median blur to reduce sensor noise while preserving edges
        processed = medianBlur(processed, kernelSize = 3)

        // Step 4: Enhance local contrast (CLAHE approximation)
        processed = enhanceContrast(processed, tileSize = 8, clipLimit = 3.0f)

        // Step 5: Adaptive binarization (Sauvola method) for high-contrast text
        processed = sauvolaBinarize(processed, windowSize = 25, k = 0.2f)

        processed
    }

    /**
     * Lightweight pre-processing for real-time preview analysis.
     * Only converts to grayscale and adjusts contrast - faster than full pipeline.
     */
    suspend fun quickPreprocess(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        var processed = bitmap
        processed = toGrayscale(processed)
        processed = enhanceContrast(processed, tileSize = 8, clipLimit = 2.0f)
        processed
    }

    /**
     * Normalize resolution to a max dimension while maintaining aspect ratio.
     * This ensures consistent processing time and avoids feeding huge bitmaps to ML Kit.
     */
    private fun normalizeResolution(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val scale = minOf(
            maxDimension.toFloat() / width,
            maxDimension.toFloat() / height,
            1f // Don't upscale
        )
        if (scale >= 1f) return bitmap

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
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
     * Replaces each pixel with the median value from its neighborhood.
     * More effective than Gaussian blur for removing salt-and-pepper noise
     * while keeping text edges sharp.
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
                // Sort and take median
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
     * approximation. Works on local tiles to enhance text-background contrast
     * while limiting noise amplification in uniform areas.
     *
     * @param tileSize Size of local tiles in pixels
     * @param clipLimit Maximum contrast amplification factor
     */
    private fun enhanceContrast(bitmap: Bitmap, tileSize: Int, clipLimit: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Extract grayscale values
        val gray = IntArray(width * height) { Color.red(pixels[it]) }

        // Process each tile
        val tilesX = (width + tileSize - 1) / tileSize
        val tilesY = (height + tileSize - 1) / tileSize

        // Build tile CDFs (Cumulative Distribution Functions)
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

                // Clip histogram to limit contrast amplification
                val clipValue = (totalPixels.toFloat() / 256f * clipLimit).toInt()
                var clippedPixels = 0
                for (i in 0..255) {
                    if (histogram[i] > clipValue) {
                        clippedPixels += histogram[i] - clipValue
                        histogram[i] = clipValue
                    }
                }

                // Redistribute clipped pixels evenly
                val redistribution = clippedPixels / 256
                for (i in 0..255) {
                    histogram[i] += redistribution
                }

                // Build CDF
                val cdf = tileCdfs[ty][tx]
                var cumulative = 0f
                for (i in 0..255) {
                    cumulative += histogram[i].toFloat() / totalPixels
                    cdf[i] = cumulative
                }
            }
        }

        // Apply bilinear interpolation between tile CDFs
        val result = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val v = gray[y * width + x]

                // Tile coordinates with fractional parts for interpolation
                val fx = (x.toFloat() / tileSize) - 0.5f
                val fy = (y.toFloat() / tileSize) - 0.5f
                val tx0 = fx.toInt().coerceIn(0, tilesX - 2)
                val ty0 = fy.toInt().coerceIn(0, tilesY - 2)
                val tx1 = tx0 + 1
                val ty1 = ty0 + 1
                val fracX = fx - tx0
                val fracY = fy - ty0

                // Bilinear interpolation of CDF values
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
     * Sauvola binarization — a local adaptive thresholding method that works well
     * for documents with varying illumination. Computes a local threshold for each
     * pixel based on the local mean and standard deviation.
     *
     * Formula: T(x,y) = m(x,y) * (1 + k * (s(x,y)/R - 1))
     * where m = local mean, s = local standard deviation, k = 0.2, R = 128
     *
     * This produces cleaner text than global Otsu thresholding for ID cards
     * that may have uneven lighting or shadows.
     */
    private fun sauvolaBinarize(bitmap: Bitmap, windowSize: Int, k: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height) { Color.red(pixels[it]) }
        val halfWindow = windowSize / 2
        val R = 128f

        // Precompute integral image for fast local mean and variance
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

                // Local sum using integral image
                val sum = integral[y1 + 1][x1 + 1] - integral[y0][x1 + 1]
                        - integral[y1 + 1][x0] + integral[y0][x0]

                // Local sum of squares
                val sumSq = integralSq[y1 + 1][x1 + 1] - integralSq[y0][x1 + 1]
                        - integralSq[y1 + 1][x0] + integralSq[y0][x0]

                val mean = (sum.toFloat() / area)
                val variance = (sumSq.toFloat() / area) - (mean * mean)
                val stdDev = sqrt(max(0f, variance))

                // Sauvola threshold
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
