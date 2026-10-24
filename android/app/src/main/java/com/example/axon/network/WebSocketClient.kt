package com.example.axon.network
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit
class WebSocketClient(private val listener: WebSocketConnectionListener) : InputClient {
    private var serverIp: String = ""
    override fun getServerIp(): String {
        return serverIp
    }
    private var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private var generation = 0
    interface WebSocketConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
    }
    fun connect(url: String) {
        try {
            serverIp = java.net.URI(url).host ?: ""
        } catch (e: Exception) {
            serverIp = ""
        }
        // A reconnect replaces the socket instead of stacking on top of it. Bumping the
        // generation makes every callback of an older socket a no-op, so a late failure
        // from a dead connection can no longer tear down the new one.
        val gen = ++generation
        webSocket?.cancel()
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (gen == generation) listener.onConnected()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = org.json.JSONObject(text)
                    if (json.optString("type") == "ping") {
                        val timestamp = json.optLong("timestamp")
                        val responseJson = org.json.JSONObject()
                        responseJson.put("type", "pong")
                        responseJson.put("timestamp", timestamp)
                        webSocket.send(responseJson.toString())
                    }
                } catch (e: Exception) {
                }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (gen == generation) listener.onDisconnected()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (gen == generation) listener.onError(t.message ?: "Connection Failure")
            }
        })
    }
    override fun disconnect() {
        generation++
        webSocket?.close(1000, "Normal Close")
        webSocket = null
        try {
            client.connectionPool.evictAll()
        } catch (e: Exception) {
        }
    }
    fun send(message: String) {
        webSocket?.send(message)
    }
    override fun sendMove(dx: Double, dy: Double) {
        send("{\"type\":\"move\",\"dx\":$dx,\"dy\":$dy}")
    }
    override fun sendClick(button: String) {
        send("{\"type\":\"click\",\"button\":\"$button\"}")
    }
    override fun sendMouseDown(button: String) {
        send("{\"type\":\"mousedown\",\"button\":\"$button\"}")
    }
    override fun sendMouseUp(button: String) {
        send("{\"type\":\"mouseup\",\"button\":\"$button\"}")
    }
    override fun sendScroll(dy: Double, dx: Double) {
        send("{\"type\":\"scroll\",\"dy\":$dy,\"dx\":$dx}")
    }
    override fun sendType(text: String) {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        send("{\"type\":\"type\",\"text\":\"$escaped\"}")
    }
    override fun sendKey(key: String) {
        send("{\"type\":\"key\",\"key\":\"$key\"}")
    }
    override fun sendKeyCombo(modifier: String, key: String) {
        send("{\"type\":\"keycombo\",\"modifier\":\"$modifier\",\"key\":\"$key\"}")
    }
    override fun sendPinchBegin() {
        send("{\"type\":\"pinch\",\"phase\":\"begin\"}")
    }
    override fun sendPinchUpdate(scaleDelta: Double) {
        send("{\"type\":\"pinch\",\"phase\":\"update\",\"scale\":$scaleDelta}")
    }
    override fun sendPinchEnd() {
        send("{\"type\":\"pinch\",\"phase\":\"end\"}")
    }
}
