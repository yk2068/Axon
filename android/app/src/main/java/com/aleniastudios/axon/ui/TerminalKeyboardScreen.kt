package com.aleniastudios.axon.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aleniastudios.axon.network.InputClient
import com.aleniastudios.axon.R
import com.aleniastudios.axon.theme.AxonColors
private data class SpecialKey(val label: String, val key: String)
private val specialKeyRow = listOf(
    SpecialKey("ESC", "Escape"),
    SpecialKey("TAB", "Tab"),
    SpecialKey("CTRL", "__mod_ctrl"),
    SpecialKey("ALT", "__mod_alt"),
    SpecialKey("HOME", "Home"),
    SpecialKey("END", "End"),
    SpecialKey("PgUp", "Prior"),
    SpecialKey("PgDn", "Next"),
)
private val quickActions = listOf(
    SpecialKey("↑", "Up"),
    SpecialKey("↓", "Down"),
    SpecialKey("←", "Left"),
    SpecialKey("→", "Right"),
)
private val systemKeyRow = listOf(
    SpecialKey("Vol+", "volumeup"),
    SpecialKey("Vol-", "volumedown"),
    SpecialKey("Mute", "volumemute"),
    SpecialKey("Br+", "brightnessup"),
    SpecialKey("Br-", "brightnessdown"),
    SpecialKey("⏯", "playpause"),
    SpecialKey("⏮", "prevtrack"),
    SpecialKey("⏭", "nexttrack"),
    SpecialKey("PrtSc", "printscreen")
)
@Composable
fun TerminalKeyboardScreen(client: InputClient, onDisconnect: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val history = remember { mutableStateListOf<String>() }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    val historyListState = rememberLazyListState()
    var textState by remember { mutableStateOf("") }
    fun sendKey(key: String) {
        val logKey = when (key) {
            "__mod_ctrl" -> if (!ctrlActive) "[CTRL ON]" else "[CTRL OFF]"
            "__mod_alt"  -> if (!altActive)  "[ALT ON]"  else "[ALT OFF]"
            else -> "[$key]"
        }
        if (key != "__mod_ctrl" && key != "__mod_alt") history.add(logKey)
        when {
            key.startsWith("__combo_") -> {
                val parts = key.removePrefix("__combo_").split("_")
                if (parts.size == 2) client.sendKeyCombo(parts[0], parts[1])
            }
            key == "__mod_ctrl" -> { ctrlActive = !ctrlActive; altActive = false }
            key == "__mod_alt"  -> { altActive = !altActive; ctrlActive = false }
            ctrlActive -> { client.sendKeyCombo("ctrl", key.lowercase()); ctrlActive = false }
            altActive  -> { client.sendKeyCombo("alt", key.lowercase()); altActive = false }
            else -> client.sendKey(key)
        }
    }
    BasicTextField(
        value = textState,
        onValueChange = { newText ->
            if (newText.endsWith("\n")) {
                val added = if (newText.length > textState.length + 1) {
                    newText.substring(textState.length, newText.length - 1)
                } else ""
                if (added.isNotEmpty()) {
                    if (added.length == 1 && (ctrlActive || altActive)) {
                        sendKey(added)
                    } else {
                        client.sendType(added)
                        history.add(textState + added)
                    }
                } else if (textState.isNotEmpty()) {
                    history.add(textState)
                }
                sendKey("Return")
                textState = ""
            } else if (newText.length > textState.length) {
                val addedText = newText.substring(textState.length)
                if (addedText.length == 1 && (ctrlActive || altActive)) {
                    sendKey(addedText)
                } else {
                    client.sendType(addedText)
                    textState = newText
                }
            } else if (newText.length < textState.length) {
                sendKey("BackSpace")
                textState = newText
            }
        },
        modifier = Modifier
            .size(0.dp)
            .focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Default,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password
        )
    )
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus(); keyboardController?.show() } catch (_: Exception) {}
    }
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) historyListState.animateScrollToItem(history.size - 1)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AxonColors.BgGradStart, AxonColors.BgGradEnd)
                )
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(id = R.string.terminal_title),
                color = AxonColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AxonColors.RedAccent.copy(alpha = 0.1f))
                    .border(1.dp, AxonColors.RedAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable { onDisconnect() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(id = R.string.exit_action), color = AxonColors.RedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, AxonColors.BorderColor, RoundedCornerShape(16.dp))
                .clickable {
                    focusManager.clearFocus()
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
                .padding(14.dp)
        ) {
            LazyColumn(state = historyListState, modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        stringResource(id = R.string.terminal_bridge_msg, client.getServerIp()),
                        color = AxonColors.TextSecond.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
                items(history) { log ->
                    Text(
                        text = log,
                        color = if (log.startsWith("key:") || log.startsWith("[")) AxonColors.TextSecond else AxonColors.GreenAccent,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("axon:~$ ", color = AxonColors.BlueAccent, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(textState, color = AxonColors.GreenAccent, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text("█", color = AxonColors.BlueAccent.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            specialKeyRow.forEach { sk ->
                val isModActive = (sk.key == "__mod_ctrl" && ctrlActive) || (sk.key == "__mod_alt" && altActive)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isModActive) {
                                Brush.horizontalGradient(colors = listOf(AxonColors.BlueAccent, AxonColors.VioletAccent))
                            } else {
                                Brush.horizontalGradient(colors = listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f)))
                            }
                        )
                        .border(1.dp, if (isModActive) Color.Transparent else AxonColors.BorderColor, RoundedCornerShape(10.dp))
                        .clickable { sendKey(sk.key) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        sk.label,
                        color = if (isModActive) Color.White else AxonColors.TextSecond,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickActions.forEach { qa ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AxonColors.BlueAccent.copy(alpha = 0.1f))
                        .border(1.dp, AxonColors.BlueAccent.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .clickable { sendKey(qa.key) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        qa.label,
                        color = AxonColors.BlueAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            systemKeyRow.forEach { sk ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, AxonColors.BorderColor, RoundedCornerShape(10.dp))
                        .clickable { sendKey(sk.key) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        sk.label,
                        color = AxonColors.TextSecond,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, AxonColors.BorderColor, RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(id = R.string.tap_to_activate),
                color = AxonColors.TextSecond,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
