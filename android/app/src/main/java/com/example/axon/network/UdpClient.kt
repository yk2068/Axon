package com.example.axon.network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
class UdpClient(
    private val serverIp: String,
    private val port: Int = 6970
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    // Buffered, not conflated: a conflated channel silently replaced a pending move
    // packet, so the movement it carried was lost and the cursor fell behind the finger.
    private val channel = Channel<ByteArray>(capacity = 32)
    init {
        scope.launch {
            try {
                val address = InetAddress.getByName(serverIp)
                val socket = DatagramSocket()
                for (bytes in channel) {
                    try {
                        val packet = DatagramPacket(bytes, bytes.size, address, port)
                        socket.send(packet)
                    } catch (e: Exception) {
                    }
                }
                socket.close()
            } catch (e: Exception) {
            }
        }
    }
    fun sendMove(dx: Double, dy: Double) {
        val bytes = createMovePacket(dx, dy)
        channel.trySend(bytes)
    }
    fun sendScroll(dy: Double, dx: Double = 0.0) {
        val bytes = ByteArray(9)
        bytes[0] = 1.toByte()
        val yBits = java.lang.Float.floatToIntBits(dy.toFloat())
        bytes[1] = (yBits shr 24).toByte()
        bytes[2] = (yBits shr 16).toByte()
        bytes[3] = (yBits shr 8).toByte()
        bytes[4] = yBits.toByte()
        val xBits = java.lang.Float.floatToIntBits(dx.toFloat())
        bytes[5] = (xBits shr 24).toByte()
        bytes[6] = (xBits shr 16).toByte()
        bytes[7] = (xBits shr 8).toByte()
        bytes[8] = xBits.toByte()
        channel.trySend(bytes)
    }
    fun close() {
        try {
            channel.close()
            scope.cancel()
        } catch (e: Exception) {
        }
    }
    // Pinch rides the same datagram channel as motion: it is a continuous stream of
    // tiny relative changes, and a late packet is worthless. Packet types 2/3/4 keep
    // it separate from move (0) and scroll (1).
    fun sendPinchBegin() {
        channel.trySend(pinchPacket(2, 0.0))
    }
    fun sendPinchUpdate(scaleDelta: Double) {
        channel.trySend(pinchPacket(3, scaleDelta))
    }
    fun sendPinchEnd() {
        channel.trySend(pinchPacket(4, 0.0))
    }
    companion object {
        private fun pinchPacket(type: Int, value: Double): ByteArray {
            val bytes = ByteArray(9)
            bytes[0] = type.toByte()
            val bits = java.lang.Float.floatToIntBits(value.toFloat())
            bytes[1] = (bits shr 24).toByte()
            bytes[2] = (bits shr 16).toByte()
            bytes[3] = (bits shr 8).toByte()
            bytes[4] = bits.toByte()
            val zero = java.lang.Float.floatToIntBits(0f)
            bytes[5] = (zero shr 24).toByte()
            bytes[6] = (zero shr 16).toByte()
            bytes[7] = (zero shr 8).toByte()
            bytes[8] = zero.toByte()
            return bytes
        }
        fun createMovePacket(dx: Double, dy: Double): ByteArray {
            val bytes = ByteArray(9)
            bytes[0] = 0.toByte()
            val xBits = java.lang.Float.floatToIntBits(dx.toFloat())
            bytes[1] = (xBits shr 24).toByte()
            bytes[2] = (xBits shr 16).toByte()
            bytes[3] = (xBits shr 8).toByte()
            bytes[4] = xBits.toByte()
            val yBits = java.lang.Float.floatToIntBits(dy.toFloat())
            bytes[5] = (yBits shr 24).toByte()
            bytes[6] = (yBits shr 16).toByte()
            bytes[7] = (yBits shr 8).toByte()
            bytes[8] = yBits.toByte()
            return bytes
        }
    }
}
