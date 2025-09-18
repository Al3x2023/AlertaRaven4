package com.example.alertaraven4.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Gestor de ubicación para obtener la posición GPS del usuario
 */
class LocationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LocationManager"
        private const val LOCATION_UPDATE_INTERVAL = 10000L // 10 segundos
        private const val FASTEST_LOCATION_INTERVAL = 5000L // 5 segundos
        private const val LOCATION_ACCURACY_THRESHOLD = 50f // 50 metros
    }
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation
    
    private val _isLocationEnabled = MutableStateFlow(false)
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled
    
    private val _locationAccuracy = MutableStateFlow<Float?>(null)
    val locationAccuracy: StateFlow<Float?> = _locationAccuracy
    
    private var locationCallback: LocationCallback? = null
    private var isRequestingUpdates = false
    
    /**
     * Configuración de solicitud de ubicación
     */
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        LOCATION_UPDATE_INTERVAL
    ).apply {
        setMinUpdateIntervalMillis(FASTEST_LOCATION_INTERVAL)
        setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL * 2)
        setWaitForAccurateLocation(true)
    }.build()
    
    /**
     * Verifica si los permisos de ubicación están concedidos
     */
    fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Inicia las actualizaciones de ubicación
     */
    fun startLocationUpdates(): Boolean {
        if (!hasLocationPermissions()) {
            Log.e(TAG, "Permisos de ubicación no concedidos")
            return false
        }
        
        if (isRequestingUpdates) {
            Log.w(TAG, "Las actualizaciones de ubicación ya están activas")
            return true
        }
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                
                locationResult.lastLocation?.let { location ->
                    _currentLocation.value = location
                    _locationAccuracy.value = location.accuracy
                    _isLocationEnabled.value = true
                    
                    Log.d(TAG, "Ubicación actualizada: ${location.latitude}, ${location.longitude} " +
                            "Precisión: ${location.accuracy}m")
                }
            }
            
            override fun onLocationAvailability(availability: LocationAvailability) {
                super.onLocationAvailability(availability)
                _isLocationEnabled.value = availability.isLocationAvailable
                
                if (!availability.isLocationAvailable) {
                    Log.w(TAG, "Ubicación no disponible")
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            isRequestingUpdates = true
            Log.i(TAG, "Actualizaciones de ubicación iniciadas")
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al solicitar ubicación", e)
            return false
        }
    }
    
    /**
     * Detiene las actualizaciones de ubicación
     */
    fun stopLocationUpdates() {
        if (!isRequestingUpdates) {
            return
        }
        
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            locationCallback = null
            isRequestingUpdates = false
            Log.i(TAG, "Actualizaciones de ubicación detenidas")
        }
    }
    
    /**
     * Obtiene la última ubicación conocida
     */
    suspend fun getLastKnownLocation(): Location? {
        if (!hasLocationPermissions()) {
            Log.e(TAG, "Permisos de ubicación no concedidos")
            return null
        }
        
        return suspendCancellableCoroutine { continuation ->
            try {
                val task: Task<Location> = fusedLocationClient.lastLocation
                
                task.addOnSuccessListener { location ->
                    if (location != null) {
                        _currentLocation.value = location
                        _locationAccuracy.value = location.accuracy
                        Log.d(TAG, "Última ubicación obtenida: ${location.latitude}, ${location.longitude}")
                    }
                    continuation.resume(location)
                }
                
                task.addOnFailureListener { exception ->
                    Log.e(TAG, "Error al obtener última ubicación", exception)
                    continuation.resumeWithException(exception)
                }
                
                task.addOnCanceledListener {
                    Log.w(TAG, "Solicitud de ubicación cancelada")
                    continuation.resume(null)
                }
                
            } catch (e: SecurityException) {
                Log.e(TAG, "Error de seguridad al obtener ubicación", e)
                continuation.resumeWithException(e)
            }
        }
    }
    
    /**
     * Obtiene una ubicación de alta precisión para emergencias
     */
    suspend fun getEmergencyLocation(): Location? {
        if (!hasLocationPermissions()) {
            Log.e(TAG, "Permisos de ubicación no concedidos para emergencia")
            return null
        }
        
        // Primero intentar obtener la última ubicación conocida
        val lastLocation = getLastKnownLocation()
        
        // Si la última ubicación es reciente y precisa, usarla
        lastLocation?.let { location ->
            val locationAge = System.currentTimeMillis() - location.time
            if (locationAge < 30000 && location.accuracy <= LOCATION_ACCURACY_THRESHOLD) {
                Log.i(TAG, "Usando última ubicación para emergencia (${location.accuracy}m de precisión)")
                return location
            }
        }
        
        // Si no, solicitar una nueva ubicación de alta precisión
        return suspendCancellableCoroutine { continuation ->
            val emergencyLocationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                0L // Solicitud única
            ).apply {
                setMaxUpdateDelayMillis(15000L) // Timeout de 15 segundos
                setWaitForAccurateLocation(true)
            }.build()
            
            val emergencyCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    
                    locationResult.lastLocation?.let { location ->
                        fusedLocationClient.removeLocationUpdates(this)
                        _currentLocation.value = location
                        _locationAccuracy.value = location.accuracy
                        
                        Log.i(TAG, "Ubicación de emergencia obtenida: ${location.latitude}, ${location.longitude} " +
                                "Precisión: ${location.accuracy}m")
                        
                        continuation.resume(location)
                    }
                }
                
                override fun onLocationAvailability(availability: LocationAvailability) {
                    super.onLocationAvailability(availability)
                    
                    if (!availability.isLocationAvailable) {
                        fusedLocationClient.removeLocationUpdates(this)
                        Log.e(TAG, "Ubicación no disponible para emergencia")
                        continuation.resume(lastLocation) // Fallback a última ubicación conocida
                    }
                }
            }
            
            try {
                fusedLocationClient.requestLocationUpdates(
                    emergencyLocationRequest,
                    emergencyCallback,
                    Looper.getMainLooper()
                )
                
                // Timeout manual
                continuation.invokeOnCancellation {
                    fusedLocationClient.removeLocationUpdates(emergencyCallback)
                }
                
            } catch (e: SecurityException) {
                Log.e(TAG, "Error de seguridad al solicitar ubicación de emergencia", e)
                continuation.resumeWithException(e)
            }
        }
    }
    
    /**
     * Formatea la ubicación para mostrar o enviar
     */
    fun formatLocation(location: Location): String {
        return "Lat: ${String.format("%.6f", location.latitude)}, " +
               "Lng: ${String.format("%.6f", location.longitude)}, " +
               "Precisión: ${String.format("%.1f", location.accuracy)}m"
    }
    
    /**
     * Genera un enlace de Google Maps para la ubicación
     */
    fun getGoogleMapsLink(location: Location): String {
        return "https://maps.google.com/?q=${location.latitude},${location.longitude}"
    }
    
    /**
     * Verifica si la ubicación es lo suficientemente precisa
     */
    fun isLocationAccurate(location: Location): Boolean {
        return location.accuracy <= LOCATION_ACCURACY_THRESHOLD
    }
    
    /**
     * Calcula la distancia entre dos ubicaciones en metros
     */
    fun calculateDistance(location1: Location, location2: Location): Float {
        return location1.distanceTo(location2)
    }
    
    /**
     * Limpia los recursos
     */
    fun cleanup() {
        stopLocationUpdates()
        _currentLocation.value = null
        _isLocationEnabled.value = false
        _locationAccuracy.value = null
    }
}