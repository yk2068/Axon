package com.example.axon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.axon.R
import com.example.axon.network.InputClient
import com.example.axon.network.UdpClient
import kotlin.math.abs
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private val BgGradStart = Color(0xFF0F172A)
private val BgGradEnd = Color(0xFF020617)
private val BlueAccent = Color(0xFF3B82F6)
private val VioletAccent = Color(0xFF8B5CF6)
private val TextPrimary = Color(0xFFF9FAFB)
private val TextSecond = Color(0xFF94A3B8)
private val BorderColor = Color(0xFF334155)
private val RedAccent = Color(0xFFEF4444)

private const val PREFS = "axon_prefs"
private const val KEY_SENSITIVITY = "sensitivity"
private const val DEFAULT_SENSITIVITY = 2.2f
private const val KEY_SCROLL_WIDTH = "scroll_width"
private const val DEFAULT_SCROLL_WIDTH = 56f
private const val KEY_INVERT_SCROLL = "invert_scroll"

/** Finger travel, in dp, that produces one wheel notch at 1x sensitivity. */
private const val SCROLL_PX_PER_NOTCH_AT_1X = 40f

/** Scroll release tick: how often a slice of the pending travel is handed to the link. */
private const val SCROLL_TICK_MS = 10L

/** Fraction of the pending travel released per tick, which evens out finger jitter. */
private const val SCROLL_RELEASE = 0.45f

/** Below this many notches the remainder is flushed at once instead of dribbled. */
private const val SCROLL_MIN_NOTCH = 0.01f

/** Minimum gap between move packets: 8ms keeps motion at ~120Hz without flooding the link. */
private const val MIN_SEND_INTERVAL_MS = 8L

@Composable
fun TouchpadScreen(
    client: InputClient,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }
    var sensitivity by remember {
        mutableStateOf(prefs.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY))
    }
    var scrollWidthDp by remember {
        mutableStateOf(prefs.getFloat(KEY_SCROLL_WIDTH, DEFAULT_SCROLL_WIDTH))
    }
    var invertScroll by remember {
        mutableStateOf(prefs.getBoolean(KEY_INVERT_SCROLL, false))
    }
    var showSettings by remember { mutableStateOf(false) }

    val serverIp = client.getServerIp()
    val isLocalConnection = serverIp == "127.0.0.1" || serverIp == "localhost"
    val isBluetooth = client is com.example.axon.network.BluetoothClient
    val useUdp = !isLocalConnection && !isBluetooth && serverIp.isNotEmpty()
    val udpClient = remember(serverIp, useUdp) {
        if (useUdp) UdpClient(serverIp) else null
    }
    DisposableEffect(udpClient) {
        onDispose { udpClient?.close() }
    }

    val accDx = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val accDy = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val pendingNotches = remember { java.util.concurrent.atomic.AtomicReference(0f) }
    val sendMove = { dx: Double, dy: Double ->
        if (!useUdp || udpClient == null) client.sendMove(dx, dy) else udpClient.sendMove(dx, dy)
    }
    val sendScroll = { dy: Double ->
        if (!useUdp || udpClient == null) client.sendScroll(dy) else udpClient.sendScroll(dy)
    }
    // Scroll is fed as fractional notches (1.0 == one wheel notch) several times per
    // frame instead of whole notches on a 12ms timer, so the far end can emit sub-notch
    // wheel deltas and the content glides. The pending travel is released with a
    // leaky integrator: each tick hands over a fixed share of what is owed, which
    // spreads a fast flick over the following frames while keeping the total distance
    // exactly equal to the finger travel.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(SCROLL_TICK_MS)
            val owed = pendingNotches.get()
            if (owed == 0f) continue
            if (abs(owed) < SCROLL_MIN_NOTCH) {
                pendingNotches.set(0f)
                sendScroll(owed.toDouble())
            } else {
                val released = owed * SCROLL_RELEASE
                pendingNotches.set(owed - released)
                sendScroll(released.toDouble())
            }
        }
    }
    val dragChannel = remember {
        Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
    LaunchedEffect(dragChannel) {
        var lastSend = 0L
        for (signal in dragChannel) {
            // Time-based throttle: wait only for what is left of the frame budget instead
            // of sleeping a fixed 16ms after every send, which added latency on each move.
            val wait = MIN_SEND_INTERVAL_MS - (android.os.SystemClock.uptimeMillis() - lastSend)
            if (wait > 0) kotlinx.coroutines.delay(wait)
            lastSend = android.os.SystemClock.uptimeMillis()
            val dx = java.lang.Double.longBitsToDouble(accDx.getAndSet(0L))
            val dy = java.lang.Double.longBitsToDouble(accDy.getAndSet(0L))
            if (dx != 0.0 || dy != 0.0) sendMove(dx, dy)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    val tapScale = remember { Animatable(0f) }
    var tapPos by remember { mutableStateOf<Offset?>(null) }
    var scrollOffset by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(BgGradStart, BgGradEnd)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 6.dp)
                .padding(top = 6.dp, bottom = 6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // ---- main touch surface ----
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                        .pointerInput(isLocalConnection) {
                            var lastTapTime = 0L
                            var lastTwoFingerTap = 0L
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downTime = System.currentTimeMillis()
                                val isDoubleTap = (downTime - lastTapTime) < 300
                                var isDraggingClick = false
                                touchPos = down.position
                                accDx.set(0L)
                                accDy.set(0L)
                                var dragPointerId = down.id
                                val startTime = System.currentTimeMillis()
                                var isDrag = false
                                var totalDist = 0f
                                var lastZoomDist: Float? = null
                                var maxPointers = 1
                                var twoFingerMoved = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val anyPressed = event.changes.any { it.pressed }
                                    if (!anyPressed) {
                                        touchPos = null
                                        val dx = java.lang.Double.longBitsToDouble(accDx.getAndSet(0L))
                                        val dy = java.lang.Double.longBitsToDouble(accDy.getAndSet(0L))
                                        if (dx != 0.0 || dy != 0.0) sendMove(dx, dy)
                                        val duration = System.currentTimeMillis() - startTime
                                        val twoFingerTap = maxPointers >= 2 && !isDrag &&
                                            duration < 300 && twoFingerMoved < 40f
                                        if (twoFingerTap) {
                                            // Two-finger double tap opens / closes the settings panel;
                                            // there is no on-screen button any more.
                                            val now = System.currentTimeMillis()
                                            if (now - lastTwoFingerTap < 500) {
                                                showSettings = !showSettings
                                                lastTwoFingerTap = 0L
                                            } else {
                                                lastTwoFingerTap = now
                                            }
                                        } else if (isDraggingClick) {
                                            client.sendMouseUp("left")
                                        } else if (maxPointers < 2 && !isDrag && duration < 200 && totalDist < 15f) {
                                            client.sendClick("left")
                                            lastTapTime = System.currentTimeMillis()
                                            tapPos = down.position
                                            coroutineScope.launch {
                                                tapScale.snapTo(0f)
                                                tapScale.animateTo(
                                                    1f,
                                                    tween(280, easing = FastOutSlowInEasing)
                                                )
                                                tapPos = null
                                            }
                                        }
                                        break
                                    }
                                    val pressedChanges = event.changes.filter { it.pressed }
                                    maxPointers = maxOf(maxPointers, pressedChanges.size)
                                    if (pressedChanges.size >= 2) {
                                        pressedChanges.forEach {
                                            val d = it.position - it.previousPosition
                                            twoFingerMoved += kotlin.math.sqrt(d.x * d.x + d.y * d.y)
                                        }
                                    }
                                    if (pressedChanges.size == 2) {
                                        val p1 = pressedChanges[0].position
                                        val p2 = pressedChanges[1].position
                                        val dist = kotlin.math.sqrt(
                                            (p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y)
                                        )
                                        if (lastZoomDist != null) {
                                            val delta = dist - lastZoomDist!!
                                            if (abs(delta) > 15f) {
                                                if (delta > 0) {
                                                    client.sendKeyCombo("ctrl", "+")
                                                } else {
                                                    client.sendKeyCombo("ctrl", "-")
                                                }
                                                lastZoomDist = dist
                                            }
                                        } else {
                                            lastZoomDist = dist
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else {
                                        lastZoomDist = null
                                    }
                                    val change = event.changes.firstOrNull { it.id == dragPointerId && it.pressed }
                                        ?: event.changes.firstOrNull { it.pressed }
                                    if (change != null) {
                                        dragPointerId = change.id
                                        touchPos = change.position
                                        val dragAmount = change.position - change.previousPosition
                                        change.consume()
                                        if (pressedChanges.size == 1) {
                                            val dist = kotlin.math.sqrt(
                                                dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y
                                            )
                                            totalDist += dist
                                            if (totalDist > 8f) {
                                                isDrag = true
                                                if (isDoubleTap && !isDraggingClick) {
                                                    isDraggingClick = true
                                                    client.sendMouseDown("left")
                                                }
                                            }
                                            val gain = sensitivity.toDouble()
                                            val prevDx = java.lang.Double.longBitsToDouble(accDx.get())
                                            val prevDy = java.lang.Double.longBitsToDouble(accDy.get())
                                            accDx.set(java.lang.Double.doubleToRawLongBits(prevDx + dragAmount.x.toDouble() * gain))
                                            accDy.set(java.lang.Double.doubleToRawLongBits(prevDy + dragAmount.y.toDouble() * gain))
                                            dragChannel.trySend(Unit)
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        touchPos?.let { pos ->
                            drawCircle(
                                color = BlueAccent.copy(alpha = 0.15f),
                                radius = 48.dp.toPx(),
                                center = pos
                            )
                            drawCircle(
                                color = BlueAccent.copy(alpha = 0.6f),
                                radius = 5.dp.toPx(),
                                center = pos
                            )
                        }
                        tapPos?.let { pos ->
                            drawCircle(
                                color = BlueAccent.copy(alpha = 0.3f * (1f - tapScale.value)),
                                radius = 38.dp.toPx() * tapScale.value,
                                center = pos,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "⬡", color = TextSecond.copy(alpha = 0.12f), fontSize = 32.sp)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ---- scroll strip: speed follows the sensitivity slider ----
                Box(
                    modifier = Modifier
                        .width(scrollWidthDp.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                pendingNotches.set(0f)
                                var scrollPointerId = down.id
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val anyPressed = event.changes.any { it.pressed }
                                    if (!anyPressed) break
                                    val change = event.changes.firstOrNull { it.id == scrollPointerId && it.pressed }
                                        ?: event.changes.firstOrNull { it.pressed }
                                    if (change != null) {
                                        scrollPointerId = change.id
                                        val dragAmount = change.position - change.previousPosition
                                        change.consume()
                                        val sign = if (invertScroll) -1f else 1f
                                        scrollOffset += dragAmount.y * sign
                                        // Higher sensitivity => less finger travel per wheel notch,
                                        // so the same swipe scrolls proportionally further.
                                        val pxPerNotch = (SCROLL_PX_PER_NOTCH_AT_1X / sensitivity)
                                            .coerceIn(6f, 60f)
                                        val prev = pendingNotches.get()
                                        pendingNotches.set(prev + dragAmount.y * sign / pxPerNotch)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val step = 22.dp.toPx()
                        var currentY = scrollOffset % step
                        while (currentY < size.height + step) {
                            drawLine(
                                color = BorderColor.copy(alpha = 0.3f),
                                start = Offset(10.dp.toPx(), currentY),
                                end = Offset(size.width - 10.dp.toPx(), currentY),
                                strokeWidth = 1f
                            )
                            currentY += step
                        }
                    }
                    Text(
                        text = stringResource(id = R.string.scroll_label),
                        color = TextSecond.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.rotate(90f)
                    )
                }
            }
        }

        // ---- settings panel, opened by a two-finger double tap ----
        if (showSettings) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 10.dp, end = 12.dp)
                    .width(250.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xF20F172A))
                    .border(1.dp, BorderColor, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.sensitivity_label),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("%.1f×", sensitivity),
                        color = BlueAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = sensitivity,
                    onValueChange = {
                        sensitivity = it
                        prefs.edit().putFloat(KEY_SENSITIVITY, it).apply()
                    },
                    valueRange = 0.8f..5.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = BlueAccent,
                        activeTrackColor = BlueAccent,
                        inactiveTrackColor = BorderColor
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.scroll_width_label),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${scrollWidthDp.toInt()}dp",
                        color = BlueAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = scrollWidthDp,
                    onValueChange = {
                        scrollWidthDp = it
                        prefs.edit().putFloat(KEY_SCROLL_WIDTH, it).apply()
                    },
                    valueRange = 36f..110f,
                    colors = SliderDefaults.colors(
                        thumbColor = BlueAccent,
                        activeTrackColor = BlueAccent,
                        inactiveTrackColor = BorderColor
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.invert_scroll_label),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = invertScroll,
                        onCheckedChange = {
                            invertScroll = it
                            prefs.edit().putBoolean(KEY_INVERT_SCROLL, it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BlueAccent,
                            uncheckedThumbColor = TextSecond,
                            uncheckedTrackColor = BorderColor
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = serverIp,
                    color = TextSecond,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RedAccent.copy(alpha = 0.12f))
                        .border(1.dp, RedAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable { onDisconnect() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.disconnect_action),
                        color = RedAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
