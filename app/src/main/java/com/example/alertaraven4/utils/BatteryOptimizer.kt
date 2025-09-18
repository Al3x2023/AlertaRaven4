package com.example.alertaraven4.utils

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optimizador de batería para monitoreo eficiente
 */
class BatteryOptimizer(private val context: Context) {
    
    companion object {
        private const val TAG = "BatteryOptimizer"
        
        // Configuraciones de optimización
        private const val LOW_BATTERY_THRESHOLD = 20 // 20%
        private const val CRITICAL_BATTERY_THRESHOLD = 10 // 10%
        
        // Intervalos de sensores (en microsegundos)
        private const val NORMAL_SENSOR_DELAY = SensorManager.SENSOR_DELAY_NORMAL // 200ms
        private const val POWER_SAVE_SENSOR_DELAY = SensorManager.SENSOR_DELAY_UI // 60ms
        private const val CRITICAL_SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME // 20ms
        
        // Intervalos de GPS (en milisegundos)
        private const val NORMAL_GPS_INTERVAL = 5000L // 5 segundos
        private const val POWER_SAVE_GPS_INTERVAL = 10000L // 10 segundos
        private const val CRITICAL_GPS_INTERVAL = 15000L // 15 segundos
    }
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()
    
    private val _powerSaveMode = MutableStateFlow(PowerSaveMode.NORMAL)
    val powerSaveMode: StateFlow<PowerSaveMode> = _powerSaveMode.asStateFlow()
    
    private val _optimizationSettings = MutableStateFlow(OptimizationSettings())
    val optimizationSettings: StateFlow<OptimizationSettings> = _optimizationSettings.asStateFlow()
    
    private var batteryMonitoringJob: Job? = null
    
    enum class PowerSaveMode {
        NORMAL,      // Funcionamiento normal
        POWER_SAVE,  // Modo ahorro de energía
        CRITICAL     // Modo crítico de batería
    }
    
    data class OptimizationSettings(
        val sensorDelay: Int = NORMAL_SENSOR_DELAY,
        val gpsInterval: Long = NORMAL_GPS_INTERVAL,
        val enableVibration: Boolean = true,
        val enableSound: Boolean = true,
        val enableLocationHighAccuracy: Boolean = true,
        val maxContinuousMonitoring: Long = 8 * 60 * 60 * 1000L // 8 horas
    )
    
    init {
        startBatteryMonitoring()
        updateOptimizationSettings()
    }
    
    /**
     * Inicia el monitoreo de batería
     */
    private fun startBatteryMonitoring() {
        batteryMonitoringJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                updateBatteryLevel()
                updatePowerSaveMode()
                updateOptimizationSettings()
                
                delay(30000) // Verificar cada 30 segundos
            }
        }
    }
    
    /**
     * Actualiza el nivel de batería
     */
    private fun updateBatteryLevel() {
        try {
            val batteryIntent = context.registerReceiver(null, 
                android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                
                if (level != -1 && scale != -1) {
                    val batteryPct = (level * 100 / scale.toFloat()).toInt()
                    _batteryLevel.value = batteryPct
                    Log.d(TAG, "Nivel de batería: $batteryPct%")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo nivel de batería", e)
        }
    }
    
    /**
     * Actualiza el modo de ahorro de energía
     */
    private fun updatePowerSaveMode() {
        val currentLevel = _batteryLevel.value
        val isSystemPowerSaveMode = powerManager.isPowerSaveMode
        
        val newMode = when {
            currentLevel <= CRITICAL_BATTERY_THRESHOLD || isSystemPowerSaveMode -> PowerSaveMode.CRITICAL
            currentLevel <= LOW_BATTERY_THRESHOLD -> PowerSaveMode.POWER_SAVE
            else -> PowerSaveMode.NORMAL
        }
        
        if (_powerSaveMode.value != newMode) {
            _powerSaveMode.value = newMode
            Log.i(TAG, "Modo de energía cambiado a: $newMode")
        }
    }
    
    /**
     * Actualiza las configuraciones de optimización
     */
    private fun updateOptimizationSettings() {
        val mode = _powerSaveMode.value
        
        val newSettings = when (mode) {
            PowerSaveMode.NORMAL -> OptimizationSettings(
                sensorDelay = NORMAL_SENSOR_DELAY,
                gpsInterval = NORMAL_GPS_INTERVAL,
                enableVibration = true,
                enableSound = true,
                enableLocationHighAccuracy = true
            )
            
            PowerSaveMode.POWER_SAVE -> OptimizationSettings(
                sensorDelay = POWER_SAVE_SENSOR_DELAY,
                gpsInterval = POWER_SAVE_GPS_INTERVAL,
                enableVibration = true,
                enableSound = false, // Desactivar sonido para ahorrar batería
                enableLocationHighAccuracy = false
            )
            
            PowerSaveMode.CRITICAL -> OptimizationSettings(
                sensorDelay = CRITICAL_SENSOR_DELAY,
                gpsInterval = CRITICAL_GPS_INTERVAL,
                enableVibration = false, // Desactivar vibración
                enableSound = false,
                enableLocationHighAccuracy = false
            )
        }
        
        _optimizationSettings.value = newSettings
    }
    
    /**
     * Verifica si la optimización de batería está deshabilitada para la app
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // En versiones anteriores no hay optimización de batería
        }
    }
    
    /**
     * Solicita deshabilitar la optimización de batería
     */
    fun requestDisableBatteryOptimization(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
                   !isBatteryOptimizationDisabled()) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }
    
    /**
     * Obtiene recomendaciones de optimización
     */
    fun getOptimizationRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Verificar optimización de batería
        if (!isBatteryOptimizationDisabled()) {
            recommendations.add("Deshabilitar optimización de batería para AlertaRaven")
        }
        
        // Verificar sensores disponibles
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        if (accelerometer == null) {
            recommendations.add("Acelerómetro no disponible - funcionalidad limitada")
        }
        
        if (gyroscope == null) {
            recommendations.add("Giroscopio no disponible - detección menos precisa")
        }
        
        // Verificar GPS
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            recommendations.add("Activar GPS para ubicación precisa")
        }
        
        // Recomendaciones según nivel de batería
        when (_powerSaveMode.value) {
            PowerSaveMode.POWER_SAVE -> {
                recommendations.add("Batería baja - algunas funciones reducidas")
            }
            PowerSaveMode.CRITICAL -> {
                recommendations.add("Batería crítica - funcionalidad mínima activada")
            }
            else -> { /* Normal, sin recomendaciones adicionales */ }
        }
        
        return recommendations
    }
    
    /**
     * Calcula el consumo estimado de batería por hora
     */
    fun getEstimatedBatteryConsumptionPerHour(): Double {
        return when (_powerSaveMode.value) {
            PowerSaveMode.NORMAL -> 8.0 // 8% por hora
            PowerSaveMode.POWER_SAVE -> 5.0 // 5% por hora
            PowerSaveMode.CRITICAL -> 3.0 // 3% por hora
        }
    }
    
    /**
     * Calcula el tiempo estimado de monitoreo restante
     */
    fun getEstimatedMonitoringTimeRemaining(): Long {
        val currentBattery = _batteryLevel.value
        val consumptionPerHour = getEstimatedBatteryConsumptionPerHour()
        
        if (consumptionPerHour <= 0) return Long.MAX_VALUE
        
        val hoursRemaining = (currentBattery / consumptionPerHour).toLong()
        return hoursRemaining * 60 * 60 * 1000L // Convertir a milisegundos
    }
    
    /**
     * Optimiza la configuración de sensores según el modo actual
     */
    fun getOptimizedSensorDelay(): Int {
        return _optimizationSettings.value.sensorDelay
    }
    
    /**
     * Optimiza el intervalo de GPS según el modo actual
     */
    fun getOptimizedGpsInterval(): Long {
        return _optimizationSettings.value.gpsInterval
    }
    
    /**
     * Verifica si la vibración está habilitada
     */
    fun isVibrationEnabled(): Boolean {
        return _optimizationSettings.value.enableVibration
    }
    
    /**
     * Verifica si el sonido está habilitado
     */
    fun isSoundEnabled(): Boolean {
        return _optimizationSettings.value.enableSound
    }
    
    /**
     * Verifica si la alta precisión de ubicación está habilitada
     */
    fun isHighAccuracyLocationEnabled(): Boolean {
        return _optimizationSettings.value.enableLocationHighAccuracy
    }
    
    /**
     * Genera un reporte de estado de batería
     */
    fun generateBatteryReport(): String {
        val report = StringBuilder()
        
        report.append("=== REPORTE DE BATERÍA ===\n\n")
        report.append("Nivel actual: ${_batteryLevel.value}%\n")
        report.append("Modo de energía: ${_powerSaveMode.value}\n")
        report.append("Optimización deshabilitada: ${if (isBatteryOptimizationDisabled()) "Sí" else "No"}\n")
        report.append("Consumo estimado/hora: ${String.format("%.1f", getEstimatedBatteryConsumptionPerHour())}%\n")
        
        val timeRemaining = getEstimatedMonitoringTimeRemaining()
        if (timeRemaining != Long.MAX_VALUE) {
            val hours = timeRemaining / (60 * 60 * 1000L)
            report.append("Tiempo estimado restante: ${hours}h\n")
        } else {
            report.append("Tiempo estimado restante: Ilimitado\n")
        }
        
        report.append("\nConfiguraciones actuales:\n")
        val settings = _optimizationSettings.value
        report.append("- Intervalo sensores: ${getSensorDelayText(settings.sensorDelay)}\n")
        report.append("- Intervalo GPS: ${settings.gpsInterval / 1000}s\n")
        report.append("- Vibración: ${if (settings.enableVibration) "Activada" else "Desactivada"}\n")
        report.append("- Sonido: ${if (settings.enableSound) "Activado" else "Desactivado"}\n")
        report.append("- GPS alta precisión: ${if (settings.enableLocationHighAccuracy) "Activado" else "Desactivado"}\n")
        
        return report.toString()
    }
    
    /**
     * Convierte el delay de sensor a texto legible
     */
    private fun getSensorDelayText(delay: Int): String {
        return when (delay) {
            SensorManager.SENSOR_DELAY_FASTEST -> "Más rápido (20ms)"
            SensorManager.SENSOR_DELAY_GAME -> "Juego (20ms)"
            SensorManager.SENSOR_DELAY_UI -> "UI (60ms)"
            SensorManager.SENSOR_DELAY_NORMAL -> "Normal (200ms)"
            else -> "Personalizado (${delay}μs)"
        }
    }
    
    /**
     * Limpia recursos
     */
    fun cleanup() {
        batteryMonitoringJob?.cancel()
        Log.i(TAG, "BatteryOptimizer limpiado")
    }
}