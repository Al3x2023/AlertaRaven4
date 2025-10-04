package com.example.alertaraven4.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsManagerInstrumentedTest {

    private lateinit var settingsManager: SettingsManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settingsManager = SettingsManager(context)
        settingsManager.resetToDefaults()
    }

    @Test
    fun reportTrainingData_togglePersists() {
        assertFalse(settingsManager.isReportTrainingDataEnabled())
        settingsManager.setReportTrainingDataEnabled(true)
        assertTrue(settingsManager.isReportTrainingDataEnabled())
    }
}