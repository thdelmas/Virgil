package com.virgil.app.data

import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class FalseAlarmSnapshotTest {

    private var originalLocale: Locale = Locale.getDefault()
    private var originalTz: TimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        originalTz = TimeZone.getDefault()
        Locale.setDefault(Locale.forLanguageTag("fr-FR"))
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalTz)
    }

    @Test
    fun `report text carries the triage fields a maintainer needs`() {
        val text = sample().toReportText()

        assertTrue("Report schema: v2" in text, "schema version missing: $text")
        assertTrue("App version: 1.2.3" in text)
        assertTrue("Device: Google Pixel 7" in text)
        assertTrue("Android API: 34" in text)
        assertTrue("App locale: fr-FR" in text)
        assertTrue("Timezone: Europe/Paris" in text)
        assertTrue("Trigger type: impact" in text)
    }

    @Test
    fun `report text never leaks location or contact fields`() {
        val text = sample().toReportText().lowercase()

        assertTrue("latitude" !in text)
        assertTrue("longitude" !in text)
        assertTrue("phone" !in text)
        assertTrue("contact" !in text)
    }

    private fun sample(): FalseAlarmSnapshot = FalseAlarmSnapshot(
        triggeredAt = 1_700_000_000_000L,
        dismissedAt = 1_700_000_012_000L,
        triggerType = "impact",
        peakAccel = 27.5f,
        phaseAtDismiss = 3,
        secondsLeftAtDismiss = 12,
        appVersion = "1.2.3",
        deviceManufacturer = "Google",
        deviceModel = "Pixel 7",
        androidVersion = 34,
    )
}
