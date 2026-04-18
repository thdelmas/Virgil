package com.virgil.app.service

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmergencyDispatcherTest {

    @Test
    fun `fresh fix omits age suffix`() {
        val msg = EmergencyDispatcher.formatLocation(48.8566, 2.3522, 12, ageMs = 5_000)
        assertTrue(msg.contains("Location: https://maps.google.com/?q=48.8566,2.3522"))
        assertTrue(!msg.contains("old"))
    }

    @Test
    fun `minute-old fix labels age in minutes`() {
        val msg = EmergencyDispatcher.formatLocation(48.8566, 2.3522, 12, ageMs = 12 * 60_000)
        assertTrue(msg.contains("Location (12 min old):"), "got: $msg")
    }

    @Test
    fun `hour-old fix labels age in hours and minutes`() {
        val ageMs = (2 * 3600 + 7 * 60) * 1000L
        val msg = EmergencyDispatcher.formatLocation(48.8566, 2.3522, 30, ageMs = ageMs)
        assertTrue(msg.contains("Location (2h 7min old):"), "got: $msg")
    }

    @Test
    fun `accuracy is rendered in meters`() {
        val msg = EmergencyDispatcher.formatLocation(0.0, 0.0, 42, ageMs = 0)
        assertTrue(msg.contains("Accuracy: 42m"))
    }

    @Test
    fun `boundary at one minute does not yet show age`() {
        val msg = EmergencyDispatcher.formatLocation(0.0, 0.0, 10, ageMs = 59_999)
        assertTrue(!msg.contains("old"), "got: $msg")
        val msg2 = EmergencyDispatcher.formatLocation(0.0, 0.0, 10, ageMs = 60_000)
        assertEquals(true, msg2.contains("(1 min old)"), "got: $msg2")
    }
}
