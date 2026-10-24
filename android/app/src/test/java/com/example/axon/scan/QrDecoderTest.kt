package com.example.axon.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the QR path that replaced the (much larger) ML Kit scanner: a real QR bitmap is rendered
 * into a padded camera-style Y plane and must decode back to the same URL.
 */
class QrDecoderTest {

    private val url = "http://192.168.1.35:6969/?token=axonlocal7f3a9c2e&udp=6970"

    /** Renders [text] as a QR code centred in a padded greyscale plane, like a camera frame. */
    private fun qrPlane(text: String, width: Int = 640, height: Int = 480, rowStride: Int = 680): ByteArray {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 320, 320)
        val plane = ByteArray(rowStride * height) { 0xC8.toByte() } // light background
        val left = (width - matrix.width) / 2
        val top = (height - matrix.height) / 2
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) {
                    plane[(top + y) * rowStride + left + x] = 0x10 // dark module
                }
            }
        }
        return plane
    }

    @Test
    fun decodesQrFromPaddedYPlane() {
        val plane = qrPlane(url)
        assertEquals(url, QrDecoder().decode(plane, rowStride = 680, width = 640, height = 480))
    }

    @Test
    fun toleratesRotatedSensorFrame() {
        // A portrait sensor delivers the same picture with swapped dimensions; QR is rotation
        // independent so the payload must still come back.
        val plane = qrPlane(url, width = 480, height = 640, rowStride = 512)
        assertEquals(url, QrDecoder().decode(plane, rowStride = 512, width = 480, height = 640))
    }

    @Test
    fun returnsNullOnBlankFrame() {
        val blank = ByteArray(700 * 400) { 0xC8.toByte() }
        assertNull(QrDecoder().decode(blank, rowStride = 700, width = 680, height = 400))
    }

    @Test
    fun returnsNullOnMalformedInput() {
        assertNull(QrDecoder().decode(ByteArray(10), rowStride = 4, width = 8, height = 8))
        assertNull(QrDecoder().decode(ByteArray(64), rowStride = 8, width = 0, height = 8))
    }
}
