package com.example.alertaraven4.api

import com.example.alertaraven4.api.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Interfaz del servicio API para AlertaRaven
 * Define todos los endpoints disponibles en la API
 */
interface AlertaRavenApiService {
    
    /**
     * Endpoint para enviar alertas de emergencia
     */
    @POST("api/v1/emergency-alert")
    suspend fun sendEmergencyAlert(
        @Body request: EmergencyAlertRequest,
        @Header("Authorization") authorization: String
    ): Response<AlertResponse>

    /**
     * Endpoint debug para enviar alertas de emergencia
     */
    @POST("api/v1/emergency-alert-debug")
    suspend fun sendEmergencyAlertDebug(
        @Body request: EmergencyAlertRequest,
        @Header("Authorization") authorization: String
    ): Response<AlertResponse>
    
    /**
     * Obtiene el estado de una alerta específica
     */
    @GET("api/v1/alerts/{alert_id}")
    suspend fun getAlertStatus(
        @Path("alert_id") alertId: String,
        @Header("Authorization") authorization: String
    ): Response<AlertStatusResponse>
    
    /**
     * Obtiene lista de alertas
     */
    @GET("api/v1/alerts")
    suspend fun getAlerts(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("device_id") deviceId: String? = null,
        @Query("status") status: String? = null,
        @Header("Authorization") authorization: String
    ): Response<AlertsListResponse>

    /**
     * Actualiza el estado de una alerta
     */
    @PUT("api/alerts/{alert_id}/status")
    suspend fun updateAlertStatus(
        @Path("alert_id") alertId: String,
        @Body request: com.example.alertaraven4.api.models.UpdateAlertStatusRequest,
        @Header("Authorization") authorization: String
    ): Response<AlertStatusResponse>
    
    /**
     * Health check de la API
     */
    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>
    
    /**
     * Endpoint raíz de la API
     */
    @GET("/")
    suspend fun getApiInfo(): Response<ApiInfoResponse>

    /**
     * Endpoint para reportar eventos de sensores (ventanas de datos)
     */
    @POST("api/v1/sensor-events")
    suspend fun sendSensorEvent(
        @Body request: com.example.alertaraven4.api.models.SensorEventRequest,
        @Header("Authorization") authorization: String
    ): Response<com.example.alertaraven4.api.models.SensorEventResponse>

    /**
     * Lista eventos de sensores
     */
    @GET("api/v1/sensor-events")
    suspend fun listSensorEvents(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Header("Authorization") authorization: String
    ): Response<com.example.alertaraven4.api.models.SensorEventsListResponse>

    /**
     * Exporta eventos de sensores en formato CSV
     */
    @GET("api/v1/sensor-events/export")
    suspend fun exportSensorEventsCsv(
        @Header("Authorization") authorization: String
    ): Response<okhttp3.ResponseBody>

    /**
     * Obtiene contactos del dispositivo
     */
    @GET("api/v1/contacts/{device_id}")
    suspend fun getDeviceContacts(
        @Path("device_id") deviceId: String,
        @Header("Authorization") authorization: String
    ): Response<com.example.alertaraven4.api.models.DeviceContactsResponse>

    /**
     * Reemplaza contactos del dispositivo
     */
    @PUT("api/v1/contacts/{device_id}")
    suspend fun replaceDeviceContacts(
        @Path("device_id") deviceId: String,
        @Body contacts: List<com.example.alertaraven4.api.models.EmergencyContact>,
        @Header("Authorization") authorization: String
    ): Response<com.example.alertaraven4.api.models.DeviceContactsResponse>
}

/**
 * Respuestas adicionales de la API
 */
data class AlertsListResponse(
    val alerts: List<AlertStatusResponse>,
    val pagination: PaginationInfo,
    val statistics: AlertStatistics?
)

data class PaginationInfo(
    val limit: Int,
    val offset: Int,
    val total: Int
)

data class AlertStatistics(
    val total_alerts: Int,
    val alerts_by_type: Map<String, Int>,
    val alerts_by_status: Map<String, Int>,
    val alerts_today: Int,
    val average_confidence: Float
)

data class HealthResponse(
    val status: String,
    val timestamp: String,
    val database: String,
    val websocket_connections: Int,
    val services: Map<String, Boolean>
)

data class ApiInfoResponse(
    val message: String,
    val version: String,
    val status: String
)