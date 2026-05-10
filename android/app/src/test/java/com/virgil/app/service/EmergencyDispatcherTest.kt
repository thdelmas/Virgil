package com.virgil.app.service

import android.telephony.SmsManager
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test
import kotlin.test.assertEquals

class EmergencyDispatcherTest {

    @Test
    fun `mapSmsErrorCode distinguishes radio off and no service from generic`() {
        assertEquals(
            EmergencyDispatcher.SmsStatus.FAILED_RADIO_OFF,
            EmergencyDispatcher.mapSmsErrorCode(SmsManager.RESULT_ERROR_RADIO_OFF),
        )
        assertEquals(
            EmergencyDispatcher.SmsStatus.FAILED_NO_SERVICE,
            EmergencyDispatcher.mapSmsErrorCode(SmsManager.RESULT_ERROR_NO_SERVICE),
        )
        assertEquals(
            EmergencyDispatcher.SmsStatus.FAILED_GENERIC,
            EmergencyDispatcher.mapSmsErrorCode(SmsManager.RESULT_ERROR_GENERIC_FAILURE),
        )
    }

    @Test
    fun `TriggerType includes PANIC for manual aggression-or-theft alerts`() {
        // PANIC is the only trigger type that suppresses the auto-call (the
        // siren would scream over the mic). Lock the enum membership so a
        // future "simplification" doesn't quietly delete it.
        val names = EmergencyDispatcher.TriggerType.values().map { it.name }.toSet()
        assertEquals(setOf("FALL", "NO_RESPONSE", "PANIC"), names)
    }

    @Test
    fun `Result derives sent and failed counts from per-contact statuses`() {
        val result = EmergencyDispatcher.Result(
            locationAvailable = true,
            contactResults = listOf(
                EmergencyDispatcher.ContactResult(
                    "A", "+1", EmergencyDispatcher.SmsStatus.SENT,
                ),
                EmergencyDispatcher.ContactResult(
                    "B", "+2", EmergencyDispatcher.SmsStatus.FAILED_RADIO_OFF,
                ),
                EmergencyDispatcher.ContactResult(
                    "C", "+3", EmergencyDispatcher.SmsStatus.FAILED_TIMEOUT,
                ),
            ),
        )
        assertEquals(1, result.smsSent)
        assertEquals(2, result.smsFailed)
    }

    @Test
    fun `formatOffset renders whole-hour positive offsets without minutes`() {
        assertEquals("GMT+2", EmergencyDispatcher.formatOffset(2 * 3600))
    }

    @Test
    fun `formatOffset renders whole-hour negative offsets with minus sign`() {
        assertEquals("GMT-5", EmergencyDispatcher.formatOffset(-5 * 3600))
    }

    @Test
    fun `formatOffset renders UTC as GMT+0`() {
        assertEquals("GMT+0", EmergencyDispatcher.formatOffset(0))
    }

    @Test
    fun `formatOffset renders half-hour offsets with two-digit minutes`() {
        // India standard time: +5:30
        assertEquals("GMT+5:30", EmergencyDispatcher.formatOffset(5 * 3600 + 30 * 60))
    }

    @Test
    fun `formatOffset renders 45-minute offsets`() {
        // Chatham Islands: +12:45
        assertEquals("GMT+12:45", EmergencyDispatcher.formatOffset(12 * 3600 + 45 * 60))
    }

    @Test
    fun `formatOffset renders negative half-hour offsets`() {
        // Newfoundland: -3:30
        assertEquals("GMT-3:30", EmergencyDispatcher.formatOffset(-(3 * 3600 + 30 * 60)))
    }

    @Test
    fun `formatDateTime renders fixed instant in Paris standard time`() {
        // 2026-01-15 14:32 in Europe/Paris (winter → CET, GMT+1)
        val instant = ZonedDateTime.of(2026, 1, 15, 14, 32, 0, 0, ZoneId.of("Europe/Paris"))
            .toInstant()
            .toEpochMilli()
        assertEquals(
            "2026-01-15 14:32 GMT+1",
            EmergencyDispatcher.formatDateTime(instant, ZoneId.of("Europe/Paris")),
        )
    }

    @Test
    fun `formatDateTime renders fixed instant in Paris summer time`() {
        // 2026-07-15 14:32 in Europe/Paris (summer → CEST, GMT+2)
        val instant = ZonedDateTime.of(2026, 7, 15, 14, 32, 0, 0, ZoneId.of("Europe/Paris"))
            .toInstant()
            .toEpochMilli()
        assertEquals(
            "2026-07-15 14:32 GMT+2",
            EmergencyDispatcher.formatDateTime(instant, ZoneId.of("Europe/Paris")),
        )
    }

    @Test
    fun `formatDateTime renders India half-hour offset`() {
        val instant = ZonedDateTime.of(2026, 4, 18, 9, 5, 0, 0, ZoneId.of("Asia/Kolkata"))
            .toInstant()
            .toEpochMilli()
        assertEquals(
            "2026-04-18 09:05 GMT+5:30",
            EmergencyDispatcher.formatDateTime(instant, ZoneId.of("Asia/Kolkata")),
        )
    }

    @Test
    fun `formatDateTime renders UTC`() {
        val instant = ZonedDateTime.of(2026, 4, 18, 12, 0, 0, 0, ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        assertEquals(
            "2026-04-18 12:00 GMT+0",
            EmergencyDispatcher.formatDateTime(instant, ZoneId.of("UTC")),
        )
    }

    @Test
    fun `formatDateTime pads single-digit date components with zero`() {
        val instant = ZonedDateTime.of(2026, 1, 5, 3, 7, 0, 0, ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        assertEquals(
            "2026-01-05 03:07 GMT+0",
            EmergencyDispatcher.formatDateTime(instant, ZoneId.of("UTC")),
        )
    }
}
