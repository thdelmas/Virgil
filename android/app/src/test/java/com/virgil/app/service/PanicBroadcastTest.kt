package com.virgil.app.service

import org.junit.Test
import kotlin.test.assertEquals

class PanicBroadcastTest {

    // Interop contract: this exact string is what the PanicKit ecosystem
    // (Ripple, Haven, secure-wipe responders) listens for. If it drifts,
    // Virgil stops reaching every panic receiver silently. See PanicKit
    // Panic.ACTION_TRIGGER — https://github.com/guardianproject/PanicKit.
    @Test
    fun `trigger action matches the PanicKit wire constant`() {
        assertEquals(
            "info.guardianproject.panic.action.TRIGGER",
            PanicBroadcast.ACTION_TRIGGER,
        )
    }
}
