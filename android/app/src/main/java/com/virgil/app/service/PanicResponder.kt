package com.virgil.app.service

/**
 * Wire constants and pure-logic helpers for acting as a PanicKit *responder*.
 *
 * Counterpart to [PanicBroadcast], which emits triggers to other responders.
 * Here Virgil receives triggers from external trigger apps (Ripple, Haven,
 * hardware-button companions, etc.) once the user has explicitly paired them
 * via the CONNECT intent flow.
 *
 * **Sender identity on a BroadcastReceiver**: Android does not convey the
 * sender package to a manifest-declared BroadcastReceiver. We therefore can
 * only gate on whether the user has paired *any* PanicKit trigger app —
 * non-empty set ⇒ honour the broadcast. A malicious app could spoof the
 * action, so the security property is "the user has knowingly opted into the
 * PanicKit responder pathway at least once," not "this exact sender is
 * paired." The CONNECT activity uses [android.app.Activity.getCallingPackage]
 * so per-app pairing is precise on the way *in*; it's the trigger fan-out
 * that's necessarily coarse. Documented in docs/COMPLIANCE.md.
 *
 * Wire-compatible with https://github.com/guardianproject/PanicKit without
 * pulling the library as a dependency.
 */
object PanicResponder {

    const val ACTION_TRIGGER = "info.guardianproject.panic.action.TRIGGER"
    const val ACTION_CONNECT = "info.guardianproject.panic.action.CONNECT"
    const val ACTION_DISCONNECT = "info.guardianproject.panic.action.DISCONNECT"

    fun shouldHandleTrigger(connectedSenders: Set<String>): Boolean =
        connectedSenders.isNotEmpty()
}
