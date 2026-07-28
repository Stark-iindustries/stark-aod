package com.starkiindustries.aod

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Reads TYPE_STEP_COUNTER (cumulative since boot) and converts it to
 * today's step count by recording a baseline on first read.
 */
class StepHelper(
    context: Context,
    private val onUpdate: (Long) -> Unit
) {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var baseline = -1L

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val total = event.values[0].toLong()
            if (baseline < 0L) baseline = total
            onUpdate((total - baseline).coerceAtLeast(0L))
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    fun start() {
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sm.unregisterListener(listener)
    }
}
