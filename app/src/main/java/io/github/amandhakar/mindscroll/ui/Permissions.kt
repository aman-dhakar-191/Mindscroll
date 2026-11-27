package io.github.amandhakar.mindscroll.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import io.github.amandhakar.mindscroll.service.IslandListenerService
import io.github.amandhakar.mindscroll.service.TrackerAccessibilityService

/** Current state of everything onboarding asks for. */
data class PermissionState(
    val accessibility: Boolean,
    val notifications: Boolean,
    val batteryUnrestricted: Boolean,
) {
    companion object {
        fun read(context: Context) = PermissionState(
            accessibility = TrackerAccessibilityService.isEnabled(context),
            notifications = notificationsAllowed(context),
            batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
        )

        private fun notificationsAllowed(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        }
    }
}

object PermissionIntents {
    fun accessibilitySettings() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Where "Allow restricted settings" lives (App info → ⋮) on Android 13+. */
    fun appInfo(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun notificationSettings(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /**
     * Where the user gives Mindscroll notification access: straight to its own switch on Android 11+,
     * otherwise the list of apps with notification access.
     */
    fun notificationListenerSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    IslandListenerService.component(context).flattenToString(),
                )
        } else {
            notificationListenerList()
        }

    fun notificationListenerList() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    @SuppressLint("BatteryLife") // Sideloaded app; a counter that gets killed is useless.
    fun ignoreBatteryOptimizations(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
}
