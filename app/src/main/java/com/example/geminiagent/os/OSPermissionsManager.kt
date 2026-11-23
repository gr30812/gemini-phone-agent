package com.example.geminiagent.os

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionStatus(
    val notificationAccess: Boolean,
    val accessibilityService: Boolean,
    val contactsPermission: Boolean,
    val phoneCallPermission: Boolean,
    val smsPermission: Boolean,
    val audioRecordPermission: Boolean
) {
    val allGranted: Boolean
        get() = notificationAccess && accessibilityService && contactsPermission && phoneCallPermission && smsPermission && audioRecordPermission
}

object OSPermissionsManager {

    val REQUIRED_RUNTIME_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS
    )

    fun getStatus(context: Context): PermissionStatus {
        return PermissionStatus(
            notificationAccess = AgentNotificationListenerService.isPermissionGranted(context),
            accessibilityService = AgentAccessibilityService.isServiceEnabled(context),
            contactsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
            phoneCallPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED,
            smsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED,
            audioRecordPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
