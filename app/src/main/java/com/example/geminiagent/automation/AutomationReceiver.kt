package com.example.geminiagent.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.geminiagent.api.GeminiApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutomationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("automation_id") ?: return
        val name = intent.getStringExtra("name") ?: "Antigravity Automation"
        val prompt = intent.getStringExtra("prompt") ?: return

        val automations = AutomationManager.getAutomations(context)
        val auto = automations.find { it.id == id } ?: return
        if (!auto.isEnabled) return

        val prefs = context.getSharedPreferences("gemini_agent_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var summary = "Executed successfully."
                if (apiKey.isNotBlank()) {
                    val client = GeminiApiClient(context)
                    val response = client.sendMessage(apiKey, "[AUTOMATED TASK] $prompt")
                    summary = response.text.take(200)
                }

                // Update last run time & result
                val updatedList = automations.map {
                    if (it.id == id) it.copy(lastRunTime = System.currentTimeMillis(), lastResult = summary) else it
                }
                AutomationManager.saveAutomations(context, updatedList)

                // Reschedule for next occurrence
                AutomationManager.setAlarm(context, auto)

                // Show completion notification
                showNotification(context, name, summary)
            } catch (e: Exception) {
                showNotification(context, name, "Automation failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, title: String, content: String) {
        val channelId = "antigravity_automation_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Antigravity Background Automations",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏰ $title")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(title.hashCode(), notification)
    }
}
