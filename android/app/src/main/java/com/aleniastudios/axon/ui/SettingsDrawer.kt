package com.aleniastudios.axon.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aleniastudios.axon.R
import com.aleniastudios.axon.theme.AxonColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawer(
    isAirMouse: Boolean,
    onAirMouseToggle: (Boolean) -> Unit,
    airMouseSensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AxonColors.BgGradStart, // Or a solid dark color
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 8.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Settings",
                color = AxonColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // AirMouse Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "AirMouse Mode",
                        color = AxonColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Use gyroscope as cursor",
                        color = AxonColors.TextSecond,
                        fontSize = 14.sp
                    )
                }
                Switch(
                    checked = isAirMouse,
                    onCheckedChange = { onAirMouseToggle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AxonColors.BlueAccent,
                        checkedTrackColor = AxonColors.BlueAccent.copy(alpha = 0.5f),
                        uncheckedThumbColor = AxonColors.TextSecond,
                        uncheckedTrackColor = AxonColors.BorderColor
                    )
                )
            }

            if (isAirMouse) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sensitivity",
                            color = AxonColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${airMouseSensitivity.toInt()}",
                            color = AxonColors.TextSecond,
                            fontSize = 14.sp
                        )
                    }
                    Slider(
                        value = airMouseSensitivity,
                        onValueChange = onSensitivityChange,
                        valueRange = 100f..3000f,
                        colors = SliderDefaults.colors(
                            thumbColor = AxonColors.BlueAccent,
                            activeTrackColor = AxonColors.BlueAccent,
                            inactiveTrackColor = AxonColors.BorderColor
                        )
                    )
                }
            }

            HorizontalDivider(color = AxonColors.BorderColor, modifier = Modifier.padding(vertical = 8.dp))

            // About
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/"))
                        context.startActivity(intent)
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "About",
                        color = AxonColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Axon v2.0",
                        color = AxonColors.TextSecond,
                        fontSize = 14.sp
                    )
                }
            }

            // Support
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:contact.aleniastudios@gmail.com"))
                        context.startActivity(intent)
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Support",
                        color = AxonColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Contact Alenia Studios",
                        color = AxonColors.TextSecond,
                        fontSize = 14.sp
                    )
                }
            }

            HorizontalDivider(color = AxonColors.BorderColor, modifier = Modifier.padding(vertical = 8.dp))

            // Disconnect
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onDisconnect()
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.disconnect_action),
                    color = AxonColors.RedAccent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
