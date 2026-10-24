package com.example.axon.network
interface InputClient {
    fun sendMove(dx: Double, dy: Double)
    fun sendScroll(dy: Double, dx: Double = 0.0)
    fun sendClick(button: String)
    fun sendMouseDown(button: String)
    fun sendMouseUp(button: String)
    fun sendType(text: String)
    fun sendKey(key: String)
    fun sendKeyCombo(modifier: String, key: String)

    /** Opens a two-finger pinch at the remote cursor position. */
    fun sendPinchBegin()

    /** Relative change of the finger separation: 0.05 == 5 % wider than last frame. */
    fun sendPinchUpdate(scaleDelta: Double)

    /** Closes the pinch gesture. */
    fun sendPinchEnd()
    fun disconnect()
    fun getServerIp(): String
}
