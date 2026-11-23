package com.example.geminiagent.usage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class UsageStats(
    val dailyPercentLeft: Int,
    val dailyUsed: Int,
    val dailyLimit: Int,
    val dailyResetsIn: String,
    val fiveHourPercentLeft: Int,
    val fiveHourUsed: Int,
    val fiveHourLimit: Int,
    val fiveHourResetsIn: String,
    val totalTokens: Long
)

class UsageTracker(context: Context) {

    private val prefs = context.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DAILY_LIMIT = 1500      // Gemini API free tier: 1500 req/day
        const val FIVE_HOUR_LIMIT = 300   // Recommended 5h safe window
    }

    @Synchronized
    fun recordRequest(tokens: Long = 0) {
        val now = System.currentTimeMillis()
        val historyStr = prefs.getString("request_history", "[]") ?: "[]"
        val array = try { JSONArray(historyStr) } catch (e: Exception) { JSONArray() }

        val obj = JSONObject().apply {
            put("t", now)
            put("tokens", tokens)
        }
        array.put(obj)

        // Keep last 48 hours only
        val cutoff = now - (48 * 60 * 60 * 1000L)
        val filtered = JSONArray()
        var totalTokensAccum = prefs.getLong("total_tokens_accum", 0L) + tokens

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            if (item.getLong("t") >= cutoff) {
                filtered.put(item)
            }
        }

        prefs.edit()
            .putString("request_history", filtered.toString())
            .putLong("total_tokens_accum", totalTokensAccum)
            .apply()
    }

    fun getUsageStats(): UsageStats {
        val now = System.currentTimeMillis()
        val historyStr = prefs.getString("request_history", "[]") ?: "[]"
        val array = try { JSONArray(historyStr) } catch (e: Exception) { JSONArray() }

        val fiveHourCutoff = now - (5 * 60 * 60 * 1000L)
        val twentyFourHourCutoff = now - (24 * 60 * 60 * 1000L)

        var fiveHourCount = 0
        var dailyCount = 0
        var oldestInFiveHours = now
        var oldestInDaily = now

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val t = item.getLong("t")

            if (t >= fiveHourCutoff) {
                fiveHourCount++
                if (t < oldestInFiveHours) oldestInFiveHours = t
            }
            if (t >= twentyFourHourCutoff) {
                dailyCount++
                if (t < oldestInDaily) oldestInDaily = t
            }
        }

        val dailyPercent = (((DAILY_LIMIT - dailyCount).coerceAtLeast(0).toFloat() / DAILY_LIMIT) * 100).toInt()
        val fiveHourPercent = (((FIVE_HOUR_LIMIT - fiveHourCount).coerceAtLeast(0).toFloat() / FIVE_HOUR_LIMIT) * 100).toInt()

        val fiveHourResetMillis = (oldestInFiveHours + (5 * 60 * 60 * 1000L) - now).coerceAtLeast(0)
        val fiveHourHours = (fiveHourResetMillis / (1000 * 60 * 60)).toInt()
        val fiveHourMinutes = ((fiveHourResetMillis / (1000 * 60)) % 60).toInt()
        val fiveHourResetsIn = if (fiveHourCount > 0) "${fiveHourHours}h ${fiveHourMinutes}m" else "5h 0m"

        val dailyResetMillis = (oldestInDaily + (24 * 60 * 60 * 1000L) - now).coerceAtLeast(0)
        val dailyHours = (dailyResetMillis / (1000 * 60 * 60)).toInt()
        val dailyMinutes = ((dailyResetMillis / (1000 * 60)) % 60).toInt()
        val dailyResetsIn = if (dailyCount > 0) "${dailyHours}h ${dailyMinutes}m" else "23h 59m"

        val totalTokens = prefs.getLong("total_tokens_accum", 0L)

        return UsageStats(
            dailyPercentLeft = dailyPercent,
            dailyUsed = dailyCount,
            dailyLimit = DAILY_LIMIT,
            dailyResetsIn = dailyResetsIn,
            fiveHourPercentLeft = fiveHourPercent,
            fiveHourUsed = fiveHourCount,
            fiveHourLimit = FIVE_HOUR_LIMIT,
            fiveHourResetsIn = fiveHourResetsIn,
            totalTokens = totalTokens
        )
    }
}
