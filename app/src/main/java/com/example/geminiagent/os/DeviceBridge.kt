package com.example.geminiagent.os

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceBridge {

    fun readContacts(context: Context, query: String?, limit: Int = 10): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return "Error: READ_CONTACTS permission is not granted. Please grant permission in settings."
        }

        return try {
            val resolver = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            val selection = if (!query.isNullOrBlank()) {
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            } else null
            val selectionArgs = if (!query.isNullOrBlank()) arrayOf("%$query%") else null
            val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

            val cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder)
                ?: return "No contacts found."

            val results = mutableListOf<String>()
            cursor.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                var count = 0
                while (c.moveToNext() && count < limit) {
                    val name = if (nameIdx != -1) c.getString(nameIdx) else "Unknown"
                    val number = if (numIdx != -1) c.getString(numIdx) else ""
                    results.add("$name: $number")
                    count++
                }
            }

            if (results.isEmpty()) "No contacts found matching '${query ?: ""}'." else results.joinToString("\n")
        } catch (e: Exception) {
            "Error reading contacts: ${e.message}"
        }
    }

    fun makeCall(context: Context, phoneNumber: String): String {
        val cleanNumber = phoneNumber.trim().replace(" ", "").replace("-", "")
        if (cleanNumber.isEmpty()) return "Error: Invalid phone number."

        val hasCallPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        return try {
            val intent = if (hasCallPerm) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            if (hasCallPerm) "Initiating direct call to $cleanNumber..." else "Opened dialer for $cleanNumber (Grant CALL_PHONE permission for direct auto-dial)."
        } catch (e: Exception) {
            "Error placing call: ${e.message}"
        }
    }

    fun readRecentSms(context: Context, limit: Int = 10): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "Error: READ_SMS permission is not granted. Please grant permission in settings."
        }

        return try {
            val resolver = context.contentResolver
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("address", "body", "date")
            val sortOrder = "date DESC LIMIT $limit"

            val cursor = resolver.query(uri, projection, null, null, sortOrder)
                ?: return "No SMS messages found."

            val results = mutableListOf<String>()
            cursor.use { c ->
                val addrIdx = c.getColumnIndex("address")
                val bodyIdx = c.getColumnIndex("body")
                val dateIdx = c.getColumnIndex("date")
                val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

                while (c.moveToNext()) {
                    val sender = if (addrIdx != -1) c.getString(addrIdx) else "Unknown"
                    val body = if (bodyIdx != -1) c.getString(bodyIdx) else ""
                    val timestamp = if (dateIdx != -1) c.getLong(dateIdx) else 0L
                    val timeStr = if (timestamp > 0) dateFormat.format(Date(timestamp)) else ""
                    results.add("[$timeStr] From: $sender\nMessage: $body")
                }
            }

            if (results.isEmpty()) "No recent SMS found." else results.joinToString("\n---\n")
        } catch (e: Exception) {
            "Error reading SMS: ${e.message}"
        }
    }

    fun sendSms(context: Context, phoneNumber: String, messageText: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            // Fallback to launching SMS app
            return try {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")).apply {
                    putExtra("sms_body", messageText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "SEND_SMS permission missing; opened SMS compose screen for $phoneNumber."
            } catch (e: Exception) {
                "Error sending SMS: ${e.message}"
            }
        }

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, messageText, null, null)
            "SMS successfully dispatched to $phoneNumber."
        } catch (e: Exception) {
            "Error dispatching SMS: ${e.message}"
        }
    }

    fun openApp(context: Context, appNameOrPackage: String): String {
        val query = appNameOrPackage.trim().lowercase(Locale.ROOT)
        val pm = context.packageManager

        // First check exact package name
        var intent = pm.getLaunchIntentForPackage(appNameOrPackage.trim())
        var matchedApp = appNameOrPackage

        if (intent == null) {
            // Search all installed launcher applications
            val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in allApps) {
                val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
                val pkg = app.packageName.lowercase(Locale.ROOT)
                if (label.contains(query) || pkg.contains(query)) {
                    intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        matchedApp = pm.getApplicationLabel(app).toString()
                        break
                    }
                }
            }
        }

        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Successfully launched '$matchedApp'."
        } else {
            "Could not find an installed app matching '$appNameOrPackage'."
        }
    }

    fun setAlarm(context: Context, hour: Int, minute: Int, message: String): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message.ifBlank { "Antigravity Agent Alarm" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Alarm successfully scheduled for %02d:%02d (%s).".format(hour, minute, message)
        } catch (e: Exception) {
            "Error setting alarm: ${e.message}"
        }
    }

    fun getDeviceStatus(context: Context): String {
        val sb = StringBuilder()

        // Battery
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, batteryFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale) else -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        sb.append("Battery: $batteryPct% (Charging: $isCharging)\n")

        // Network
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val netType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular (Mobile Data)"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Disconnected / Offline"
        }
        sb.append("Network: $netType\n")

        // Memory (RAM)
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val availMemMb = memInfo.availMem / (1024 * 1024)
        val totalMemMb = memInfo.totalMem / (1024 * 1024)
        sb.append("Memory (RAM): ${availMemMb}MB free of ${totalMemMb}MB\n")

        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val availStorageMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
        val totalStorageMb = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024)
        sb.append("Internal Storage: ${availStorageMb / 1024}GB free of ${totalStorageMb / 1024}GB\n")

        // OS
        sb.append("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) - Device: ${Build.MANUFACTURER} ${Build.MODEL}")

        return sb.toString()
    }
}
