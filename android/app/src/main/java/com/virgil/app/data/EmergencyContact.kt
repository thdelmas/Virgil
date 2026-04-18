package com.virgil.app.data

/**
 * @property languageCode IETF language tag (e.g. "fr"). `null` means "use the
 * app's current language" — so existing contacts from before this field
 * existed simply inherit whatever the user picked app-wide.
 */
data class EmergencyContact(
    val name: String,
    val phone: String,
    val isPrimary: Boolean = false,
    val languageCode: String? = null,
)
