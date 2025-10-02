package com.example.alertaraven4.api.models

import com.google.gson.annotations.SerializedName
import java.util.Date

/**
 * Modelos de datos para la comunicación con la API AlertaRaven
 */

data class LocationData(
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double,
    
    @SerializedName("accuracy")
    val accuracy: Float? = null,
    
    @SerializedName("altitude")
    val altitude: Double? = null,
    
    @SerializedName("speed")
    val speed: Float? = null,
    
    @SerializedName("timestamp")
    val timestamp: String? = null
)

data class MedicalInfo(
    @SerializedName("blood_type")
    val bloodType: String? = null,
    
    @SerializedName("allergies")
    val allergies: List<String>? = null,
    
    @SerializedName("medications")
    val medications: List<String>? = null,
    
    @SerializedName("medical_conditions")
    val medicalConditions: List<String>? = null,
    
    @SerializedName("emergency_medical_info")
    val emergencyMedicalInfo: String? = null
)

data class EmergencyContact(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("phone")
    val phone: String,
    
    @SerializedName("relationship")
    val relationship: String? = null,
    
    @SerializedName("is_primary")
    val isPrimary: Boolean = false
)

data class AccidentEventData(
    @SerializedName("accident_type")
    val accidentType: String,
    
    @SerializedName("timestamp")
    val timestamp: String,
    
    @SerializedName("confidence")
    val confidence: Float,
    
    @SerializedName("acceleration_magnitude")
    val accelerationMagnitude: Float,
    
    @SerializedName("gyroscope_magnitude")
    val gyroscopeMagnitude: Float,
    
    @SerializedName("location_data")
    val locationData: LocationData? = null,
    
    @SerializedName("additional_sensor_data")
    val additionalSensorData: Map<String, Any>? = null
)

data class EmergencyAlertRequest(
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("user_id")
    val userId: String? = null,
    
    @SerializedName("accident_event")
    val accidentEvent: AccidentEventData,
    
    @SerializedName("medical_info")
    val medicalInfo: MedicalInfo? = null,
    
    @SerializedName("emergency_contacts")
    val emergencyContacts: List<EmergencyContact>,
    
    @SerializedName("api_key")
    val apiKey: String
)

data class AlertResponse(
    @SerializedName("alert_id")
    val alertId: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("timestamp")
    val timestamp: String
)

data class AlertStatusResponse(
    @SerializedName("alert_id")
    val alertId: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("created_at")
    val createdAt: String,
    
    @SerializedName("updated_at")
    val updatedAt: String,
    
    @SerializedName("accident_type")
    val accidentType: String,
    
    @SerializedName("confidence")
    val confidence: Float,
    
    @SerializedName("location_data")
    val locationData: LocationData? = null,
    
    @SerializedName("emergency_contacts_count")
    val emergencyContactsCount: Int
)

data class ApiError(
    @SerializedName("detail")
    val detail: String
)

/**
 * Resultado de operaciones de API
 */
sealed class ApiResult<T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error<T>(val message: String, val code: Int? = null) : ApiResult<T>()
    data class NetworkError<T>(val exception: Throwable) : ApiResult<T>()
}

/**
 * Estados de conexión con la API
 */
enum class ApiConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    CONNECTING,
    ERROR
}