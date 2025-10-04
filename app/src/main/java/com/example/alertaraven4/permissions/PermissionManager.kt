package com.example.alertaraven4.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Gestor de permisos para AlertaRaven
 */
class PermissionManager(
    private val activity: ComponentActivity,
    private val onPermissionsResult: (allGranted: Boolean, deniedPermissions: List<String>) -> Unit
) {
    companion object {
        private const val TAG = "PermissionManager"
        
        // Permisos críticos que necesita la app
        val CRITICAL_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE
        )
        
        // Permisos adicionales según la versión de Android
        val CONDITIONAL_PERMISSIONS = mapOf(
            Build.VERSION_CODES.TIRAMISU to listOf(Manifest.permission.POST_NOTIFICATIONS),
            Build.VERSION_CODES.Q to listOf(Manifest.permission.ACTIVITY_RECOGNITION)
        )
        
        // Permisos de ubicación en segundo plano (requieren ubicación normal primero)
        val BACKGROUND_LOCATION_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    }
    
    private var currentPermissionIndex = 0
    private var permissionsToRequest = mutableListOf<String>()
    private var deniedPermissions = mutableListOf<String>()
    
    // Launcher para solicitar permisos múltiples
    private val multiplePermissionsLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            handlePermissionsResult(permissions)
        }
    
    // Launcher para solicitar permiso individual
    private val singlePermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            handleSinglePermissionResult(granted)
        }
    
    // Launcher para configuración de la app
    private val settingsLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Verificar permisos después de volver de configuración
            checkAndRequestPermissions()
        }
    
    /**
     * Inicia el proceso de solicitud de permisos
     */
    fun checkAndRequestPermissions() {
        Log.i(TAG, "Iniciando verificación de permisos")
        
        permissionsToRequest.clear()
        deniedPermissions.clear()
        currentPermissionIndex = 0
        
        // Agregar permisos críticos
        permissionsToRequest.addAll(CRITICAL_PERMISSIONS)
        
        // Agregar permisos condicionales según la versión de Android
        CONDITIONAL_PERMISSIONS.forEach { (minSdk, permissions) ->
            if (Build.VERSION.SDK_INT >= minSdk) {
                permissionsToRequest.addAll(permissions)
            }
        }
        
        // Filtrar permisos ya concedidos
        val permissionsToCheck = permissionsToRequest.filter { permission ->
            !isPermissionGranted(permission)
        }.toMutableList()
        
        if (permissionsToCheck.isEmpty()) {
            Log.i(TAG, "Todos los permisos ya están concedidos")
            // Verificar ubicación en segundo plano por separado
            checkBackgroundLocationPermission()
            return
        }
        
        permissionsToRequest = permissionsToCheck
        Log.i(TAG, "Solicitando ${permissionsToRequest.size} permisos: $permissionsToRequest")
        
        // Solicitar permisos en lote
        multiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }
    
    /**
     * Verifica si un permiso está concedido
     */
    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Maneja el resultado de múltiples permisos
     */
    private fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()
        
        permissions.forEach { (permission, isGranted) ->
            if (isGranted) {
                granted.add(permission)
                Log.i(TAG, "Permiso concedido: $permission")
            } else {
                denied.add(permission)
                Log.w(TAG, "Permiso denegado: $permission")
            }
        }
        
        deniedPermissions.addAll(denied)
        
        if (denied.isEmpty()) {
            Log.i(TAG, "Todos los permisos básicos concedidos")
            // Verificar ubicación en segundo plano
            checkBackgroundLocationPermission()
        } else {
            Log.w(TAG, "Algunos permisos fueron denegados: $denied")
            // Notificar resultado final
            onPermissionsResult(false, deniedPermissions)
        }
    }
    
    /**
     * Maneja el resultado de un permiso individual
     */
    private fun handleSinglePermissionResult(granted: Boolean) {
        if (granted) {
            Log.i(TAG, "Permiso de ubicación en segundo plano concedido")
            onPermissionsResult(deniedPermissions.isEmpty(), deniedPermissions)
        } else {
            Log.w(TAG, "Permiso de ubicación en segundo plano denegado")
            deniedPermissions.add(BACKGROUND_LOCATION_PERMISSION)
            onPermissionsResult(false, deniedPermissions)
        }
    }
    
    /**
     * Verifica y solicita permiso de ubicación en segundo plano
     */
    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!isPermissionGranted(BACKGROUND_LOCATION_PERMISSION)) {
                // Verificar que los permisos de ubicación normal estén concedidos primero
                if (isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    
                    Log.i(TAG, "Solicitando permiso de ubicación en segundo plano")
                    singlePermissionLauncher.launch(BACKGROUND_LOCATION_PERMISSION)
                } else {
                    Log.w(TAG, "No se puede solicitar ubicación en segundo plano sin permisos de ubicación básicos")
                    deniedPermissions.add(BACKGROUND_LOCATION_PERMISSION)
                    onPermissionsResult(false, deniedPermissions)
                }
            } else {
                Log.i(TAG, "Permiso de ubicación en segundo plano ya concedido")
                onPermissionsResult(deniedPermissions.isEmpty(), deniedPermissions)
            }
        } else {
            // En versiones anteriores a Android 10, no se necesita permiso específico
            Log.i(TAG, "Ubicación en segundo plano no requiere permiso específico en esta versión")
            onPermissionsResult(deniedPermissions.isEmpty(), deniedPermissions)
        }
    }
    
    /**
     * Abre la configuración de la aplicación
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        settingsLauncher.launch(intent)
    }

    /**
     * Verifica si el permiso de superposición (mostrar sobre otras apps) está concedido
     */
    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else {
            true
        }
    }

    /**
     * Genera el Intent para solicitar el permiso de superposición
     */
    fun requestOverlayPermission(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasOverlayPermission()) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
        } else {
            null
        }
    }
    
    /**
     * Verifica si se debe mostrar una explicación para un permiso
     */
    fun shouldShowRationale(permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
    
    /**
     * Obtiene una descripción amigable del permiso
     */
    fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION -> 
                "Ubicación: Necesario para detectar tu ubicación en caso de accidente y enviarla a tus contactos de emergencia."
            
            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> 
                "Ubicación en segundo plano: Permite que la app detecte accidentes incluso cuando no la estés usando activamente."
            
            Manifest.permission.SEND_SMS -> 
                "Envío de SMS: Necesario para enviar mensajes de emergencia a tus contactos cuando se detecte un accidente."
            
            Manifest.permission.CALL_PHONE -> 
                "Realizar llamadas: Permite llamar automáticamente a servicios de emergencia si es necesario."
            
            Manifest.permission.POST_NOTIFICATIONS -> 
                "Notificaciones: Necesario para mostrarte alertas importantes y el estado de la detección de accidentes."
            
            Manifest.permission.ACTIVITY_RECOGNITION -> 
                "Reconocimiento de actividad: Ayuda a mejorar la precisión de la detección de accidentes."
            
            else -> "Permiso necesario para el funcionamiento de la aplicación."
        }
    }
    
    /**
     * Verifica si todos los permisos críticos están concedidos
     */
    fun areAllCriticalPermissionsGranted(): Boolean {
        return CRITICAL_PERMISSIONS.all { isPermissionGranted(it) }
    }
}