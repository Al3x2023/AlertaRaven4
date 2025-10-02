package com.example.alertaraven4.data

/**
 * Datos de contacto de emergencia
 */
data class EmergencyContact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String,
    val relationship: String, // Ej: "Familiar", "Amigo", "Médico"
    val isPrimary: Boolean = false, // Contacto principal
    val isActive: Boolean = true
)

/**
 * Alias para compatibilidad con la API
 */
typealias EmergencyContactData = EmergencyContact

/**
 * Información médica del usuario
 */
data class MedicalProfile(
    val fullName: String = "",
    val age: Int = 0,
    val weight: Float = 0f,
    val height: Float = 0f,
    val dateOfBirth: String = "",
    val bloodType: BloodType = BloodType.UNKNOWN,
    val allergies: List<String> = emptyList(),
    val medications: List<String> = emptyList(),
    val medicalConditions: List<String> = emptyList(),
    val additionalNotes: String = "",
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val emergencyMedicalInfo: String = "", // Información adicional importante
    val insuranceInfo: String = "",
    val doctorName: String = "",
    val doctorPhone: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Tipos de sangre
 */
enum class BloodType(val displayName: String) {
    A_POSITIVE("A+"),
    A_NEGATIVE("A-"),
    B_POSITIVE("B+"),
    B_NEGATIVE("B-"),
    AB_POSITIVE("AB+"),
    AB_NEGATIVE("AB-"),
    O_POSITIVE("O+"),
    O_NEGATIVE("O-"),
    UNKNOWN("Desconocido")
}

/**
 * Configuración de alertas
 */
data class AlertSettings(
    val isEnabled: Boolean = true,
    val cancelTimeoutSeconds: Int = 15, // Tiempo para cancelar alerta
    val sendSMS: Boolean = true,
    val makeCall: Boolean = false, // Llamar automáticamente
    val sendLocation: Boolean = true,
    val includeMedicalInfo: Boolean = true,
    val alertSound: Boolean = true,
    val vibration: Boolean = true,
    val repeatAlerts: Boolean = true, // Repetir alertas si no se cancela
    val repeatIntervalMinutes: Int = 5
)

/**
 * Estado de una alerta de emergencia
 */
data class EmergencyAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val accidentEvent: AccidentEvent,
    val timestamp: Long = System.currentTimeMillis(),
    val status: AlertStatus = AlertStatus.PENDING,
    val contactsNotified: List<String> = emptyList(), // IDs de contactos notificados
    val cancelTimeRemaining: Int = 15, // Segundos restantes para cancelar
    val location: android.location.Location? = null,
    val medicalInfo: MedicalProfile? = null
)

/**
 * Estados de una alerta
 */
enum class AlertStatus {
    PENDING,        // Esperando confirmación/cancelación
    CANCELLED,      // Cancelada por el usuario
    SENT,          // Enviada a contactos
    CONFIRMED,     // Confirmada como emergencia real
    FAILED         // Falló al enviar
}