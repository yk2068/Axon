package com.example.axon

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.axon.network.WebSocketClient
import com.example.axon.service.SessionService
import com.example.axon.theme.AxonTheme
import com.example.axon.ui.QrScannerScreen
import com.example.axon.ui.TouchpadScreen

enum class AppScreen {
    Scan,
    Control
}

/**
 * Single-purpose activity: reopen the last session (or scan a QR code), then act as a touchpad.
 *
 * The last connection URL is persisted, so relaunching reconnects without rescanning. Dropped
 * connections are retried silently with a short backoff instead of kicking back to the scanner.
 * While a session is up the activity holds a high-performance Wi-Fi lock, a partial wake lock and
 * a foreground service, which together keep the WebSocket alive once the screen turns off.
 */
class MainActivity : ComponentActivity(), WebSocketClient.WebSocketConnectionListener {

    private companion object {
        const val PREFS = "axon_prefs"
        const val KEY_LAST_URL = "last_url"
        const val INITIAL_RETRIES = 4
        const val AUTO_RETRIES = 2
    }

    private var webSocketClient: WebSocketClient? = null
    private var currentScreen by mutableStateOf(AppScreen.Scan)
    private var autoConnecting by mutableStateOf(false)

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val handler = Handler(Looper.getMainLooper())
    private var lastUrl: String? = null
    private var retriesLeft = 0
    private var attempt = 0
    private var manualDisconnect = false
    private var dropHandled = false

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
                                if (autoConnecting) {
                                    ConnectingScreen()
                                } else {
                                    QrScannerScreen(
                                        onQrDetected = { url -> beginSession(url, auto = false) }
                                    )
                                }
                            }
                            AppScreen.Control -> {
                                webSocketClient?.let { client ->
                                    TouchpadScreen(
                                        client = client,
                                        onDisconnect = { endSession() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val saved = prefs().getString(KEY_LAST_URL, null)
        if (!saved.isNullOrBlank()) {
            beginSession(saved, auto = true)
        }
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- session lifecycle ----

    private fun beginSession(url: String, auto: Boolean) {
        manualDisconnect = false
        dropHandled = false
        lastUrl = url
        autoConnecting = auto
        attempt = 0
        retriesLeft = if (auto) AUTO_RETRIES else INITIAL_RETRIES
        prefs().edit().putString(KEY_LAST_URL, url).apply()
        openSocket()
    }

    private fun openSocket() {
        val url = lastUrl ?: return
        dropHandled = false
        webSocketClient?.connect(toWebSocketUrl(url))
    }

    private fun toWebSocketUrl(url: String): String = try {
        val uri = java.net.URI(url)
        val query = if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
        "ws://${uri.host}:${uri.port}/ws$query"
    } catch (e: Exception) {
        url.replace("http://", "ws://").replace("/?", "/ws?")
    }

    private fun endSession() {
        manualDisconnect = true
        handler.removeCallbacksAndMessages(null)
        webSocketClient?.disconnect()
        releaseKeepAlive()
        SessionService.stop(this)
        autoConnecting = false
        currentScreen = AppScreen.Scan
    }

    /** A connection dropped on its own: retry with backoff before giving up. */
    private fun handleDrop(message: String) {
        if (manualDisconnect || dropHandled) return
        dropHandled = true

        if (retriesLeft <= 0 || lastUrl == null) {
            autoConnecting = false
            releaseKeepAlive()
            SessionService.stop(this)
            currentScreen = AppScreen.Scan
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            return
        }

        retriesLeft--
        attempt++
        val delay = when (attempt) {
            1 -> 400L
            2 -> 1000L
            3 -> 2000L
            else -> 3000L
        }
        handler.postDelayed({
            if (!manualDisconnect && !isFinishing) openSocket()
        }, delay)
    }

    // ---- listener callbacks ----

    override fun onConnected() {
        runOnUiThread {
            if (manualDisconnect) return@runOnUiThread
            dropHandled = false
            attempt = 0
            retriesLeft = INITIAL_RETRIES
            autoConnecting = false
            acquireKeepAlive()
            SessionService.start(this)
            currentScreen = AppScreen.Control
        }
    }

    override fun onDisconnected() {
        runOnUiThread { handleDrop("") }
    }

    override fun onError(message: String) {
        runOnUiThread { handleDrop(message) }
    }

    // ---- locks ----

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

    override fun onDestroy() {
        super.onDestroy()
        manualDisconnect = true
        handler.removeCallbacksAndMessages(null)
        releaseKeepAlive()
        SessionService.stop(this)
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

@Composable
private fun ConnectingScreen() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = Color(0xFF3B82F6),
                strokeWidth = 2.dp
            )
            Text(text = "Connecting…", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
    }
}
