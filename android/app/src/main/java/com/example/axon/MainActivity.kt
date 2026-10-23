package com.example.axon

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.axon.network.WebSocketClient
import com.example.axon.theme.AxonTheme
import com.example.axon.ui.QrScannerScreen
import com.example.axon.ui.TouchpadScreen

enum class AppScreen {
    Scan,
    Control
}

/**
 * Single-purpose activity: scan a QR code, then act as a touchpad.
 *
 * While a session is up the activity holds a high-performance Wi-Fi lock and a partial
 * wake lock so that turning the screen off does not tear down the WebSocket connection.
 */
class MainActivity : ComponentActivity(), WebSocketClient.WebSocketConnectionListener {

    private var webSocketClient: WebSocketClient? = null
    private var currentScreen by mutableStateOf(AppScreen.Scan)

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webSocketClient = WebSocketClient(this)
        enableEdgeToEdge()
        setContent {
            AxonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Crossfade(targetState = currentScreen, label = "screenTransition") { screen ->
                        when (screen) {
                            AppScreen.Scan -> {
                                QrScannerScreen(
                                    onQrDetected = { url -> connectFromQr(url) }
                                )
                            }
                            AppScreen.Control -> {
                                webSocketClient?.let { client ->
                                    TouchpadScreen(
                                        client = client,
                                        onDisconnect = {
                                            client.disconnect()
                                            currentScreen = AppScreen.Scan
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun connectFromQr(url: String) {
        val wsUrl = try {
            val uri = java.net.URI(url)
            val query = if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
            "ws://${uri.host}:${uri.port}/ws$query"
        } catch (e: Exception) {
            url.replace("http://", "ws://").replace("/?", "/ws?")
        }
        webSocketClient?.connect(wsUrl)
    }

    private fun acquireKeepAlive() {
        try {
            if (wifiLock == null) {
                val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "axon:wifi")
                    .apply { setReferenceCounted(false) }
            }
            if (wifiLock?.isHeld != true) wifiLock?.acquire()

            if (wakeLock == null) {
                val power = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "axon:session")
                    .apply { setReferenceCounted(false) }
            }
            if (wakeLock?.isHeld != true) wakeLock?.acquire()
        } catch (e: Exception) {
            // Locks are best-effort; the session still works without them.
        }
    }

    private fun releaseKeepAlive() {
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
        }
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
        }
    }

    override fun onConnected() {
        runOnUiThread {
            acquireKeepAlive()
            currentScreen = AppScreen.Control
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            releaseKeepAlive()
            currentScreen = AppScreen.Scan
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            releaseKeepAlive()
            currentScreen = AppScreen.Scan
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseKeepAlive()
        webSocketClient?.disconnect()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (currentScreen == AppScreen.Control) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                        webSocketClient?.sendKey("volumeup")
                    }
                    return true
                }
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                        webSocketClient?.sendKey("volumedown")
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
