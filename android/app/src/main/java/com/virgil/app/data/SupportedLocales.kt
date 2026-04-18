package com.virgil.app.data

import com.virgil.app.R

/**
 * Languages the app ships translations for. Order here drives the order shown
 * in pickers (English first, then the Iberian peninsula languages).
 */
enum class SupportedLocale(val code: String, val displayNameRes: Int) {
    EN("en", R.string.language_name_en),
    FR("fr", R.string.language_name_fr),
    ES("es", R.string.language_name_es),
    CA("ca", R.string.language_name_ca),
    PT("pt", R.string.language_name_pt),
    EU("eu", R.string.language_name_eu);

    companion object {
        fun fromCode(code: String?): SupportedLocale? =
            entries.firstOrNull { it.code == code }
    }
}
