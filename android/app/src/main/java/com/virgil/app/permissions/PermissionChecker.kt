package com.virgil.app.permissions

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionChecker {

    enum class Status { Granted, Denied, NotApplicable }

    fun status(context: Context, permission: VirgilPermission): Status {
        if (!permission.appliesOnThisDevice()) return Status.NotApplicable

        return when (permission.kind) {
            VirgilPermission.Kind.Runtime -> runtimeStatus(context, permission.androidName!!)
            VirgilPermission.Kind.ExactAlarm -> exactAlarmStatus(context)
            VirgilPermission.Kind.FullScreenIntent -> fullScreenIntentStatus(context)
        }
    }

    fun statuses(context: Context): List<PermissionState> =
        VirgilPermission.applicable().map { PermissionState(it, status(context, it)) }

    fun isGranted(context: Context, permission: VirgilPermission): Boolean =
        status(context, permission) == Status.Granted

    fun missingMandatory(context: Context): List<VirgilPermission> =
        VirgilPermission.applicable()
            .filter { it.mandatory && status(context, it) == Status.Denied }

    private fun runtimeStatus(context: Context, androidName: String): Status {
        val granted = ContextCompat.checkSelfPermission(context, androidName) ==
            PackageManager.PERMISSION_GRANTED
        return if (granted) Status.Granted else Status.Denied
    }

    private fun exactAlarmStatus(context: Context): Status {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Status.Granted
        val am = context.getSystemService(AlarmManager::class.java) ?: return Status.Denied
        return if (am.canScheduleExactAlarms()) Status.Granted else Status.Denied
    }

    private fun fullScreenIntentStatus(context: Context): Status {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Status.Granted
        val nm = context.getSystemService(NotificationManager::class.java)
            ?: return Status.Denied
        return if (nm.canUseFullScreenIntent()) Status.Granted else Status.Denied
    }
}

data class PermissionState(
    val permission: VirgilPermission,
    val status: PermissionChecker.Status,
)
