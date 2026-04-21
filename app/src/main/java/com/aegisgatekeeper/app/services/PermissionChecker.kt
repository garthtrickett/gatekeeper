package com.aegisgatekeeper.app.services

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.aegisgatekeeper.app.GatekeeperAccessibilityService

data class PermissionStatus(
    val hasOverlay: Boolean,
    val hasUsageAccess: Boolean,
    val hasAccessibility: Boolean,
    val isBatteryDisabled: Boolean,
) {
    val isDualMoatEnabled: Boolean
        get() = hasOverlay && hasUsageAccess && hasAccessibility && isBatteryDisabled
}

/**
 * Pure native Kotlin utility object to explicitly check the granted status of the
 * 4 critical permissions required for the Dual-Moat Interceptor.
 */
object PermissionChecker {
    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasUsageAccessPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasAccessibilityPermission(context: Context): Boolean {
        val componentName = ComponentName(context, GatekeeperAccessibilityService::class.java).flattenToString()
        val enabledServices =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

        return enabledServices.split(":").any { it == componentName }
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun checkAll(context: Context): PermissionStatus =
        PermissionStatus(
            hasOverlay = hasOverlayPermission(context),
            hasUsageAccess = hasUsageAccessPermission(context),
            hasAccessibility = hasAccessibilityPermission(context),
            isBatteryDisabled = isBatteryOptimizationDisabled(context),
        )
}
