package com.example.geminiagent.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object AutomationManager {

    private const val PREFS_NAME = "antigravity_automations"
    private const val KEY_AUTOMATIONS = "automations_json"

    fun getAllAutomations(context: Context): List<ScheduledAutomation> = getAutomations(context)

    fun getAutomations(context: Context): List<ScheduledAutomation> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_AUTOMATIONS, "[]") ?: "[]"
        val list = mutableListOf<ScheduledAutomation>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ScheduledAutomation(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        intervalMinutes = obj.getInt("intervalMinutes"),
                        prompt = obj.getString("prompt"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        lastRunTime = obj.optLong("lastRunTime", 0L),
                        lastResult = obj.optString("lastResult", null)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveAutomations(context: Context, list: List<ScheduledAutomation>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("intervalMinutes", a.intervalMinutes)
                put("prompt", a.prompt)
                put("isEnabled", a.isEnabled)
                put("lastRunTime", a.lastRunTime)
                if (a.lastResult != null) put("lastResult", a.lastResult)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AUTOMATIONS, arr.toString())
            .apply()
    }

    fun scheduleAutomation(context: Context, name: String, intervalMinutes: Int, prompt: String): String {
        val id = UUID.randomUUID().toString().take(8)
        val auto = ScheduledAutomation(
            id = id,
            name = name,
            intervalMinutes = intervalMinutes.coerceAtLeast(5),
            prompt = prompt,
            isEnabled = true
        )
        val current = getAutomations(context).toMutableList()
        current.add(auto)
        saveAutomations(context, current)

        // Set Alarm
        setAlarm(context, auto)
        return "Scheduled automation '$name' (ID: $id) to run every $intervalMinutes minutes."
    }

    fun cancelAutomation(context: Context, id: String): String {
        val current = getAutomations(context).toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index == -1) return "Automation ID '$id' not found."
        current.removeAt(index)
        saveAutomations(context, current)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutomationReceiver::class.java).apply {
            action = "com.example.geminiagent.AUTOMATION_TRIGGER"
            putExtra("automation_id", id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        return "Automation '$id' cancelled successfully."
    }

    fun setAlarm(context: Context, auto: ScheduledAutomation) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutomationReceiver::class.java).apply {
            action = "com.example.geminiagent.AUTOMATION_TRIGGER"
            putExtra("automation_id", auto.id)
            putExtra("prompt", auto.prompt)
            putExtra("name", auto.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            auto.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + (auto.intervalMinutes * 60 * 1000L)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}
