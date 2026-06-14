package com.virgil.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * OEM survival aids. Aggressive battery managers (MIUI, One UI, ColorOS, …)
 * stop background services, silently switching the guardian off. These help the
 * user grant the exemptions that keep Virgil running.
 *
 * Both routes are permission-free deep links into system UI. We deliberately do
 * NOT use REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (a Play-restricted permission
 * that pops a one-tap dialog) — adding it is a manifest + policy decision the
 * project hasn't approved. We never flip a setting on the user's behalf; we only
 * take them to the right screen.
 */
object BatteryGuard {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** The system battery-optimisation list. Permission-free; user finds Virgil. */
    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /**
     * This device's auto-start / background-launch manager, if it has a known
     * one that resolves. Component names per dontkillmyapp.com; resolved before
     * returning because they are undocumented and drift across ROM versions.
     */
    fun oemAutoStartIntent(context: Context): Intent? {
        val component = AUTO_START_COMPONENTS[Build.MANUFACTURER.lowercase()] ?: return null
        val intent = Intent().setComponent(ComponentName(component.first, component.second))
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }

    private val MIUI = "com.miui.securitycenter" to
        "com.miui.permcenter.autostart.AutoStartManagementActivity"
    private val HUAWEI = "com.huawei.systemmanager" to
        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
    private val COLOROS = "com.coloros.safecenter" to
        "com.coloros.safecenter.permission.startup.StartupAppListActivity"

    private val AUTO_START_COMPONENTS = mapOf(
        "xiaomi" to MIUI,
        "redmi" to MIUI,
        "poco" to MIUI,
        "huawei" to HUAWEI,
        "honor" to HUAWEI,
        "oppo" to COLOROS,
        "realme" to COLOROS,
        "vivo" to ("com.vivo.permissionmanager" to
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        "oneplus" to ("com.oneplus.security" to
            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
    )
}
