package com.example.alertaraven4.ml

import com.example.alertaraven4.data.AccidentType
import com.example.alertaraven4.data.SensorData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccidentMLPredictorTest {

    @Test
    fun predictAccidentProbability_returnsValidPrediction() {
        val predictor = AccidentMLPredictor()

        val now = System.currentTimeMillis()
        val current = SensorData(
            accelerometerX = 12f,
            accelerometerY = 0f,
            accelerometerZ = 12f,
            gyroscopeX = 1f,
            gyroscopeY = 1f,
            gyroscopeZ = 1f,
            timestamp = now
        )

        val recent = (0 until 20).map { i ->
            SensorData(
                accelerometerX = 9f,
                accelerometerY = 0f,
                accelerometerZ = 9f,
                gyroscopeX = 0.5f,
                gyroscopeY = 0.5f,
                gyroscopeZ = 0.5f,
                timestamp = now - (20 - i) * 50L
            )
        }

        val prediction = predictor.predictAccidentProbability(current, recent)

        assertNotNull(prediction)
        assertTrue("Confidence debe estar en [0,1]", prediction.confidence in 0f..1f)
        // Debe contener probabilidades para las 4 clases principales
        val keys = prediction.probabilities.keys
        assertTrue(keys.containsAll(listOf(
            AccidentType.COLLISION,
            AccidentType.ROLLOVER,
            AccidentType.SUDDEN_STOP,
            AccidentType.FALL
        )))

        // El tipo más probable debe existir en el mapa
        assertTrue(keys.contains(prediction.type) || prediction.type == AccidentType.UNKNOWN)
    }

    @Test
    fun accidentTypeName_isUppercase_forLabelCasingConsistency() {
        // Verifica que los nombres del enum sean mayúsculas para enviar al backend
        assertEquals("COLLISION", AccidentType.COLLISION.name)
        assertEquals("SUDDEN_STOP", AccidentType.SUDDEN_STOP.name)
        assertEquals("ROLLOVER", AccidentType.ROLLOVER.name)
        assertEquals("FALL", AccidentType.FALL.name)
    }
}