package com.example.alertaraven4.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor centralizado de configuración de la aplicación
 */
class SettingsManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "alerta_raven_settings"
        
        // Claves de configuración
        private const val KEY_DETECTION_SENSITIVITY = "detection_sensitivity"
        private const val KEY_ALERT_TIMER = "alert_timer"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_LOCATION_ACCURACY = "location_accuracy"
        private const val KEY_AUTO_START_MONITORING = "auto_start_monitoring"
        private const val KEY_MONITORING_DELAY = "monitoring_delay"
        private const val KEY_REQUIRE_CONFIRMATION = "require_confirmation"
        private const val KEY_AUTO_CALL_ENABLED = "auto_call_enabled"
        
        // Valores por defecto
        const val DEFAULT_DETECTION_SENSITIVITY = "Media"
        const val DEFAULT_ALERT_TIMER = 15
        const val DEFAULT_SOUND_ENABLED = true
        const val DEFAULT_VIBRATION_ENABLED = true
        const val DEFAULT_LOCATION_ACCURACY = "Alta"
        const val DEFAULT_AUTO_START_MONITORING = false
        const val DEFAULT_MONITORING_DELAY = 3 // segundos
        const val DEFAULT_REQUIRE_CONFIRMATION = true
        const val DEFAULT_AUTO_CALL_ENABLED = false // Deshabilitado por defecto por seguridad
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Estados observables
    private val _detectionSensitivity = MutableStateFlow(getDetectionSensitivity())
    val detectionSensitivity: StateFlow<String> = _detectionSensitivity.asStateFlow()
    
    private val _alertTimer = MutableStateFlow(getAlertTimer())
    val alertTimer: StateFlow<Int> = _alertTimer.asStateFlow()
    
    private val _soundEnabled = MutableStateFlow(isSoundEnabled())
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()
    
    private val _vibrationEnabled = MutableStateFlow(isVibrationEnabled())
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()
    
    private val _locationAccuracy = MutableStateFlow(getLocationAccuracy())
    val locationAccuracy: StateFlow<String> = _locationAccuracy.asStateFlow()
    
    private val _autoStartMonitoring = MutableStateFlow(isAutoStartMonitoringEnabled())
    val autoStartMonitoring: StateFlow<Boolean> = _autoStartMonitoring.asStateFlow()
    
    private val _monitoringDelay = MutableStateFlow(getMonitoringDelay())
    val monitoringDelay: StateFlow<Int> = _monitoringDelay.asStateFlow()
    
    private val _requireConfirmation = MutableStateFlow(isConfirmationRequired())
    val requireConfirmation: StateFlow<Boolean> = _requireConfirmation.asStateFlow()
    
    private val _autoCallEnabled = MutableStateFlow(isAutoCallEnabled())
    val autoCallEnabled: StateFlow<Boolean> = _autoCallEnabled.asStateFlow()
    
    // Getters
    fun getDetectionSensitivity(): String = prefs.getString(KEY_DETECTION_SENSITIVITY, DEFAULT_DETECTION_SENSITIVITY) ?: DEFAULT_DETECTION_SENSITIVITY
    
    fun getAlertTimer(): Int = prefs.getInt(KEY_ALERT_TIMER, DEFAULT_ALERT_TIMER)
    
    fun isSoundEnabled(): Boolean = prefs.getBoolean(KEY_SOUND_ENABLED, DEFAULT_SOUND_ENABLED)
    
    fun isVibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION_ENABLED, DEFAULT_VIBRATION_ENABLED)
    
    fun getLocationAccuracy(): String = prefs.getString(KEY_LOCATION_ACCURACY, DEFAULT_LOCATION_ACCURACY) ?: DEFAULT_LOCATION_ACCURACY
    
    fun isAutoStartMonitoringEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_START_MONITORING, DEFAULT_AUTO_START_MONITORING)
    
    fun getMonitoringDelay(): Int = prefs.getInt(KEY_MONITORING_DELAY, DEFAULT_MONITORING_DELAY)
    
    fun isConfirmationRequired(): Boolean = prefs.getBoolean(KEY_REQUIRE_CONFIRMATION, DEFAULT_REQUIRE_CONFIRMATION)
    
    fun isAutoCallEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_CALL_ENABLED, DEFAULT_AUTO_CALL_ENABLED)
    
    // Setters
    fun setDetectionSensitivity(sensitivity: String) {
        prefs.edit().putString(KEY_DETECTION_SENSITIVITY, sensitivity).apply()
        _detectionSensitivity.value = sensitivity
    }
    
    fun setAlertTimer(timer: Int) {
        prefs.edit().putInt(KEY_ALERT_TIMER, timer).apply()
        _alertTimer.value = timer
    }
    
    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        _soundEnabled.value = enabled
    }
    
    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
        _vibrationEnabled.value = enabled
    }
    
    fun setLocationAccuracy(accuracy: String) {
        prefs.edit().putString(KEY_LOCATION_ACCURACY, accuracy).apply()
        _locationAccuracy.value = accuracy
    }
    
    fun setAutoStartMonitoring(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_MONITORING, enabled).apply()
        _autoStartMonitoring.value = enabled
    }
    
    fun setMonitoringDelay(delay: Int) {
        prefs.edit().putInt(KEY_MONITORING_DELAY, delay).apply()
        _monitoringDelay.value = delay
    }
    
    fun setRequireConfirmation(required: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_CONFIRMATION, required).apply()
        _requireConfirmation.value = required
    }
    
    fun setAutoCallEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CALL_ENABLED, enabled).apply()
        _autoCallEnabled.value = enabled
    }
    
    /**
     * Obtiene la configuración de sensibilidad como valor numérico
     * para usar en el detector de accidentes
     */
    fun getSensitivityThreshold(): Float {
        return when (getDetectionSensitivity()) {
            "Baja" -> 15.0f
            "Media" -> 12.0f
            "Alta" -> 8.0f
            else -> 12.0f
        }
    }
    
    /**
     * Obtiene la configuración de precisión de ubicación como valor numérico
     */
    fun getLocationAccuracyMeters(): Float {
        return when (getLocationAccuracy()) {
            "Baja" -> 100.0f
            "Media" -> 50.0f
            "Alta" -> 10.0f
            else -> 50.0f
        }
    }
    
    /**
     * Resetea todas las configuraciones a valores por defecto
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        
        // Actualizar estados observables
        _detectionSensitivity.value = DEFAULT_DETECTION_SENSITIVITY
        _alertTimer.value = DEFAULT_ALERT_TIMER
        _soundEnabled.value = DEFAULT_SOUND_ENABLED
        _vibrationEnabled.value = DEFAULT_VIBRATION_ENABLED
        _locationAccuracy.value = DEFAULT_LOCATION_ACCURACY
        _autoStartMonitoring.value = DEFAULT_AUTO_START_MONITORING
        _monitoringDelay.value = DEFAULT_MONITORING_DELAY
        _requireConfirmation.value = DEFAULT_REQUIRE_CONFIRMATION
        _autoCallEnabled.value = DEFAULT_AUTO_CALL_ENABLED
    }
}