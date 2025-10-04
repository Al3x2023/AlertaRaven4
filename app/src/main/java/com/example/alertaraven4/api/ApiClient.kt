package com.example.alertaraven4.api

import android.content.Context
import android.util.Log
import com.example.alertaraven4.api.models.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente API para AlertaRaven
 * Maneja toda la comunicación con el servidor de la API
 */
class ApiClient private constructor() {
    
    companion object {
        private const val TAG = "ApiClient"
        private const val DEFAULT_BASE_URL = "https://alertaraven.onrender.com/" // URL base por defecto
        private const val API_KEY = "alertaraven_mobile_key_2024"
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 30L
        private const val WRITE_TIMEOUT = 30L
        
        @Volatile
        private var INSTANCE: ApiClient? = null
        
        fun getInstance(): ApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiClient().also { INSTANCE = it }
            }
        }
    }
    
    private var baseUrl: String = DEFAULT_BASE_URL
    private var apiService: AlertaRavenApiService
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
        .create()
    
    init {
        apiService = createApiService()
    }
    
    /**
     * Configura la URL base de la API
     */
    fun setBaseUrl(url: String) {
        if (url != baseUrl) {
            baseUrl = if (url.endsWith("/")) url else "$url/"
            apiService = createApiService()
            Log.d(TAG, "Base URL actualizada a: $baseUrl")
        }
    }
    
    /**
     * Crea el servicio API con Retrofit
     */
    private fun createApiService(): AlertaRavenApiService {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        return retrofit.create(AlertaRavenApiService::class.java)
    }
    
    /**
     * Envía una alerta de emergencia a la API
     */
    suspend fun sendEmergencyAlert(request: EmergencyAlertRequest): ApiResult<AlertResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Enviando alerta de emergencia: ${request.deviceId}")
                
                val response = apiService.sendEmergencyAlert(
                    request = request,
                    authorization = "Bearer $API_KEY"
                )
                
                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando alerta de emergencia", e)
                ApiResult.NetworkError(e)
            }
        }
    }

    /**
     * Envía un evento de sensores (ventana de datos) a la API
     */
    suspend fun sendSensorEvent(request: SensorEventRequest): ApiResult<SensorEventResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Enviando evento de sensor: ${request.deviceId}")

                val response = apiService.sendSensorEvent(
                    request = request,
                    authorization = "Bearer $API_KEY"
                )

                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando evento de sensor", e)
                ApiResult.NetworkError(e)
            }
        }
    }

    /**
     * Lista eventos de sensores
     */
    suspend fun listSensorEvents(limit: Int = 50, offset: Int = 0): ApiResult<SensorEventsListResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Listando eventos de sensores")

                val response = apiService.listSensorEvents(
                    limit = limit,
                    offset = offset,
                    authorization = "Bearer $API_KEY"
                )

                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error listando eventos de sensores", e)
                ApiResult.NetworkError(e)
            }
        }
    }

    /**
     * Exporta eventos de sensores como CSV
     */
    suspend fun exportSensorEventsCsv(): ApiResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Exportando eventos de sensores (CSV)")

                val response = apiService.exportSensorEventsCsv(
                    authorization = "Bearer $API_KEY"
                )

                if (response.isSuccessful) {
                    val csv = response.body()?.string() ?: ""
                    ApiResult.Success(csv)
                } else {
                    Log.e(TAG, "Error exportando CSV: HTTP ${response.code()} - ${response.message()}")
                    ApiResult.Error("HTTP ${response.code()}", response.code())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exportando eventos de sensores", e)
                ApiResult.NetworkError(e)
            }
        }
    }
    
    /**
     * Obtiene el estado de una alerta
     */
    suspend fun getAlertStatus(alertId: String): ApiResult<AlertStatusResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Obteniendo estado de alerta: $alertId")
                
                val response = apiService.getAlertStatus(
                    alertId = alertId,
                    authorization = "Bearer $API_KEY"
                )
                
                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error obteniendo estado de alerta", e)
                ApiResult.NetworkError(e)
            }
        }
    }
    
    /**
     * Obtiene lista de alertas
     */
    suspend fun getAlerts(
        limit: Int = 50,
        offset: Int = 0,
        deviceId: String? = null,
        status: String? = null
    ): ApiResult<AlertsListResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Obteniendo lista de alertas")
                
                val response = apiService.getAlerts(
                    limit = limit,
                    offset = offset,
                    deviceId = deviceId,
                    status = status,
                    authorization = "Bearer $API_KEY"
                )
                
                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error obteniendo lista de alertas", e)
                ApiResult.NetworkError(e)
            }
        }
    }

    /**
     * Actualiza estado de alerta
     */
    suspend fun updateAlertStatus(alertId: String, status: String): ApiResult<AlertStatusResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Actualizando estado de alerta: $alertId -> $status")

                val response = apiService.updateAlertStatus(
                    alertId = alertId,
                    request = UpdateAlertStatusRequest(status = status),
                    authorization = "Bearer $API_KEY"
                )

                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando estado de alerta", e)
                ApiResult.NetworkError(e)
            }
        }
    }

    /**
     * Obtiene contactos del dispositivo
     */
    suspend fun getDeviceContacts(deviceId: String): ApiResult<DeviceContactsResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Obteniendo contactos del dispositivo: $deviceId")

                val response = apiService.getDeviceContacts(
                    deviceId = deviceId,
                    authorization = "Bearer $API_KEY"
                )

                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error obteniendo contactos del dispositivo", e)
                ApiResult.NetworkError(e)
            }
        }
    }

    /**
     * Reemplaza contactos del dispositivo
     */
    suspend fun replaceDeviceContacts(deviceId: String, contacts: List<EmergencyContact>): ApiResult<DeviceContactsResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Reemplazando contactos del dispositivo: $deviceId")

                val response = apiService.replaceDeviceContacts(
                    deviceId = deviceId,
                    contacts = contacts,
                    authorization = "Bearer $API_KEY"
                )

                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error reemplazando contactos del dispositivo", e)
                ApiResult.NetworkError(e)
            }
        }
    }

    /**
     * Envía alerta en modo debug
     */
    suspend fun sendEmergencyAlertDebug(request: EmergencyAlertRequest): ApiResult<AlertResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Enviando alerta de emergencia (debug)")

                val response = apiService.sendEmergencyAlertDebug(
                    request = request,
                    authorization = "Bearer $API_KEY"
                )

                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando alerta de emergencia (debug)", e)
                ApiResult.NetworkError(e)
            }
        }
    }
    
    /**
     * Verifica la salud de la API
     */
    suspend fun healthCheck(): ApiResult<HealthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Verificando salud de la API")
                
                val response = apiService.healthCheck()
                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error en health check", e)
                ApiResult.NetworkError(e)
            }
        }
    }
    
    /**
     * Obtiene información de la API
     */
    suspend fun getApiInfo(): ApiResult<ApiInfoResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Obteniendo información de la API")
                
                val response = apiService.getApiInfo()
                handleResponse(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error obteniendo información de la API", e)
                ApiResult.NetworkError(e)
            }
        }
    }
    
    /**
     * Verifica la conectividad con la API
     */
    suspend fun checkConnectivity(): ApiConnectionStatus {
        return when (val result = healthCheck()) {
            is ApiResult.Success -> {
                if (result.data.status == "healthy") {
                    ApiConnectionStatus.CONNECTED
                } else {
                    ApiConnectionStatus.ERROR
                }
            }
            is ApiResult.Error -> ApiConnectionStatus.ERROR
            is ApiResult.NetworkError -> ApiConnectionStatus.DISCONNECTED
        }
    }
    
    /**
     * Maneja las respuestas HTTP de Retrofit
     */
    private fun <T> handleResponse(response: Response<T>): ApiResult<T> {
        return if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                Log.d(TAG, "Respuesta exitosa: ${response.code()}")
                ApiResult.Success(body)
            } else {
                Log.w(TAG, "Respuesta exitosa pero body es null")
                ApiResult.Error("Respuesta vacía del servidor", response.code())
            }
        } else {
            Log.e(TAG, "Error en respuesta: HTTP ${response.code()} - ${response.message()}")
            ApiResult.Error("HTTP ${response.code()}", response.code())
        }
    }
    
    /**
     * Obtiene la URL base actual
     */
    fun getCurrentBaseUrl(): String = baseUrl
    
    /**
     * Verifica si la URL base es la por defecto (emulador)
     */
    fun isUsingDefaultUrl(): Boolean = baseUrl == DEFAULT_BASE_URL
}