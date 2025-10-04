package com.example.alertaraven4.api

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.alertaraven4.api.models.*
import com.example.alertaraven4.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Servicio para manejar la comunicación con la API AlertaRaven
 * Proporciona una interfaz de alto nivel para enviar alertas y monitorear el estado
 */
class AlertApiService(private val context: Context) {
    
    companion object {
        private const val TAG = "AlertApiService"
        private const val RETRY_DELAY_MS = 5000L
        private const val MAX_RETRY_ATTEMPTS = 3
    }
    
    private val apiClient = ApiClient.getInstance()
    private val dataAdapter = ApiDataAdapter(context)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Estado de conexión con la API
    private val _connectionStatus = MutableStateFlow(ApiConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ApiConnectionStatus> = _connectionStatus.asStateFlow()
    
    // Cola de alertas pendientes de envío
    private val pendingAlerts = mutableListOf<EmergencyAlertRequest>()
    private val _pendingAlertsCount = MutableStateFlow(0)
    val pendingAlertsCount: StateFlow<Int> = _pendingAlertsCount.asStateFlow()
    
    // Resultados de alertas enviadas
    private val _alertResults = MutableSharedFlow<AlertSendResult>()
    val alertResults: SharedFlow<AlertSendResult> = _alertResults.asSharedFlow()
    
    init {
        // Iniciar monitoreo de conectividad
        startConnectivityMonitoring()
        // Procesar alertas pendientes
        startPendingAlertsProcessor()
    }
    
    /**
     * Envía una alerta de emergencia a la API
     */
    suspend fun sendEmergencyAlert(
        accidentEvent: AccidentEvent,
        location: Location? = null,
        medicalProfile: MedicalProfile? = null,
        emergencyContacts: List<EmergencyContactData> = emptyList(),
        additionalSensorData: Map<String, Any>? = null
    ): AlertSendResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Preparando envío de alerta de emergencia")
                
                val alertRequest = dataAdapter.convertToEmergencyAlertRequest(
                    accidentEvent = accidentEvent,
                    location = location,
                    medicalProfile = medicalProfile,
                    emergencyContacts = emergencyContacts,
                    additionalSensorData = additionalSensorData
                )
                
                // Validar datos antes de enviar
                when (val validation = dataAdapter.validateEmergencyAlertRequest(alertRequest)) {
                    is ValidationResult.Success -> {
                        sendAlertWithRetry(alertRequest)
                    }
                    is ValidationResult.Error -> {
                        val errorMessage = "Datos inválidos: ${validation.errors.joinToString(", ")}"
                        Log.e(TAG, errorMessage)
                        AlertSendResult.ValidationError(validation.errors)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preparando alerta", e)
                AlertSendResult.Error("Error preparando alerta: ${e.message}")
            }
        }
    }
    
    /**
     * Envía una alerta con reintentos automáticos
     */
    private suspend fun sendAlertWithRetry(alertRequest: EmergencyAlertRequest): AlertSendResult {
        var lastError: String? = null
        
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            Log.d(TAG, "Intento ${attempt + 1} de envío de alerta")
            
            when (val result = apiClient.sendEmergencyAlert(alertRequest)) {
                is ApiResult.Success -> {
                    Log.i(TAG, "Alerta enviada exitosamente: ${result.data.alertId}")
                    val sendResult = AlertSendResult.Success(result.data)
                    _alertResults.emit(sendResult)
                    return sendResult
                }
                
                is ApiResult.Error -> {
                    lastError = result.message
                    Log.w(TAG, "Error enviando alerta (intento ${attempt + 1}): ${result.message}")
                    
                    // Si es un error de autenticación o validación, no reintentar
                    if (result.code == 401 || result.code == 400) {
                        val sendResult = AlertSendResult.Error(result.message)
                        _alertResults.emit(sendResult)
                        return sendResult
                    }
                }
                
                is ApiResult.NetworkError -> {
                    lastError = "Error de red: ${result.exception.message}"
                    Log.w(TAG, "Error de red (intento ${attempt + 1})", result.exception)
                }
            }
            
            // Esperar antes del siguiente intento
            if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                delay(RETRY_DELAY_MS)
            }
        }
        
        // Si llegamos aquí, todos los intentos fallaron
        Log.e(TAG, "Falló el envío de alerta después de $MAX_RETRY_ATTEMPTS intentos")
        
        // Agregar a cola de pendientes para reintento posterior
        addToPendingQueue(alertRequest)
        
        val sendResult = AlertSendResult.Error(lastError ?: "Error desconocido después de múltiples intentos")
        _alertResults.emit(sendResult)
        return sendResult
    }
    
    /**
     * Agrega una alerta a la cola de pendientes
     */
    private fun addToPendingQueue(alertRequest: EmergencyAlertRequest) {
        synchronized(pendingAlerts) {
            pendingAlerts.add(alertRequest)
            _pendingAlertsCount.value = pendingAlerts.size
        }
        Log.d(TAG, "Alerta agregada a cola de pendientes. Total: ${pendingAlerts.size}")
    }
    
    /**
     * Obtiene el estado de una alerta
     */
    suspend fun getAlertStatus(alertId: String): ApiResult<AlertStatusResponse> {
        return apiClient.getAlertStatus(alertId)
    }

    /**
     * Actualiza el estado de una alerta
     */
    suspend fun updateAlertStatus(alertId: String, status: String): ApiResult<AlertStatusResponse> {
        return apiClient.updateAlertStatus(alertId, status)
    }

    /**
     * Obtiene la lista de alertas
     */
    suspend fun getAlerts(
        limit: Int = 50,
        offset: Int = 0,
        deviceId: String? = null,
        status: String? = null
    ): ApiResult<AlertsListResponse> {
        return apiClient.getAlerts(limit, offset, deviceId, status)
    }

    /**
     * Lista eventos de sensores
     */
    suspend fun listSensorEvents(limit: Int = 50, offset: Int = 0): ApiResult<SensorEventsListResponse> {
        return apiClient.listSensorEvents(limit, offset)
    }

    /**
     * Exporta eventos de sensores (CSV)
     */
    suspend fun exportSensorEventsCsv(): ApiResult<String> {
        return apiClient.exportSensorEventsCsv()
    }

    /**
     * Obtiene contactos del dispositivo
     */
    suspend fun getDeviceContacts(deviceId: String): ApiResult<DeviceContactsResponse> {
        return apiClient.getDeviceContacts(deviceId)
    }

    /**
     * Reemplaza contactos del dispositivo
     */
    suspend fun replaceDeviceContacts(
        deviceId: String,
        contacts: List<com.example.alertaraven4.api.models.EmergencyContact>
    ): ApiResult<DeviceContactsResponse> {
        return apiClient.replaceDeviceContacts(deviceId, contacts)
    }
    
    /**
     * Configura la URL base de la API
     */
    fun setApiBaseUrl(url: String) {
        apiClient.setBaseUrl(url)
        Log.i(TAG, "URL base de API configurada: $url")
        
        // Reiniciar monitoreo de conectividad
        serviceScope.launch {
            checkConnectivity()
        }
    }
    
    /**
     * Verifica la conectividad con la API
     */
    suspend fun checkConnectivity(): ApiConnectionStatus {
        _connectionStatus.value = ApiConnectionStatus.CONNECTING
        
        val status = apiClient.checkConnectivity()
        _connectionStatus.value = status
        
        Log.d(TAG, "Estado de conectividad: $status")
        return status
    }
    
    /**
     * Inicia el monitoreo periódico de conectividad
     */
    private fun startConnectivityMonitoring() {
        serviceScope.launch {
            while (isActive) {
                try {
                    checkConnectivity()
                    delay(30000) // Verificar cada 30 segundos
                } catch (e: Exception) {
                    Log.e(TAG, "Error en monitoreo de conectividad", e)
                    _connectionStatus.value = ApiConnectionStatus.ERROR
                    delay(10000) // Esperar menos tiempo si hay error
                }
            }
        }
    }
    
    /**
     * Procesa alertas pendientes cuando hay conectividad
     */
    private fun startPendingAlertsProcessor() {
        serviceScope.launch {
            connectionStatus.collect { status ->
                if (status == ApiConnectionStatus.CONNECTED && pendingAlerts.isNotEmpty()) {
                    processPendingAlerts()
                }
            }
        }
    }
    
    /**
     * Procesa todas las alertas pendientes
     */
    private suspend fun processPendingAlerts() {
        Log.d(TAG, "Procesando ${pendingAlerts.size} alertas pendientes")
        
        val alertsToProcess = synchronized(pendingAlerts) {
            val copy = pendingAlerts.toList()
            pendingAlerts.clear()
            _pendingAlertsCount.value = 0
            copy
        }
        
        alertsToProcess.forEach { alertRequest ->
            try {
                when (val result = apiClient.sendEmergencyAlert(alertRequest)) {
                    is ApiResult.Success -> {
                        Log.i(TAG, "Alerta pendiente enviada exitosamente: ${result.data.alertId}")
                        _alertResults.emit(AlertSendResult.Success(result.data))
                    }
                    
                    is ApiResult.Error, is ApiResult.NetworkError -> {
                        Log.w(TAG, "Falló envío de alerta pendiente, regresando a cola")
                        addToPendingQueue(alertRequest)
                    }
                }
                
                // Pequeña pausa entre envíos
                delay(1000)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando alerta pendiente", e)
                addToPendingQueue(alertRequest)
            }
        }
    }
    
    /**
     * Obtiene información de la API
     */
    suspend fun getApiInfo(): ApiResult<ApiInfoResponse> {
        return apiClient.getApiInfo()
    }
    
    /**
     * Limpia recursos del servicio
     */
    fun cleanup() {
        serviceScope.cancel()
        Log.d(TAG, "Servicio de API limpiado")
    }
    
    /**
     * Obtiene estadísticas del servicio
     */
    fun getServiceStats(): ApiServiceStats {
        return ApiServiceStats(
            connectionStatus = _connectionStatus.value,
            pendingAlertsCount = _pendingAlertsCount.value,
            apiBaseUrl = apiClient.getCurrentBaseUrl(),
            isUsingDefaultUrl = apiClient.isUsingDefaultUrl()
        )
    }
}

/**
 * Resultado del envío de alertas
 */
sealed class AlertSendResult {
    data class Success(val response: AlertResponse) : AlertSendResult()
    data class Error(val message: String) : AlertSendResult()
    data class ValidationError(val errors: List<String>) : AlertSendResult()
}

/**
 * Estadísticas del servicio de API
 */
data class ApiServiceStats(
    val connectionStatus: ApiConnectionStatus,
    val pendingAlertsCount: Int,
    val apiBaseUrl: String,
    val isUsingDefaultUrl: Boolean
)