package com.virgil.app.service

import com.virgil.app.service.EmergencyDispatcher.AgeInfo
import org.junit.Test
import kotlin.test.assertEquals

class EmergencyDispatcherTest {

    @Test
    fun `fresh fix is classified as Fresh`() {
        assertEquals(AgeInfo.Fresh, EmergencyDispatcher.classifyAge(5_000))
    }

    @Test
    fun `boundary at one minute exclusive is still Fresh`() {
        assertEquals(AgeInfo.Fresh, EmergencyDispatcher.classifyAge(59_999))
    }

    @Test
    fun `boundary at one minute inclusive becomes Minutes(1)`() {
        assertEquals(AgeInfo.Minutes(1), EmergencyDispatcher.classifyAge(60_000))
    }

    @Test
    fun `minute-old fix returns Minutes with correct count`() {
        assertEquals(AgeInfo.Minutes(12), EmergencyDispatcher.classifyAge(12 * 60_000L))
    }

    @Test
    fun `hour-old fix returns Hours with hours and minutes`() {
        val ageMs = (2 * 3600 + 7 * 60) * 1000L
        assertEquals(AgeInfo.Hours(2, 7), EmergencyDispatcher.classifyAge(ageMs))
    }

    @Test
    fun `negative age is treated as zero (Fresh)`() {
        assertEquals(AgeInfo.Fresh, EmergencyDispatcher.classifyAge(-5_000))
    }
}
