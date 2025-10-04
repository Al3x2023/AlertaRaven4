package com.example.alertaraven4.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import android.util.Log
import com.example.alertaraven4.api.ApiClient
import com.example.alertaraven4.api.models.SensorEventRequest
import com.example.alertaraven4.api.models.SensorEventResponse
import com.example.alertaraven4.api.models.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Reporter de eventos de sensores para enviar ventanas de datos a la API
 * Calcula magnitudes, varianzas y jerk en tiempo real.
 */
class SensorEventReporter(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "SensorEventReporter"
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val scope = CoroutineScope(Dispatchers.IO)

    private val accelMagnitudes = mutableListOf<Pair<Long, Double>>()
    private val gyroMagnitudes = mutableListOf<Pair<Long, Double>>()

    private var reportingJob: Job? = null
    private var windowMs: Long = 2000
    private var sendIntervalMs: Long = 2000
    private var isRunning = false

    fun start(windowMs: Long = 2000, sendIntervalMs: Long = 2000) {
        if (isRunning) return
        this.windowMs = windowMs
        this.sendIntervalMs = sendIntervalMs

        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (accel == null || gyro == null) {
            Log.w(TAG, "Acelerómetro o giroscopio no disponibles")
            return
        }

        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
        isRunning = true

        reportingJob = scope.launch {
            while (isRunning) {
                try {
                    sendWindowIfReady()
                } catch (e: Exception) {
                    Log.e(TAG, "Error preparando envío de ventana", e)
                }
                delay(sendIntervalMs)
            }
        }
        Log.i(TAG, "SensorEventReporter iniciado")
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        reportingJob?.cancel()
        reportingJob = null
        sensorManager.unregisterListener(this)
        accelMagnitudes.clear()
        gyroMagnitudes.clear()
        Log.i(TAG, "SensorEventReporter detenido")
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val mag = magnitude(event.values)
                synchronized(accelMagnitudes) {
                    accelMagnitudes.add(now to mag)
                    purgeOld(accelMagnitudes, now - windowMs)
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val mag = magnitude(event.values)
                synchronized(gyroMagnitudes) {
                    gyroMagnitudes.add(now to mag)
                    purgeOld(gyroMagnitudes, now - windowMs)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun magnitude(values: FloatArray): Double {
        val x = values.getOrNull(0) ?: 0f
        val y = values.getOrNull(1) ?: 0f
        val z = values.getOrNull(2) ?: 0f
        return sqrt((x * x + y * y + z * z).toDouble())
    }

    private fun purgeOld(list: MutableList<Pair<Long, Double>>, minTs: Long) {
        while (list.isNotEmpty() && list.first().first < minTs) {
            list.removeAt(0)
        }
    }

    private suspend fun sendWindowIfReady() {
        val now = System.currentTimeMillis()

        val accelWindow = synchronized(accelMagnitudes) { accelMagnitudes.toList() }
        val gyroWindow = synchronized(gyroMagnitudes) { gyroMagnitudes.toList() }

        if (accelWindow.isEmpty() || gyroWindow.isEmpty()) return

        val accelMag = accelWindow.last().second
        val gyroMag = gyroWindow.last().second

        val accelVar = variance(accelWindow.map { it.second })
        val gyroVar = variance(gyroWindow.map { it.second })

        val accelJerk = computeJerk(accelWindow)

        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"

        val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(java.util.Date(now))

        val request = SensorEventRequest(
            deviceId = deviceId,
            label = null,
            accelerationMagnitude = accelMag,
            gyroscopeMagnitude = gyroMag,
            accelVariance = accelVar,
            gyroVariance = gyroVar,
            accelJerk = accelJerk,
            timestamp = ts,
            rawData = mapOf(
                "accel_count" to accelWindow.size,
                "gyro_count" to gyroWindow.size,
                "window_ms" to windowMs
            )
        )

        when (val result = ApiClient.getInstance().sendSensorEvent(request)) {
            is ApiResult.Success<SensorEventResponse> -> {
                Log.d(TAG, "Sensor event enviado: ok=${result.data.ok}, id=${result.data.eventId}")
            }
            is ApiResult.Error<*> -> {
                Log.w(TAG, "Error API enviando sensor event: ${result.message}")
            }
            is ApiResult.NetworkError<*> -> {
                Log.w(TAG, "Error de red enviando sensor event: ${result.exception.message}")
            }
        }
    }

    private fun variance(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.sum() / values.size
        var sumSq = 0.0
        for (v in values) {
            val d = v - mean
            sumSq += d * d
        }
        return sumSq / values.size
    }

    private fun computeJerk(window: List<Pair<Long, Double>>): Double {
        if (window.size < 2) return 0.0
        var sum = 0.0
        var count = 0
        for (i in 1 until window.size) {
            val dtMs = (window[i].first - window[i - 1].first).coerceAtLeast(1)
            val dtSec = dtMs / 1000.0
            val diff = abs(window[i].second - window[i - 1].second)
            sum += diff / dtSec
            count++
        }
        return if (count > 0) sum / count else 0.0
    }
}