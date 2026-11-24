package com.example.geminiagent.security

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class RiskLevel {
    SAFE,
    MODERATE,
    HIGH_RISK,
    BLOCKED
}

data class AuditLogEntry(
    val id: String = UUID.randomUUID().toString().take(8),
    val timestamp: Long = System.currentTimeMillis(),
    val timeFormatted: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
    val toolName: String,
    val detail: String,
    val riskLevel: RiskLevel,
    val hitlApproved: Boolean,
    val status: String
)

object SecurityAuditLogger {

    private const val MAX_LOGS = 200

    private fun getStorageFile(context: Context): File {
        val dir = File(context.filesDir, "security")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "audit_log.json")
    }

    @Synchronized
    fun logEvent(
        context: Context,
        toolName: String,
        detail: String,
        riskLevel: RiskLevel,
        hitlApproved: Boolean = false,
        status: String = "SUCCESS"
    ): AuditLogEntry {
        val entry = AuditLogEntry(
            toolName = toolName,
            detail = detail.take(500),
            riskLevel = riskLevel,
            hitlApproved = hitlApproved,
            status = status
        )

        val logs = getRecentLogs(context).toMutableList()
        logs.add(0, entry)
        while (logs.size > MAX_LOGS) {
            logs.removeAt(logs.size - 1)
        }

        try {
            val arr = JSONArray()
            logs.forEach { e ->
                arr.put(JSONObject().apply {
                    put("id", e.id)
                    put("timestamp", e.timestamp)
                    put("timeFormatted", e.timeFormatted)
                    put("toolName", e.toolName)
                    put("detail", e.detail)
                    put("riskLevel", e.riskLevel.name)
                    put("hitlApproved", e.hitlApproved)
                    put("status", e.status)
                })
            }
            getStorageFile(context).writeText(arr.toString())
        } catch (_: Exception) {}

        return entry
    }

    @Synchronized
    fun getRecentLogs(context: Context, limit: Int = 100): List<AuditLogEntry> {
        val file = getStorageFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            val list = mutableListOf<AuditLogEntry>()
            for (i in 0 until arr.length().coerceAtMost(limit)) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AuditLogEntry(
                        id = obj.getString("id"),
                        timestamp = obj.optLong("timestamp", 0L),
                        timeFormatted = obj.optString("timeFormatted", ""),
                        toolName = obj.optString("toolName", "unknown"),
                        detail = obj.optString("detail", ""),
                        riskLevel = try {
                            RiskLevel.valueOf(obj.optString("riskLevel", RiskLevel.SAFE.name))
                        } catch (_: Exception) {
                            RiskLevel.SAFE
                        },
                        hitlApproved = obj.optBoolean("hitlApproved", false),
                        status = obj.optString("status", "SUCCESS")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clearLogs(context: Context) {
        val file = getStorageFile(context)
        if (file.exists()) file.delete()
    }
}
