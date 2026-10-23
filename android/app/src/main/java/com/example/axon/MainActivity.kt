package com.example.axon

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
 * The connection URL, token included, is persisted. Every return to the foreground forces a
 * fresh socket — a socket that spent time in the background is usually half open while still
 * looking alive, which is why input silently went nowhere. Drops are retried quietly in the
 * background; nothing is ever surfaced as a dialog or toast.
 */
class MainActivity : ComponentActivity(), WebSocketClient.WebSocketConnectionListener {

    private companion object {
        const val PREFS = "axon_prefs"
        const val KEY_LAST_URL = "last_url"

        /** Quiet retries before the scanner is shown again. */
        const val MAX_RETRIES = 6
        const val FIRST_RETRY_MS = 300L
        const val MAX_RETRY_MS = 3000L
    }

    private var webSocketClient: WebSocketClient? = null
    private var currentScreen by mutableStateOf(AppScreen.Scan)
    private var autoConnecting by mutableStateOf(false)
    private var manualDisconnect = false
    private var connected = false
    private var foreground = false
    private var retries = 0
    private var firstStart = true

    private val handler = Handler(Looper.getMainLooper())
    private val retryTick = Runnable { retry() }

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
                                        onDisconnect = {
                                            manualDisconnect = true
                                            connected = false
                                            autoConnecting = false
                                            handler.removeCallbacks(retryTick)
                                            client.disconnect()
                                            releaseKeepAlive()
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

        savedUrl()?.let { beginSession(it, auto = true) }
    }

    private fun savedUrl(): String? {
        val url = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_URL, null)
        return if (url.isNullOrBlank()) null else url
    }

    private fun beginSession(url: String, auto: Boolean) {
        manualDisconnect = false
        retries = 0
        autoConnecting = auto && currentScreen == AppScreen.Scan
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_URL, url).apply()
        webSocketClient?.connect(toWebSocketUrl(url))
    }

    /**
     * Every return to the foreground drops the old socket and opens a new one, without
     * touching what is on screen: the touchpad stays visible and starts working again as
     * soon as the handshake lands.
     */
    override fun onStart() {
        super.onStart()
        foreground = true
        // The very first onStart follows onCreate, which already opened the session.
        if (firstStart) {
            firstStart = false
            return
        }
        if (manualDisconnect) return
        val saved = savedUrl() ?: return
        connected = false
        handler.removeCallbacks(retryTick)
        retries = 0
        autoConnecting = currentScreen == AppScreen.Scan
        webSocketClient?.connect(toWebSocketUrl(saved))
    }

    override fun onStop() {
        super.onStop()
        foreground = false
        handler.removeCallbacks(retryTick)
    }

    private fun toWebSocketUrl(url: String): String = try {
        val uri = java.net.URI(url)
        val query = if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
        "ws://${uri.host}:${uri.port}/ws$query"
    } catch (e: Exception) {
        url.replace("http://", "ws://").replace("/?", "/ws?")
    }

    override fun onConnected() {
        runOnUiThread {
            if (manualDisconnect) return@runOnUiThread
            handler.removeCallbacks(retryTick)
            retries = 0
            connected = true
            autoConnecting = false
            acquireKeepAlive()
            currentScreen = AppScreen.Control
        }
    }

    override fun onDisconnected() {
        runOnUiThread { handleDrop() }
    }

    override fun onError(message: String) {
        // Deliberately unused: a failed socket is retried silently, never reported.
        runOnUiThread { handleDrop() }
    }

    /**
     * A dropped socket is retried in place. The scanner only comes back when the retry
     * budget is spent, so a brief hiccup never throws the user out of the touchpad.
     */
    private fun handleDrop() {
        connected = false
        if (manualDisconnect || !foreground) return
        retry()
    }

    private fun retry() {
        if (manualDisconnect || !foreground || connected) return
        val saved = savedUrl()
        if (saved == null || retries >= MAX_RETRIES) {
            if (saved != null) {
                // Give up quietly and fall back to the scanner.
                autoConnecting = false
                currentScreen = AppScreen.Scan
            }
            return
        }
        retries++
        webSocketClient?.connect(toWebSocketUrl(saved))
        val backoff = (FIRST_RETRY_MS shl (retries - 1)).coerceAtMost(MAX_RETRY_MS)
        handler.removeCallbacks(retryTick)
        handler.postDelayed(retryTick, backoff)
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

    override fun onDestroy() {
        super.onDestroy()
        manualDisconnect = true
        handler.removeCallbacks(retryTick)
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

@Composable
private fun ConnectingScreen() {
    Box(
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
