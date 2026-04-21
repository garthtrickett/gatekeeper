package com.aegisgatekeeper.app.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.aegisgatekeeper.app.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GyroscopeData(
    val pitch: Float,
    val roll: Float,
)

object GyroscopeManager : SensorEventListener {
    private val sensorManager: SensorManager by lazy {
        App.instance.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private var sensor: Sensor? = null

    private val _gyroscopeData = MutableStateFlow(GyroscopeData(0f, 0f))
    val gyroscopeData = _gyroscopeData.asStateFlow()

    init {
        // Gravity acts like a physical slope, much better for balancing than a Gyroscope. Fallback to Accelerometer.
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    fun startListening() {
        sensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val type = event?.sensor?.type
        if (type == Sensor.TYPE_GRAVITY || type == Sensor.TYPE_ACCELEROMETER) {
            // event.values[0] is X axis (lateral tilt)
            // event.values[1] is Y axis (longitudinal tilt)
            _gyroscopeData.value = GyroscopeData(pitch = event.values[1], roll = event.values[0])
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) {
        // Not needed for this use case
    }
}
