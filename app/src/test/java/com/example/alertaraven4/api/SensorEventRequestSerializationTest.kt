package com.example.alertaraven4.api

import com.example.alertaraven4.api.models.SensorEventRequest
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorEventRequestSerializationTest {

    @Test
    fun sensorEventRequest_serializesWithExpectedFieldNames() {
        val request = SensorEventRequest(
            deviceId = "device_123",
            label = "COLLISION",
            predictedLabel = "ROLLOVER",
            predictionConfidence = 0.85,
            accelerationMagnitude = 12.3,
            gyroscopeMagnitude = 4.5,
            accelVariance = 1.1,
            gyroVariance = 0.9,
            accelJerk = 2.0,
            timestamp = "2024-01-01T00:00:00.000Z",
            rawData = mapOf(
                "accel_count" to 50,
                "gyro_count" to 50,
                "window_ms" to 1500L
            )
        )

        val gson = Gson()
        val json = gson.toJson(request)
        val obj = JsonParser.parseString(json).asJsonObject

        assertTrue(obj.has("device_id"))
        assertTrue(obj.has("label"))
        assertTrue(obj.has("predicted_label"))
        assertTrue(obj.has("prediction_confidence"))
        assertTrue(obj.has("acceleration_magnitude"))
        assertTrue(obj.has("gyroscope_magnitude"))
        assertTrue(obj.has("accel_variance"))
        assertTrue(obj.has("gyro_variance"))
        assertTrue(obj.has("accel_jerk"))
        assertTrue(obj.has("timestamp"))
        assertTrue(obj.has("raw_data"))

        assertEquals("ROLLOVER", obj.get("predicted_label").asString)
        assertEquals(0.85, obj.get("prediction_confidence").asDouble, 1e-9)
        assertEquals("COLLISION", obj.get("label").asString)
    }
}