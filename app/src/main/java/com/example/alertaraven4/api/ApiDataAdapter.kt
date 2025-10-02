package com.example.alertaraven4.api

import android.content.Context
import android.location.Location
import android.provider.Settings
import com.example.alertaraven4.api.models.*
import com.example.alertaraven4.data.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adaptador para convertir datos de la app a modelos de la API
 */
class ApiDataAdapter(private val context: Context) {
    
    companion object {
        private const val API_KEY = "alertaraven_mobile_key_2024"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
    }
    
    /**
     * Convierte un AccidentEvent a EmergencyAlertRequest
     */
    fun convertToEmergencyAlertRequest(
        accidentEvent: AccidentEvent,
        location: Location? = null,
        medicalProfile: MedicalProfile? = null,
        emergencyContacts: List<EmergencyContactData> = emptyList(),
        additionalSensorData: Map<String, Any>? = null
    ): EmergencyAlertRequest {
        
        val deviceId = getDeviceId()
        
        val locationData = location?.let {
            LocationData(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracy = it.accuracy,
                altitude = it.altitude,
                speed = it.speed,
                timestamp = dateFormat.format(Date(it.time))
            )
        }
        
        val medicalInfo = medicalProfile?.let {
            MedicalInfo(
                bloodType = it.bloodType.displayName,
                allergies = it.allergies,
                medications = it.medications,
                medicalConditions = it.medicalConditions,
                emergencyMedicalInfo = it.emergencyMedicalInfo
            )
        }
        
        val apiEmergencyContacts = emergencyContacts.map { contact ->
            com.example.alertaraven4.api.models.EmergencyContact(
                name = contact.name,
                phone = contact.phoneNumber,
                relationship = contact.relationship,
                isPrimary = contact.isPrimary
            )
        }
        
        val accidentEventData = AccidentEventData(
            accidentType = accidentEvent.type.name.lowercase(),
            timestamp = dateFormat.format(accidentEvent.timestamp),
            confidence = accidentEvent.confidence,
            accelerationMagnitude = accidentEvent.accelerationMagnitude,
            gyroscopeMagnitude = accidentEvent.gyroscopeMagnitude,
            locationData = locationData,
            additionalSensorData = additionalSensorData
        )
        
        return EmergencyAlertRequest(
            deviceId = deviceId,
            userId = null, // Se puede agregar si la app maneja usuarios
            accidentEvent = accidentEventData,
            medicalInfo = medicalInfo,
            emergencyContacts = apiEmergencyContacts,
            apiKey = API_KEY
        )
    }
    
    /**
     * Convierte datos de ubicación de Android a LocationData de la API
     */
    fun convertLocationToApiModel(location: Location): LocationData {
        return LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            altitude = location.altitude,
            speed = location.speed,
            timestamp = dateFormat.format(Date(location.time))
        )
    }
    
    /**
     * Convierte MedicalProfile a MedicalInfo de la API
     */
    fun convertMedicalProfileToApiModel(medicalProfile: MedicalProfile): MedicalInfo {
        return MedicalInfo(
            bloodType = medicalProfile.bloodType.displayName,
            allergies = medicalProfile.allergies,
            medications = medicalProfile.medications,
            medicalConditions = medicalProfile.medicalConditions,
            emergencyMedicalInfo = medicalProfile.emergencyMedicalInfo
        )
    }
    
    /**
     * Convierte lista de contactos de emergencia a modelos de la API
     */
    fun convertEmergencyContactsToApiModel(contacts: List<EmergencyContactData>): List<com.example.alertaraven4.api.models.EmergencyContact> {
        return contacts.map { contact ->
            com.example.alertaraven4.api.models.EmergencyContact(
                name = contact.name,
                phone = contact.phoneNumber,
                relationship = contact.relationship,
                isPrimary = contact.isPrimary
            )
        }
    }
    
    /**
     * Convierte AccidentType a string para la API
     */
    fun convertAccidentTypeToApiString(accidentType: AccidentType): String {
        return when (accidentType) {
            AccidentType.COLLISION -> "collision"
            AccidentType.SUDDEN_STOP -> "sudden_stop"
            AccidentType.ROLLOVER -> "rollover"
            AccidentType.FALL -> "fall"
            AccidentType.UNKNOWN -> "unknown"
        }
    }
    
    /**
     * Crea datos adicionales del sensor para enviar a la API
     */
    fun createAdditionalSensorData(
        rawAcceleration: FloatArray? = null,
        rawGyroscope: FloatArray? = null,
        deviceOrientation: String? = null,
        batteryLevel: Float? = null
    ): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        
        rawAcceleration?.let {
            data["raw_acceleration"] = mapOf(
                "x" to it[0],
                "y" to it[1],
                "z" to it[2]
            )
        }
        
        rawGyroscope?.let {
            data["raw_gyroscope"] = mapOf(
                "x" to it[0],
                "y" to it[1],
                "z" to it[2]
            )
        }
        
        deviceOrientation?.let {
            data["device_orientation"] = it
        }
        
        batteryLevel?.let {
            data["battery_level"] = it
        }
        
        data["app_version"] = getAppVersion()
        data["android_version"] = android.os.Build.VERSION.RELEASE
        data["device_model"] = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        
        return data
    }
    
    /**
     * Obtiene el ID único del dispositivo
     */
    private fun getDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }
    
    /**
     * Obtiene la versión de la app
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * Valida que los datos requeridos estén presentes antes de enviar a la API
     */
    fun validateEmergencyAlertRequest(request: EmergencyAlertRequest): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (request.deviceId.isBlank()) {
            errors.add("Device ID es requerido")
        }
        
        if (request.accidentEvent.confidence < 0.0f || request.accidentEvent.confidence > 1.0f) {
            errors.add("Confidence debe estar entre 0.0 y 1.0")
        }
        
        if (request.emergencyContacts.isEmpty()) {
            errors.add("Al menos un contacto de emergencia es requerido")
        }
        
        request.emergencyContacts.forEach { contact ->
            if (contact.name.isBlank()) {
                errors.add("Nombre del contacto no puede estar vacío")
            }
            if (contact.phone.isBlank()) {
                errors.add("Teléfono del contacto no puede estar vacío")
            }
        }
        
        if (request.apiKey.isBlank()) {
            errors.add("API Key es requerida")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
}

/**
 * Resultado de validación
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val errors: List<String>) : ValidationResult()
}