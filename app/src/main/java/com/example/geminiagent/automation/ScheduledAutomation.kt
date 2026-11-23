package com.example.geminiagent.automation

data class ScheduledAutomation(
    val id: String,
    val name: String,
    val intervalMinutes: Int,
    val prompt: String,
    val isEnabled: Boolean = true,
    val lastRunTime: Long = 0L,
    val lastResult: String? = null
) {
    val title: String get() = name
}
