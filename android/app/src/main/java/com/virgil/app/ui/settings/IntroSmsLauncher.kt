package com.virgil.app.ui.settings

import android.content.Context
import android.widget.Toast
import com.virgil.app.R
import com.virgil.app.data.EmergencyContact
import com.virgil.app.service.EmergencyDispatcher

/**
 * Fires the one-off introduction SMS to a newly-added contact and shows a
 * Toast with the result. Kept in a separate file to keep
 * [EmergencySettingsScreen] below the 500-line cap. See docs/COMPLIANCE.md §10.
 */
internal fun launchIntroSms(context: Context, contact: EmergencyContact) {
    val ok = EmergencyDispatcher(context).sendIntro(contact)
    val resId = if (ok) R.string.intro_sent_toast else R.string.intro_failed_toast
    Toast.makeText(
        context,
        context.getString(resId, contact.name),
        Toast.LENGTH_LONG,
    ).show()
}
