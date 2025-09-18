package com.example.alertaraven4.data

import android.location.Location

/**
 * Clase de datos para representar un evento de accidente detectado
 */
data class AccidentEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val accelerationMagnitude: Float,
    val gyroscopeMagnitude: Float,
    val location: Location?,
    val confidence: Float, // Nivel de confianza de la detección (0.0 - 1.0)
    val type: AccidentType = AccidentType.COLLISION
)

/**
 * Tipos de accidentes que puede detectar la aplicación
 */
enum class AccidentType {
    COLLISION,      // Colisión vehicular
    SUDDEN_STOP,    // Frenado brusco
    ROLLOVER,       // Volcadura
    FALL,           // Caída del dispositivo
    UNKNOWN         // Tipo desconocido
}

/**
 * Datos de los sensores en tiempo real
 */
data class SensorData(
    val accelerometerX: Float,
    val accelerometerY: Float,
    val accelerometerZ: Float,
    val gyroscopeX: Float,
    val gyroscopeY: Float,
    val gyroscopeZ: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Calcula la magnitud total de la aceleración
     */
    fun getAccelerationMagnitude(): Float {
        return kotlin.math.sqrt(
            accelerometerX * accelerometerX +
            accelerometerY * accelerometerY +
            accelerometerZ * accelerometerZ
        )
    }
    
    /**
     * Calcula la magnitud total del giroscopio
     */
    fun getGyroscopeMagnitude(): Float {
        return kotlin.math.sqrt(
            gyroscopeX * gyroscopeX +
            gyroscopeY * gyroscopeY +
            gyroscopeZ * gyroscopeZ
        )
    }
}

/**
 * Configuración de umbrales para la detección de accidentes - Optimizada
 */
data class AccidentThresholds(
    val accelerationThreshold: Float = 15.0f,      // m/s² - Reducido para detectar impactos moderados
    val gyroscopeThreshold: Float = 6.0f,          // rad/s - Reducido para mayor sensibilidad a rotaciones
    val suddenStopThreshold: Float = 8.0f,         // m/s² - Reducido para detectar frenados menos extremos
    val fallThreshold: Float = 3.0f,               // m/s² - Ajustado para reducir falsos positivos
    val minimumConfidence: Float = 0.65f,          // Reducida para permitir detección más temprana
    val sensorSamplingRate: Int = 200,             // Aumentada para mejor resolución temporal
    val analysisWindowMs: Long = 1500,             // Reducida para respuesta más rápida
    val calibrationSamples: Int = 50,              // Muestras para calibración inicial
    val noiseThreshold: Float = 0.5f,              // Umbral para filtro de ruido
    val dataQueueSize: Int = 300                   // Tamaño optimizado de cola (1.5s a 200Hz)
)