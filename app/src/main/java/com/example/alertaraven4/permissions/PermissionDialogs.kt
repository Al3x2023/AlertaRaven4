package com.example.alertaraven4.permissions

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.alertaraven4.R

/**
 * Diálogos para explicar y manejar permisos
 */
object PermissionDialogs {
    
    /**
     * Muestra un diálogo explicativo antes de solicitar permisos
     */
    fun showPermissionExplanationDialog(
        context: Context,
        onAccept: () -> Unit,
        onDeny: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("Permisos Necesarios")
            .setMessage("""
                AlertaRaven necesita varios permisos para funcionar correctamente y protegerte en caso de accidente:
                
                📍 Ubicación: Para detectar tu posición y enviarla a contactos de emergencia
                📱 SMS: Para enviar mensajes de alerta automáticos
                📞 Llamadas: Para contactar servicios de emergencia
                🔔 Notificaciones: Para alertarte sobre el estado de la app
                🏃 Actividad física: Para mejorar la detección de accidentes
                
                Estos permisos son esenciales para tu seguridad.
            """.trimIndent())
            .setPositiveButton("Conceder Permisos") { _, _ -> onAccept() }
            .setNegativeButton("Cancelar") { _, _ -> onDeny() }
            .setCancelable(false)
            .show()
    }

    /**
     * Muestra un diálogo explicando el permiso de superposición
     */
    fun showOverlayPermissionDialog(
        context: Context,
        onGoToSettings: () -> Unit,
        onSkip: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("Mostrar sobre otras apps")
            .setMessage(
                """
                Para mostrar ventanas de alerta encima de otras aplicaciones, AlertaRaven necesita el permiso de superposición.

                Esto permite:
                • Mostrar recordatorios o controles rápidos durante el monitoreo
                • Mantenerte informado sin cambiar de app

                Te llevaremos a la configuración para habilitarlo.
                """.trimIndent()
            )
            .setPositiveButton("Ir a Configuración") { _, _ -> onGoToSettings() }
            .setNegativeButton("Ahora no") { _, _ -> onSkip() }
            .setCancelable(false)
            .show()
    }

    /**
     * Muestra un diálogo explicando desactivar optimización de batería
     */
    fun showBatteryOptimizationDialog(
        context: Context,
        onGoToSettings: () -> Unit,
        onSkip: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("Optimización de batería")
            .setMessage(
                """
                Para que AlertaRaven no se pause al estar en segundo plano, recomendamos desactivar la optimización de batería para la app.

                Esto ayuda a que el monitoreo y las alertas funcionen continuamente.
                """.trimIndent()
            )
            .setPositiveButton("Desactivar") { _, _ -> onGoToSettings() }
            .setNegativeButton("Mantener activada") { _, _ -> onSkip() }
            .setCancelable(false)
            .show()
    }
    /**
     * Muestra un diálogo cuando algunos permisos fueron denegados
     */
    fun showPermissionDeniedDialog(
        context: Context,
        deniedPermissions: List<String>,
        permissionManager: PermissionManager,
        onRetry: () -> Unit,
        onContinueWithoutPermissions: () -> Unit
    ) {
        val deniedDescriptions = deniedPermissions.map { permission ->
            "• ${permissionManager.getPermissionDescription(permission)}"
        }.joinToString("\n")
        
        AlertDialog.Builder(context)
            .setTitle("Permisos Denegados")
            .setMessage("""
                Algunos permisos importantes fueron denegados:
                
                $deniedDescriptions
                
                Sin estos permisos, AlertaRaven no podrá protegerte completamente en caso de accidente.
                
                ¿Qué deseas hacer?
            """.trimIndent())
            .setPositiveButton("Reintentar") { _, _ -> onRetry() }
            .setNeutralButton("Ir a Configuración") { _, _ -> 
                permissionManager.openAppSettings()
            }
            .setNegativeButton("Continuar sin permisos") { _, _ -> 
                onContinueWithoutPermissions()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Muestra un diálogo específico para ubicación en segundo plano
     */
    fun showBackgroundLocationDialog(
        context: Context,
        onAccept: () -> Unit,
        onDeny: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("Ubicación en Segundo Plano")
            .setMessage("""
                Para una protección completa, AlertaRaven necesita acceso a tu ubicación incluso cuando la app no esté abierta.
                
                Esto permite:
                • Detectar accidentes mientras conduces
                • Enviar tu ubicación exacta a contactos de emergencia
                • Funcionar en segundo plano sin interrupciones
                
                En la siguiente pantalla, selecciona "Permitir todo el tiempo" para obtener la máxima protección.
            """.trimIndent())
            .setPositiveButton("Entendido") { _, _ -> onAccept() }
            .setNegativeButton("Omitir") { _, _ -> onDeny() }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Muestra un diálogo de éxito cuando todos los permisos están concedidos
     */
    fun showPermissionsGrantedDialog(
        context: Context,
        onContinue: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("¡Configuración Completa!")
            .setMessage("""
                ✅ Todos los permisos han sido concedidos correctamente.
                
                AlertaRaven está listo para protegerte. La app ahora puede:
                • Detectar accidentes automáticamente
                • Enviar tu ubicación a contactos de emergencia
                • Llamar a servicios de emergencia si es necesario
                • Funcionar en segundo plano
                
                ¡Tu seguridad está garantizada!
            """.trimIndent())
            .setPositiveButton("Continuar") { _, _ -> onContinue() }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Muestra un diálogo de advertencia cuando permisos críticos están denegados
     */
    fun showCriticalPermissionsMissingDialog(
        context: Context,
        onGoToSettings: () -> Unit,
        onContinueAnyway: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("⚠️ Funcionalidad Limitada")
            .setMessage("""
                Sin los permisos esenciales, AlertaRaven no puede garantizar tu protección completa.
                
                Funciones afectadas:
                • No se pueden detectar accidentes
                • No se pueden enviar alertas de emergencia
                • No se puede acceder a tu ubicación
                
                Te recomendamos encarecidamente conceder estos permisos para tu seguridad.
            """.trimIndent())
            .setPositiveButton("Ir a Configuración") { _, _ -> onGoToSettings() }
            .setNegativeButton("Continuar de todos modos") { _, _ -> onContinueAnyway() }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Muestra un diálogo informativo sobre notificaciones (Android 13+)
     */
    fun showNotificationPermissionDialog(
        context: Context,
        onAccept: () -> Unit,
        onDeny: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("Notificaciones Importantes")
            .setMessage("""
                AlertaRaven necesita enviar notificaciones para:
                
                🚨 Alertarte sobre detecciones de accidentes
                ⚡ Mostrarte el estado de la protección activa
                🔋 Informarte sobre el estado de la batería
                📱 Confirmar que los mensajes de emergencia se enviaron
                
                Estas notificaciones son cruciales para tu seguridad.
            """.trimIndent())
            .setPositiveButton("Permitir Notificaciones") { _, _ -> onAccept() }
            .setNegativeButton("No permitir") { _, _ -> onDeny() }
            .setCancelable(false)
            .show()
    }
}