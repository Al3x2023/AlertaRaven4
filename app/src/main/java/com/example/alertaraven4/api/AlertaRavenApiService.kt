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
     * Health check de la API
     */
    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>
    
    /**
     * Endpoint raíz de la API
     */
    @GET("/")
    suspend fun getApiInfo(): Response<ApiInfoResponse>
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