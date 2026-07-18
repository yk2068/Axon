package com.aleniastudios.axon.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun AirMouseSensor(
    isActive: Boolean,
    sensitivity: Float = 800f,
    onMove: (dx: Double, dy: Double) -> Unit,
    onScroll: (dy: Double) -> Unit
) {
    if (!isActive) return

    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val gyroSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }

    DisposableEffect(sensorManager, gyroSensor) {
        if (gyroSensor == null) {
            return@DisposableEffect onDispose {}
        }

        var lastTimestamp = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    if (lastTimestamp != 0L) {
                        val dt = (event.timestamp - lastTimestamp) / 1_000_000_000.0f
                        
                        val pitchSpeed = event.values[0] // X-axis (Pitch - Up/Down)
                        val rollSpeed = event.values[1]  // Y-axis (Roll)
                        val yawSpeed = event.values[2]   // Z-axis (Yaw)
                        
                        // Use a combination of Roll and Yaw for horizontal movement
                        // This allows the user to hold the phone flat (like a remote) or upright.
                        val horizontalSpeed = yawSpeed + (rollSpeed * 0.5f)
                        
                        // Smaller deadzone
                        val deadzone = 0.02f
                        val pSpeed = if (kotlin.math.abs(pitchSpeed) < deadzone) 0f else pitchSpeed
                        val hSpeed = if (kotlin.math.abs(horizontalSpeed) < deadzone) 0f else horizontalSpeed

                        // Linear movement instead of squared (which felt out of control)
                        val currentSensitivity = sensitivity.toDouble() * 2.0 // Compensate for removing the square
                        
                        // Corrección de ejes
                        val dx = -hSpeed * dt * currentSensitivity
                        val dy = pSpeed * dt * currentSensitivity
                        
                        if (kotlin.math.abs(dx) > 0.1 || kotlin.math.abs(dy) > 0.1) {
                            onMove(dx.toDouble(), dy.toDouble())
                        }
                    }
                    lastTimestamp = event.timestamp
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
}
