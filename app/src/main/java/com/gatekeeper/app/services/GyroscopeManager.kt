package com.gatekeeper.app.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.gatekeeper.app.App
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

    private var gyroscope: Sensor? = null

    private val _gyroscopeData = MutableStateFlow(GyroscopeData(0f, 0f))
    val gyroscopeData = _gyroscopeData.asStateFlow()

    init {
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    fun startListening() {
        gyroscope?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            // event.values[0] is rotation around X axis (Roll)
            // event.values[1] is rotation around Y axis (Pitch)
            // We can ignore Z axis for this 2D game.
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
