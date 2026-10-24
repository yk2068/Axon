package com.example.axon.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Decodes a QR code straight from a camera Y plane: 8-bit greyscale, rows possibly padded by
 * `rowStride`. No bitmap conversion and no native scanner model, which is what keeps the APK small.
 *
 * QR detection is orientation independent, so the sensor rotation is not applied here.
 */
class QrDecoder {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    /** Returns the QR payload, or null when the frame holds no readable code. */
    fun decode(luma: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || rowStride < width || luma.size < rowStride * height) return null
        val source = PlanarYUVLuminanceSource(luma, rowStride, height, 0, 0, width, height, false)
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))?.text
        } catch (e: NotFoundException) {
            null
        } finally {
            reader.reset()
        }
    }
}
