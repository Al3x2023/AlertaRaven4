package com.example.alertaraven4.ml

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.alertaraven4.api.ApiClient
import com.example.alertaraven4.api.models.ApiResult
import com.example.alertaraven4.api.models.SensorEventRequest
import com.example.alertaraven4.api.models.SensorEventResponse
import com.example.alertaraven4.data.AccidentType
import com.example.alertaraven4.data.SensorData
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Predictor híbrido que combina el predictor local (AccidentMLPredictor)
 * con la inferencia del modelo de la API para retroalimentación sincrónica
 * y fallback offline.
 */
class HybridAccidentPredictor(private val context: Context) {

    companion object {
        private const val TAG = "HybridAccidentPredictor"
    }

    private val mlPredictor = AccidentMLPredictor()

    /**
     * Realiza predicción local y, si es posible, consulta la API para obtener etiqueta remota.
     * Devuelve un par con (tipo remoto si disponible, respuesta cruda).
     */
    suspend fun getRemoteType(
        currentData: SensorData,
        recentData: List<SensorData>
    ): AccidentType? {
        return try {
            // Predicción local para enviar como metadata de entrenamiento
            val localPrediction = mlPredictor.predictAccidentProbability(currentData, recentData)
            val predictedLabel = localPrediction.type.name
            val predictedConfidence = localPrediction.confidence.toDouble()

            val accelMagnitudes = recentData.map { it.getAccelerationMagnitude().toDouble() }
            val gyroMagnitudes = recentData.map { it.getGyroscopeMagnitude().toDouble() }

            val accelMag = currentData.getAccelerationMagnitude().toDouble()
            val gyroMag = currentData.getGyroscopeMagnitude().toDouble()
            val accelVar = variance(accelMagnitudes)
            val gyroVar = variance(gyroMagnitudes)
            val accelJerk = computeJerkFromSeries(recentData.map { it.timestamp.toDouble() to it.getAccelerationMagnitude().toDouble() })

            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"

            val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .format(java.util.Date(currentData.timestamp))

            val request = SensorEventRequest(
                deviceId = deviceId,
                label = null,
                predictedLabel = predictedLabel,
                predictionConfidence = predictedConfidence,
                accelerationMagnitude = accelMag,
                gyroscopeMagnitude = gyroMag,
                accelVariance = accelVar,
                gyroVariance = gyroVar,
                accelJerk = accelJerk,
                timestamp = ts,
                rawData = mapOf(
                    "accel_count" to accelMagnitudes.size,
                    "gyro_count" to gyroMagnitudes.size,
                    "window_ms" to (recentData.lastOrNull()?.timestamp?.minus(recentData.firstOrNull()?.timestamp ?: currentData.timestamp) ?: 0L)
                )
            )

            when (val result = ApiClient.getInstance().sendSensorEvent(request)) {
                is ApiResult.Success<SensorEventResponse> -> {
                    val label = result.data.label
                    val mapped = mapLabelToType(label)
                    Log.d(TAG, "Remoto etiqueta=$label, tipo=$mapped, ok=${result.data.ok}")
                    mapped
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "Error API: ${result.message}")
                    null
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Error de red: ${result.exception.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo consultando API: ${e.message}")
            null
        }
    }

    /**
     * Predicción local con ML, devuelta para combinación externa.
     */
    fun localPredict(
        currentData: SensorData,
        recentData: List<SensorData>
    ) = mlPredictor.predictAccidentProbability(currentData, recentData)

    private fun variance(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }

    private fun computeJerkFromSeries(series: List<Pair<Double, Double>>): Double {
        if (series.size < 2) return 0.0
        // jerk ~ derivada de aceleración: Δa/Δt promedio absoluto
        var total = 0.0
        var count = 0
        for (i in 1 until series.size) {
            val dt = (series[i].first - series[i - 1].first) / 1000.0 // ms -> s
            if (dt <= 0.0) continue
            val da = series[i].second - series[i - 1].second
            total += kotlin.math.abs(da / dt)
            count++
        }
        return if (count == 0) 0.0 else total / count
    }

    private fun mapLabelToType(label: String?): AccidentType? {
        if (label == null) return null
        return when (label.lowercase(Locale.ROOT)) {
            "collision", "colision", "accident_collision" -> AccidentType.COLLISION
            "rollover", "volcadura", "accident_rollover" -> AccidentType.ROLLOVER
            "sudden_stop", "frenado", "accident_sudden_stop" -> AccidentType.SUDDEN_STOP
            "fall", "caida", "accident_fall" -> AccidentType.FALL
            else -> AccidentType.UNKNOWN
        }
    }

    /**
     * Envía una muestra etiquetada para entrenamiento a la API usando la ventana reciente.
     * No influye en la predicción, solo registra datos cuando hay eventos.
     */
    suspend fun sendTrainingSampleLabelled(
        currentData: SensorData,
        recentData: List<SensorData>,
        label: String?
    ) {
        try {
            // Calcular predicción local para adjuntar como predicted_label
            val localPrediction = mlPredictor.predictAccidentProbability(currentData, recentData)
            val predictedLabel = localPrediction.type.name
            val predictedConfidence = localPrediction.confidence.toDouble()

            val accelMagnitudes = recentData.map { it.getAccelerationMagnitude().toDouble() }
            val gyroMagnitudes = recentData.map { it.getGyroscopeMagnitude().toDouble() }

            val accelMag = currentData.getAccelerationMagnitude().toDouble()
            val gyroMag = currentData.getGyroscopeMagnitude().toDouble()
            val accelVar = variance(accelMagnitudes)
            val gyroVar = variance(gyroMagnitudes)
            val accelJerk = computeJerkFromSeries(recentData.map { it.timestamp.toDouble() to it.getAccelerationMagnitude().toDouble() })

            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"

            val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .format(java.util.Date(currentData.timestamp))

            val request = SensorEventRequest(
                deviceId = deviceId,
                label = label,
                predictedLabel = predictedLabel,
                predictionConfidence = predictedConfidence,
                accelerationMagnitude = accelMag,
                gyroscopeMagnitude = gyroMag,
                accelVariance = accelVar,
                gyroVariance = gyroVar,
                accelJerk = accelJerk,
                timestamp = ts,
                rawData = mapOf(
                    "accel_count" to accelMagnitudes.size,
                    "gyro_count" to gyroMagnitudes.size,
                    "window_ms" to (recentData.lastOrNull()?.timestamp?.minus(recentData.firstOrNull()?.timestamp ?: currentData.timestamp) ?: 0L)
                )
            )

            when (val result = ApiClient.getInstance().sendSensorEvent(request)) {
                is ApiResult.Success<SensorEventResponse> -> {
                    Log.d(TAG, "Muestra entrenamiento enviada: ok=${result.data.ok}, id=${result.data.eventId}, label=${result.data.label}")
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "Error API enviando entrenamiento: ${result.message}")
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Error red enviando entrenamiento: ${result.exception.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al enviar muestra entrenamiento: ${e.message}")
        }
    }
}