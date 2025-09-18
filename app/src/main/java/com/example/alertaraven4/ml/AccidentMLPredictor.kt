package com.example.alertaraven4.ml

import com.example.alertaraven4.data.AccidentType
import com.example.alertaraven4.data.SensorData
import kotlin.math.*

/**
 * Sistema básico de Machine Learning para mejorar la precisión de detección de accidentes
 * Utiliza un enfoque de ensemble con múltiples clasificadores simples
 */
class AccidentMLPredictor {
    
    companion object {
        private const val TAG = "AccidentMLPredictor"
        
        // Pesos para diferentes características
        private const val ACCELERATION_WEIGHT = 0.4f
        private const val GYROSCOPE_WEIGHT = 0.3f
        private const val PATTERN_WEIGHT = 0.2f
        private const val TREND_WEIGHT = 0.1f
    }
    
    // Historial para análisis de patrones
    private val accelerationHistory = mutableListOf<Float>()
    private val gyroscopeHistory = mutableListOf<Float>()
    private val maxHistorySize = 20
    
    /**
     * Predice la probabilidad de accidente usando ML básico
     */
    fun predictAccidentProbability(
        currentData: SensorData,
        recentData: List<SensorData>
    ): AccidentPrediction {
        
        // Extraer características
        val features = extractFeatures(currentData, recentData)
        
        // Clasificadores ensemble
        val collisionProb = classifyCollision(features)
        val rolloverProb = classifyRollover(features)
        val suddenStopProb = classifySuddenStop(features)
        val fallProb = classifyFall(features)
        
        // Determinar tipo más probable
        val probabilities = mapOf(
            AccidentType.COLLISION to collisionProb,
            AccidentType.ROLLOVER to rolloverProb,
            AccidentType.SUDDEN_STOP to suddenStopProb,
            AccidentType.FALL to fallProb
        )
        
        val mostLikelyType = probabilities.maxByOrNull { it.value }?.key ?: AccidentType.UNKNOWN
        val maxProbability = probabilities.values.maxOrNull() ?: 0f
        
        // Actualizar historial
        updateHistory(currentData)
        
        return AccidentPrediction(
            type = mostLikelyType,
            confidence = maxProbability,
            probabilities = probabilities
        )
    }
    
    /**
     * Extrae características relevantes de los datos
     */
    private fun extractFeatures(currentData: SensorData, recentData: List<SensorData>): MLFeatures {
        val accelerationMagnitude = currentData.getAccelerationMagnitude()
        val gyroscopeMagnitude = currentData.getGyroscopeMagnitude()
        
        // Características estadísticas
        val accelerationMean = recentData.map { it.getAccelerationMagnitude() }.average().toFloat()
        val accelerationStd = calculateStandardDeviation(recentData.map { it.getAccelerationMagnitude() })
        val gyroscopeMean = recentData.map { it.getGyroscopeMagnitude() }.average().toFloat()
        val gyroscopeStd = calculateStandardDeviation(recentData.map { it.getGyroscopeMagnitude() })
        
        // Características de tendencia
        val accelerationTrend = calculateTrend(recentData.map { it.getAccelerationMagnitude() })
        val gyroscopeTrend = calculateTrend(recentData.map { it.getGyroscopeMagnitude() })
        
        // Características de variabilidad
        val accelerationVariability = if (accelerationMean > 0) accelerationStd / accelerationMean else 0f
        val gyroscopeVariability = if (gyroscopeMean > 0) gyroscopeStd / gyroscopeMean else 0f
        
        return MLFeatures(
            accelerationMagnitude = accelerationMagnitude,
            gyroscopeMagnitude = gyroscopeMagnitude,
            accelerationMean = accelerationMean,
            accelerationStd = accelerationStd,
            gyroscopeMean = gyroscopeMean,
            gyroscopeStd = gyroscopeStd,
            accelerationTrend = accelerationTrend,
            gyroscopeTrend = gyroscopeTrend,
            accelerationVariability = accelerationVariability,
            gyroscopeVariability = gyroscopeVariability
        )
    }
    
    /**
     * Clasificador para colisiones
     */
    private fun classifyCollision(features: MLFeatures): Float {
        var score = 0f
        
        // Alta aceleración súbita
        if (features.accelerationMagnitude > 15f) {
            score += 0.4f * (features.accelerationMagnitude / 30f).coerceAtMost(1f)
        }
        
        // Baja variabilidad previa (movimiento estable antes del impacto)
        if (features.accelerationVariability < 0.3f) {
            score += 0.2f
        }
        
        // Tendencia creciente rápida
        if (features.accelerationTrend > 5f) {
            score += 0.3f
        }
        
        // Giroscopio moderado (no volcadura)
        if (features.gyroscopeMagnitude < 8f) {
            score += 0.1f
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * Clasificador para volcaduras
     */
    private fun classifyRollover(features: MLFeatures): Float {
        var score = 0f
        
        // Alta rotación
        if (features.gyroscopeMagnitude > 6f) {
            score += 0.5f * (features.gyroscopeMagnitude / 15f).coerceAtMost(1f)
        }
        
        // Rotación sostenida
        if (features.gyroscopeTrend > 2f) {
            score += 0.3f
        }
        
        // Aceleración moderada a alta
        if (features.accelerationMagnitude > 10f) {
            score += 0.2f
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * Clasificador para frenado brusco
     */
    private fun classifySuddenStop(features: MLFeatures): Float {
        var score = 0f
        
        // Desaceleración significativa
        if (features.accelerationTrend < -3f) {
            score += 0.4f * (abs(features.accelerationTrend) / 10f).coerceAtMost(1f)
        }
        
        // Aceleración moderada
        if (features.accelerationMagnitude in 8f..20f) {
            score += 0.3f
        }
        
        // Baja rotación
        if (features.gyroscopeMagnitude < 5f) {
            score += 0.2f
        }
        
        // Alta variabilidad en aceleración
        if (features.accelerationVariability > 0.5f) {
            score += 0.1f
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * Clasificador para caídas
     */
    private fun classifyFall(features: MLFeatures): Float {
        var score = 0f
        
        // Aceleración muy baja (caída libre)
        if (features.accelerationMagnitude < 5f) {
            score += 0.5f * (1f - features.accelerationMagnitude / 5f)
        }
        
        // Baja rotación
        if (features.gyroscopeMagnitude < 3f) {
            score += 0.3f
        }
        
        // Tendencia decreciente
        if (features.accelerationTrend < -2f) {
            score += 0.2f
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * Calcula la desviación estándar
     */
    private fun calculateStandardDeviation(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        
        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance).toFloat()
    }
    
    /**
     * Calcula la tendencia (pendiente) de una serie de valores
     */
    private fun calculateTrend(values: List<Float>): Float {
        if (values.size < 2) return 0f
        
        val n = values.size
        val sumX = (0 until n).sum()
        val sumY = values.sum()
        val sumXY = values.mapIndexed { index, value -> index * value }.sum()
        val sumX2 = (0 until n).map { it * it }.sum()
        
        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        return slope
    }
    
    /**
     * Actualiza el historial de datos
     */
    private fun updateHistory(currentData: SensorData) {
        accelerationHistory.add(currentData.getAccelerationMagnitude())
        gyroscopeHistory.add(currentData.getGyroscopeMagnitude())
        
        // Mantener tamaño del historial
        if (accelerationHistory.size > maxHistorySize) {
            accelerationHistory.removeAt(0)
        }
        if (gyroscopeHistory.size > maxHistorySize) {
            gyroscopeHistory.removeAt(0)
        }
    }
    
    /**
     * Reinicia el predictor
     */
    fun reset() {
        accelerationHistory.clear()
        gyroscopeHistory.clear()
    }
}

/**
 * Características extraídas para ML
 */
data class MLFeatures(
    val accelerationMagnitude: Float,
    val gyroscopeMagnitude: Float,
    val accelerationMean: Float,
    val accelerationStd: Float,
    val gyroscopeMean: Float,
    val gyroscopeStd: Float,
    val accelerationTrend: Float,
    val gyroscopeTrend: Float,
    val accelerationVariability: Float,
    val gyroscopeVariability: Float
)

/**
 * Resultado de predicción de ML
 */
data class AccidentPrediction(
    val type: AccidentType,
    val confidence: Float,
    val probabilities: Map<AccidentType, Float>
)