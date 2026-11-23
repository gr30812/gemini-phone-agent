package com.example.geminiagent.os

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

object ShizukuBridge {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_REQUEST_CODE = 8801

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            if (!isShizukuRunning()) return false
            if (Shizuku.isPreV11()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission() {
        try {
            if (isShizukuRunning() && !hasPermission()) {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
        } catch (_: Exception) {}
    }

    fun executeAdbCommand(command: String): String {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return "Error: Command cannot be empty."

        if (!isShizukuRunning()) {
            return "Error: Shizuku service is not running on this phone.\n" +
                   "To use elevated ADB commands without root:\n" +
                   "1. Open the Shizuku app.\n" +
                   "2. Start it using Wireless Debugging in Developer Options (one-time setup).\n" +
                   "3. Return to Antigravity Agent and authorize permission."
        }

        if (!hasPermission()) {
            return "Error: Shizuku permission not yet authorized for Antigravity Agent.\n" +
                   "Please tap 'Authorize Shizuku' in OS Permissions menu or open Shizuku -> Authorized Applications and toggle on Antigravity Agent."
        }

        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", trimmed), null, null) as java.lang.Process
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            process.waitFor(30, TimeUnit.SECONDS)

            val sb = StringBuilder()
            if (stdout.isNotBlank()) sb.append(stdout.trim())
            if (stderr.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append("\n[stderr]: ")
                sb.append(stderr.trim())
            }
            if (sb.isEmpty()) "Command executed successfully with elevated ADB shell privileges (exit code: ${process.exitValue()})." else sb.toString()
        } catch (e: Exception) {
            "ADB Execution Error: ${e.message}"
        }
    }

    fun openShizukuApp(context: Context) {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            // Open Play Store or Shizuku official guide
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        }
    }
}
