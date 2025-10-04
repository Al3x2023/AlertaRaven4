package com.example.alertaraven4.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.util.Log
import com.example.alertaraven4.data.AccidentEvent
import com.example.alertaraven4.data.AccidentThresholds
import com.example.alertaraven4.data.AccidentType
import com.example.alertaraven4.data.SensorData
import com.example.alertaraven4.ml.AccidentMLPredictor
import com.example.alertaraven4.ml.HybridAccidentPredictor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detector de accidentes que utiliza acelerómetro y giroscopio
 * para identificar posibles accidentes vehiculares
 */
class AccidentDetector(
    private val context: Context,
    private val thresholds: AccidentThresholds = AccidentThresholds()
) : SensorEventListener {

    companion object {
        private const val TAG = "AccidentDetector"
        private const val GRAVITY = 9.81f
        // Aumentar throttle para reducir tráfico y estrechar rango intermedio
        private const val REMOTE_QUERY_THROTTLE_MS = 10000L
        private const val REMOTE_EPISODE_COOLDOWN_MS = 15000L
        private const val INTERMEDIATE_CONF_MIN = 0.45f
        private const val INTERMEDIATE_CONF_MAX = 0.70f
    }

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    
    // Cola para almacenar datos recientes de sensores (optimizada)
    private val sensorDataQueue = ConcurrentLinkedQueue<SensorData>()
    private val maxQueueSize get() = thresholds.dataQueueSize
    
    // Datos actuales de sensores con filtros
    private var currentAcceleration = FloatArray(3)
    private var currentGyroscope = FloatArray(3)
    private var lastAcceleration = FloatArray(3)
    private var filteredAcceleration = FloatArray(3)
    private var filteredGyroscope = FloatArray(3)
    
    // Calibración automática
    private var calibrationData = mutableListOf<FloatArray>()
    private var isCalibrated = false
    private var baselineAcceleration = FloatArray(3)
    private var baselineGyroscope = FloatArray(3)
    
    // Filtros de ruido
    private val accelerationHistory = Array(5) { FloatArray(3) }
    private val gyroscopeHistory = Array(5) { FloatArray(3) }
    private var historyIndex = 0
    
    // Sistema de Machine Learning
    private val mlPredictor = AccidentMLPredictor()
    private val hybridPredictor = HybridAccidentPredictor(context)

    // Control de frecuencia para consultas al modelo remoto
    private var lastRemoteQueryAt: Long = 0L
    private var lastRemoteCandidateType: AccidentType? = null
    private var lastRemoteCandidateAt: Long = 0L
    
    // Estado del detector
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring
    
    private val _accidentDetected = MutableStateFlow<AccidentEvent?>(null)
    val accidentDetected: StateFlow<AccidentEvent?> = _accidentDetected
    
    private val _currentSensorData = MutableStateFlow<SensorData?>(null)
    val currentSensorData: StateFlow<SensorData?> = _currentSensorData
    
    private var currentLocation: Location? = null
    
    /**
     * Inicia el monitoreo de sensores
     */
    fun startMonitoring(): Boolean {
        if (_isMonitoring.value) {
            Log.w(TAG, "El monitoreo ya está activo")
            return true
        }
        
        // Reiniciar calibración
        resetCalibration()
        
        // Usar frecuencia más alta para mejor tiempo de respuesta
        val samplingRate = SensorManager.SENSOR_DELAY_FASTEST // ~200Hz para máxima responsividad
        
        val accelerometerRegistered = accelerometer?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                samplingRate
            )
        } ?: false
        
        val gyroscopeRegistered = gyroscope?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                samplingRate
            )
        } ?: false
        
        if (accelerometerRegistered) {
            _isMonitoring.value = true
            val gyroStatus = if (gyroscopeRegistered) "con giroscopio" else "solo con acelerómetro"
            Log.i(TAG, "Monitoreo de sensores iniciado $gyroStatus")
            return true
        } else {
            Log.e(TAG, "No se pudo registrar el acelerómetro")
            return false
        }
    }
    
    /**
     * Detiene el monitoreo de sensores
     */
    fun stopMonitoring() {
        if (!_isMonitoring.value) {
            return
        }
        
        sensorManager.unregisterListener(this)
        _isMonitoring.value = false
        sensorDataQueue.clear()
        Log.i(TAG, "Monitoreo de sensores detenido")
    }
    
    /**
     * Actualiza la ubicación actual
     */
    fun updateLocation(location: Location) {
        currentLocation = location
    }
    
    /**
     * Reinicia la calibración
     */
    private fun resetCalibration() {
        calibrationData.clear()
        isCalibrated = false
        baselineAcceleration.fill(0f)
        baselineGyroscope.fill(0f)
        historyIndex = 0
        
        // Limpiar historial
        accelerationHistory.forEach { it.fill(0f) }
        gyroscopeHistory.forEach { it.fill(0f) }
    }
    
    /**
     * Procesa datos de calibración
     */
    private fun processCalibration(acceleration: FloatArray, gyroscope: FloatArray) {
        if (isCalibrated) return
        
        calibrationData.add(acceleration.clone())
        
        if (calibrationData.size >= thresholds.calibrationSamples) {
            // Calcular baseline promedio
            for (i in 0..2) {
                baselineAcceleration[i] = calibrationData.map { it[i] }.average().toFloat()
            }
            baselineGyroscope[0] = gyroscope[0]
            baselineGyroscope[1] = gyroscope[1] 
            baselineGyroscope[2] = gyroscope[2]
            
            isCalibrated = true
            Log.i(TAG, "Calibración completada. Baseline: [${baselineAcceleration.joinToString()}]")
        }
    }
    
    /**
     * Aplica filtro de media móvil para reducir ruido
     */
    private fun applyNoiseFilter(acceleration: FloatArray, gyroscope: FloatArray) {
        // Actualizar historial
        acceleration.copyInto(accelerationHistory[historyIndex])
        gyroscope.copyInto(gyroscopeHistory[historyIndex])
        historyIndex = (historyIndex + 1) % accelerationHistory.size
        
        // Calcular media móvil
        for (i in 0..2) {
            filteredAcceleration[i] = accelerationHistory.map { it[i] }.average().toFloat()
            filteredGyroscope[i] = gyroscopeHistory.map { it[i] }.average().toFloat()
        }
        
        // Aplicar filtro de ruido
        for (i in 0..2) {
            if (kotlin.math.abs(filteredAcceleration[i] - baselineAcceleration[i]) < thresholds.noiseThreshold) {
                filteredAcceleration[i] = baselineAcceleration[i]
            }
            if (kotlin.math.abs(filteredGyroscope[i] - baselineGyroscope[i]) < thresholds.noiseThreshold) {
                filteredGyroscope[i] = baselineGyroscope[i]
            }
        }
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Filtrar la gravedad para obtener aceleración lineal con mejor filtro
                val alpha = 0.9f // Filtro más agresivo para mejor estabilidad
                currentAcceleration[0] = alpha * currentAcceleration[0] + (1 - alpha) * event.values[0]
                currentAcceleration[1] = alpha * currentAcceleration[1] + (1 - alpha) * event.values[1]
                currentAcceleration[2] = alpha * currentAcceleration[2] + (1 - alpha) * event.values[2]
                
                // Calcular aceleración lineal (sin gravedad)
                val linearAcceleration = FloatArray(3)
                linearAcceleration[0] = event.values[0] - currentAcceleration[0]
                linearAcceleration[1] = event.values[1] - currentAcceleration[1]
                linearAcceleration[2] = event.values[2] - currentAcceleration[2]
                
                // Procesar calibración si es necesario
                if (!isCalibrated) {
                    processCalibration(linearAcceleration, currentGyroscope)
                    return // No procesar datos hasta completar calibración
                }
                
                // Aplicar filtros de ruido
                applyNoiseFilter(linearAcceleration, currentGyroscope)
                
                // Procesar datos filtrados
                processSensorData(filteredAcceleration, filteredGyroscope)
            }
            
            Sensor.TYPE_GYROSCOPE -> {
                currentGyroscope[0] = event.values[0]
                currentGyroscope[1] = event.values[1]
                currentGyroscope[2] = event.values[2]
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Precisión del sensor ${sensor?.name} cambió a: $accuracy")
    }
    
    /**
     * Procesa los datos de sensores y detecta posibles accidentes - Optimizado
     */
    private fun processSensorData(acceleration: FloatArray, gyroscope: FloatArray) {
        val sensorData = SensorData(
            accelerometerX = acceleration[0],
            accelerometerY = acceleration[1],
            accelerometerZ = acceleration[2],
            gyroscopeX = gyroscope[0],
            gyroscopeY = gyroscope[1],
            gyroscopeZ = gyroscope[2]
        )
        
        // Optimización: Agregar a la cola de forma más eficiente
        sensorDataQueue.offer(sensorData)
        if (sensorDataQueue.size > maxQueueSize) {
            sensorDataQueue.poll()
        }
        
        // Emitir datos actuales solo si hay observadores
        _currentSensorData.value = sensorData
        
        // Análisis optimizado: Solo analizar cada N muestras para reducir carga computacional
        if (sensorDataQueue.size % 3 == 0) { // Analizar cada 3 muestras (~66Hz efectivo)
            CoroutineScope(Dispatchers.Default).launch {
                analyzeForAccident(sensorData)
            }
        }
    }
    
    /**
     * Analiza los datos del sensor para detectar accidentes
     */
    private suspend fun analyzeForAccident(currentData: SensorData) {
        val accelerationMagnitude = currentData.getAccelerationMagnitude()
        val gyroscopeMagnitude = currentData.getGyroscopeMagnitude()
        
        // Análisis de patrones mejorado
        val recentData = sensorDataQueue.toList().takeLast(10)
        val accelerationTrend = calculateAccelerationTrend(recentData)
        val gyroscopeTrend = calculateGyroscopeTrend(recentData)
        
        // Detectar diferentes tipos de accidentes con mejor precisión
        val accidentType = when {
            // Colisión: alta aceleración súbita con patrón específico
            isCollisionPattern(accelerationMagnitude, accelerationTrend) -> {
                AccidentType.COLLISION
            }
            
            // Volcadura: alta rotación sostenida
            isRolloverPattern(gyroscopeMagnitude, gyroscopeTrend) -> {
                AccidentType.ROLLOVER
            }
            
            // Frenado brusco: desaceleración significativa con patrón
            isSuddenStopPattern(currentData, recentData) -> {
                AccidentType.SUDDEN_STOP
            }
            
            // Caída libre: aceleración muy baja sostenida
            isFallPattern(accelerationMagnitude, recentData) -> {
                AccidentType.FALL
            }
            
            else -> null
        }
        
        accidentType?.let { type ->
            val confidence = calculateEnhancedConfidence(currentData, type, recentData)
            
            if (confidence >= thresholds.minimumConfidence) {
                val accidentEvent = AccidentEvent(
                    accelerationMagnitude = accelerationMagnitude,
                    gyroscopeMagnitude = gyroscopeMagnitude,
                    location = currentLocation,
                    confidence = confidence,
                    type = type
                )
                
                Log.w(TAG, "Accidente detectado: $type con confianza $confidence (análisis mejorado)")
                _accidentDetected.value = accidentEvent
            }
        }
    }
    
    /**
     * Calcula tendencia de aceleración
     */
    private fun calculateAccelerationTrend(data: List<SensorData>): Float {
        if (data.size < 2) return 0f
        return data.zipWithNext { prev, curr ->
            curr.getAccelerationMagnitude() - prev.getAccelerationMagnitude()
        }.average().toFloat()
    }
    
    /**
     * Calcula tendencia de giroscopio
     */
    private fun calculateGyroscopeTrend(data: List<SensorData>): Float {
        if (data.size < 2) return 0f
        return data.zipWithNext { prev, curr ->
            curr.getGyroscopeMagnitude() - prev.getGyroscopeMagnitude()
        }.average().toFloat()
    }
    
    /**
     * Detecta patrón de colisión
     */
    private fun isCollisionPattern(magnitude: Float, trend: Float): Boolean {
        return magnitude > thresholds.accelerationThreshold && 
               trend > 5.0f // Aceleración creciente rápida
    }
    
    /**
     * Detecta patrón de volcadura
     */
    private fun isRolloverPattern(magnitude: Float, trend: Float): Boolean {
        return magnitude > thresholds.gyroscopeThreshold && 
               trend > 2.0f // Rotación sostenida
    }
    
    /**
     * Detecta patrón de frenado brusco mejorado
     */
    private fun isSuddenStopPattern(currentData: SensorData, recentData: List<SensorData>): Boolean {
        if (recentData.size < 5) return false
        
        val accelerationChanges = recentData.zipWithNext { prev, curr ->
            abs(curr.accelerometerY - prev.accelerometerY)
        }
        
        val hasSignificantDeceleration = accelerationChanges.any { it > thresholds.suddenStopThreshold }
        val isConsistent = accelerationChanges.count { it > thresholds.suddenStopThreshold * 0.5f } >= 3
        
        return hasSignificantDeceleration && isConsistent
    }
    
    /**
     * Detecta patrón de caída libre
     */
    private fun isFallPattern(magnitude: Float, recentData: List<SensorData>): Boolean {
        if (recentData.size < 3) return false
        
        val lowAccelerationCount = recentData.count { it.getAccelerationMagnitude() < thresholds.fallThreshold }
        return magnitude < thresholds.fallThreshold && lowAccelerationCount >= 2
    }
    
    /**
     * Calcula confianza mejorada basada en múltiples factores incluyendo ML
     */
    private suspend fun calculateEnhancedConfidence(
        currentData: SensorData,
        type: AccidentType,
        recentData: List<SensorData>
    ): Float {
        val magnitude = currentData.getAccelerationMagnitude()
        val gyroMagnitude = currentData.getGyroscopeMagnitude()
        val accelerationTrend = calculateAccelerationTrend(recentData)
        val gyroTrend = calculateGyroscopeTrend(recentData)
        val patternMatches = countPatternMatches(currentData, type, recentData)
        
        // Predicción de ML (50% del peso)
        val mlPrediction = mlPredictor.predictAccidentProbability(currentData, recentData)
        val mlConfidence = mlPrediction.probabilities[type] ?: 0f
        
        // Método tradicional (50% del peso)
        var traditionalConfidence = 0f
        
        // Factor de magnitud (20% del peso tradicional)
        val magnitudeRatio = magnitude / thresholds.accelerationThreshold
        traditionalConfidence += (magnitudeRatio.coerceAtMost(2.0f) * 0.2f)
        
        // Factor de giroscopio (10% del peso tradicional)
        val gyroRatio = gyroMagnitude / thresholds.gyroscopeThreshold
        traditionalConfidence += (gyroRatio.coerceAtMost(2.0f) * 0.1f)
        
        // Factor de tendencia (10% del peso tradicional)
        val trendFactor = (abs(accelerationTrend) / 10.0f).coerceAtMost(1.0f)
        traditionalConfidence += (trendFactor * 0.1f)
        
        // Factor de patrones detectados (10% del peso tradicional)
        val patternFactor = (patternMatches / 3.0f).coerceAtMost(1.0f)
        traditionalConfidence += (patternFactor * 0.1f)
        
        // Combinar ML y método tradicional
        var finalConfidence = (mlConfidence * 0.5f) + (traditionalConfidence * 0.5f)

        // Consultar remoto solo si la confianza local está en rango intermedio y respetando throttle
        val now = System.currentTimeMillis()
        val isIntermediate = mlConfidence in INTERMEDIATE_CONF_MIN..INTERMEDIATE_CONF_MAX
        val canQuery = (now - lastRemoteQueryAt) >= REMOTE_QUERY_THROTTLE_MS
        val isNewEpisode = (type != lastRemoteCandidateType) || ((now - lastRemoteCandidateAt) >= REMOTE_EPISODE_COOLDOWN_MS)
        val strongCandidate = patternMatches >= 2

        if (isIntermediate && canQuery && isNewEpisode && strongCandidate) {
            try {
                val remoteType = hybridPredictor.getRemoteType(currentData, recentData)
                lastRemoteQueryAt = now
                lastRemoteCandidateType = type
                lastRemoteCandidateAt = now
                remoteType?.let { rType ->
                    if (rType == type) {
                        finalConfidence += 0.2f
                        Log.d(TAG, "Remoto coincide con $type, confianza reforzada")
                    } else if (rType != AccidentType.UNKNOWN) {
                        finalConfidence -= 0.1f
                        Log.d(TAG, "Remoto discrepa ($rType vs $type), confianza ajustada")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo obtener etiqueta remota: ${e.message}")
            }
        } else {
            when {
                !isIntermediate -> Log.d(TAG, "Omito consulta remota: confianza local fuera de rango ($mlConfidence)")
                !canQuery -> Log.d(TAG, "Omito consulta remota por throttle (${now - lastRemoteQueryAt}ms < ${REMOTE_QUERY_THROTTLE_MS}ms)")
                !isNewEpisode -> Log.d(TAG, "Omito consulta remota: mismo episodio para $type dentro de cooldown")
                !strongCandidate -> Log.d(TAG, "Omito consulta remota: candidato débil (patrones=$patternMatches)")
            }
        }

        return finalConfidence.coerceIn(0f, 1f)
    }
    
    /**
     * Cuenta patrones coincidentes para el tipo de accidente
     */
    private fun countPatternMatches(currentData: SensorData, type: AccidentType, recentData: List<SensorData>): Int {
        var matches = 0
        
        when (type) {
            AccidentType.COLLISION -> {
                if (currentData.getAccelerationMagnitude() > thresholds.accelerationThreshold) matches++
                if (calculateAccelerationTrend(recentData) > 5.0f) matches++
            }
            AccidentType.ROLLOVER -> {
                if (currentData.getGyroscopeMagnitude() > thresholds.gyroscopeThreshold) matches++
                if (calculateGyroscopeTrend(recentData) > 2.0f) matches++
            }
            AccidentType.SUDDEN_STOP -> {
                if (isSuddenStopPattern(currentData, recentData)) matches += 2
            }
            AccidentType.FALL -> {
                if (isFallPattern(currentData.getAccelerationMagnitude(), recentData)) matches += 2
            }
            else -> {}
        }
        
        return matches
    }

    /**
     * Devuelve una copia de los datos recientes de sensores para muestreo/entrenamiento.
     */
    fun getRecentSensorData(limit: Int = 50): List<SensorData> {
        return sensorDataQueue.toList().takeLast(limit)
    }
    
    /**
     * Calcula el nivel de confianza de la detección
     */
    private fun calculateConfidence(data: SensorData, type: AccidentType): Float {
        val accelerationMagnitude = data.getAccelerationMagnitude()
        val gyroscopeMagnitude = data.getGyroscopeMagnitude()
        
        return when (type) {
            AccidentType.COLLISION -> {
                // Confianza basada en qué tan por encima del umbral está la aceleración
                val ratio = accelerationMagnitude / thresholds.accelerationThreshold
                (ratio - 1.0f).coerceIn(0.0f, 1.0f)
            }
            
            AccidentType.ROLLOVER -> {
                val ratio = gyroscopeMagnitude / thresholds.gyroscopeThreshold
                (ratio - 1.0f).coerceIn(0.0f, 1.0f)
            }
            
            AccidentType.SUDDEN_STOP -> {
                // Analizar consistencia del patrón
                0.8f // Valor fijo por ahora, se puede mejorar
            }
            
            AccidentType.FALL -> {
                // Confianza inversa para caída libre
                val ratio = thresholds.fallThreshold / accelerationMagnitude
                (ratio - 1.0f).coerceIn(0.0f, 1.0f)
            }
            
            AccidentType.UNKNOWN -> 0.5f
        }
    }
    
    /**
     * Reinicia el estado de detección de accidentes
     */
    fun resetAccidentDetection() {
        _accidentDetected.value = null
        mlPredictor.reset()
    }
    
    /**
     * Verifica si los sensores están disponibles
     */
    fun areSensorsAvailable(): Boolean {
        return accelerometer != null // Solo requiere acelerómetro
    }
}