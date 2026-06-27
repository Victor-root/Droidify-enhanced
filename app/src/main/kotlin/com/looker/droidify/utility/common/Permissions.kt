package com.looker.droidify.utility.common

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.looker.droidify.utility.common.extension.intent
import com.looker.droidify.utility.common.extension.powerManager

fun Context.isIgnoreBatteryEnabled() =
    powerManager?.isIgnoringBatteryOptimizations(packageName) == true

/** Whether the app may install APKs from "unknown sources". Always true below Android 8, where it
 *  isn't gated per app; needed by the default/session installer. */
fun Context.canRequestPackageInstalls(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

/** Opens the system page where the user allows this app to install unknown apps. No-op below O. */
fun Context.openUnknownAppSourcesSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val intent = intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES) {
        data = "package:$packageName".toUri()
    }
    runCatching { startActivity(intent) }
}

fun Context.requestBatteryFreedom() {
    if (!isIgnoreBatteryEnabled()) {
        val intent = intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
            data = "package:$packageName".toUri()
        }
        runCatching {
            startActivity(intent)
        }
    }
}

fun Activity.requestNotificationPermission(
    request: (permission: String) -> Unit,
    onGranted: () -> Unit = {},
) {
    when {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED -> {
            onGranted()
        }

        shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
            sdkAbove(Build.VERSION_CODES.TIRAMISU) {
                request(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        else -> {
            sdkAbove(Build.VERSION_CODES.TIRAMISU) {
                request(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
