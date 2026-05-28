package com.virgil.app.service

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PanicResponderTest {

    @Test
    fun `trigger action matches the PanicKit wire constant`() {
        assertEquals(
            "info.guardianproject.panic.action.TRIGGER",
            PanicResponder.ACTION_TRIGGER,
        )
    }

    @Test
    fun `connect and disconnect actions match the PanicKit wire constants`() {
        assertEquals(
            "info.guardianproject.panic.action.CONNECT",
            PanicResponder.ACTION_CONNECT,
        )
        assertEquals(
            "info.guardianproject.panic.action.DISCONNECT",
            PanicResponder.ACTION_DISCONNECT,
        )
    }

    @Test
    fun `trigger is ignored when no app is paired`() {
        assertFalse(PanicResponder.shouldHandleTrigger(emptySet()))
    }

    @Test
    fun `trigger is honoured when at least one app is paired`() {
        assertTrue(PanicResponder.shouldHandleTrigger(setOf("info.guardianproject.ripple")))
        assertTrue(
            PanicResponder.shouldHandleTrigger(
                setOf("info.guardianproject.ripple", "org.havenapp.main"),
            )
        )
    }
}
