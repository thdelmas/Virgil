package com.virgil.app.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * App-wide locale override, stored in SharedPreferences so it can be read
 * synchronously from `attachBaseContext`. `null` means "follow the system
 * language"; any other value is an IETF language tag (e.g. "fr", "eu").
 */
object AppLocale {

    private const val PREFS = "virgil_locale"
    private const val KEY_CODE = "code"

    fun read(context: Context): String? = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_CODE, null)

    fun write(context: Context, code: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (code.isNullOrBlank()) remove(KEY_CODE) else putString(KEY_CODE, code)
            apply()
        }
    }

    /**
     * Returns a context whose resources resolve in [code], or the original
     * context if [code] is null. Also applies the locale as the JVM default so
     * standalone `Locale.getDefault()` calls (date formatting, number parsing)
     * stay consistent with the UI.
     */
    fun wrap(base: Context, code: String? = read(base)): Context {
        if (code.isNullOrBlank()) return base
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLocales(LocaleList(locale))
        }
        return base.createConfigurationContext(config)
    }
}
