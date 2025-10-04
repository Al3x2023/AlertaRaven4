package com.example.alertaraven4.repository

import android.content.Context
import android.location.Location
import com.example.alertaraven4.api.AlertApiService
import com.example.alertaraven4.api.AlertSendResult
import com.example.alertaraven4.api.models.AlertResponse
import com.example.alertaraven4.api.models.AlertStatusResponse
import com.example.alertaraven4.api.models.ApiResult
import com.example.alertaraven4.data.AccidentEvent
import com.example.alertaraven4.data.EmergencyContactData
import com.example.alertaraven4.data.MedicalProfile

/**
 * Repositorio de alertas que delega en AlertApiService
 * y adapta sus resultados al tipo ApiResult utilizado por el dominio.
 */
class AlertRepository private constructor(private val context: Context) {

    private val apiService = AlertApiService(context)

    companion object {
        @Volatile
        private var INSTANCE: AlertRepository? = null

        fun getInstance(context: Context): AlertRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlertRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Envía una alerta de emergencia y adapta el resultado a ApiResult.
     */
    suspend fun sendEmergencyAlert(
        accidentEvent: AccidentEvent,
        location: Location? = null,
        medicalProfile: MedicalProfile? = null,
        emergencyContacts: List<EmergencyContactData> = emptyList(),
        additionalSensorData: Map<String, Any>? = null
    ): ApiResult<AlertResponse> {
        return when (val result = apiService.sendEmergencyAlert(
            accidentEvent = accidentEvent,
            location = location,
            medicalProfile = medicalProfile,
            emergencyContacts = emergencyContacts,
            additionalSensorData = additionalSensorData
        )) {
            is AlertSendResult.Success -> ApiResult.Success(result.response)
            is AlertSendResult.Error -> ApiResult.Error(result.message)
            is AlertSendResult.ValidationError -> ApiResult.Error(
                "Datos inválidos: ${result.errors.joinToString(", ")}")
        }
    }

    /**
     * Obtiene el estado de una alerta.
     */
    suspend fun getAlertStatus(alertId: String): ApiResult<AlertStatusResponse> {
        return apiService.getAlertStatus(alertId)
    }

    /**
     * Actualiza el estado de una alerta.
     */
    suspend fun updateAlertStatus(alertId: String, status: String): ApiResult<AlertStatusResponse> {
        return apiService.updateAlertStatus(alertId, status)
    }

    /**
     * Cancela una alerta existente.
     * Nota: el endpoint no está disponible actualmente; devuelve error transparente.
     */
    suspend fun cancelAlert(alertId: String): ApiResult<AlertStatusResponse> {
        return ApiResult.Error("Endpoint de cancelación de alerta no implementado", 501)
    }

    /**
     * Configura la URL base de la API a través del servicio.
     */
    fun setApiBaseUrl(url: String) {
        apiService.setApiBaseUrl(url)
    }

    /**
     * Limpia recursos.
     */
    fun cleanup() {
        apiService.cleanup()
    }
}