package com.example.alertaraven4.data

/**
 * DTO para contactos de emergencia usado en la capa de API/Adaptador.
 */
data class EmergencyContactData(
    val name: String,
    val phoneNumber: String,
    val relationship: String,
    val isPrimary: Boolean = false,
    val isActive: Boolean = true
)