package com.example.geminiagent.model

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.geminiagent.artifacts.AgentArtifact
import com.example.geminiagent.artifacts.ArtifactStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ChatHistoryManager {

    private const val MAX_MESSAGES = 300

    private fun getStorageFile(context: Context): File {
        val dir = File(context.filesDir, "chat")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "history.json")
    }

    @Synchronized
    fun saveMessages(context: Context, messages: List<ChatMessage>) {
        try {
            val file = getStorageFile(context)
            val subList = if (messages.size > MAX_MESSAGES) {
                messages.takeLast(MAX_MESSAGES)
            } else {
                messages
            }

            val array = JSONArray()
            for (msg in subList) {
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("sender", msg.sender.name)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                    if (msg.toolLog != null) put("toolLog", msg.toolLog)
                    if (msg.thinkingText != null) put("thinkingText", msg.thinkingText)
                    if (msg.artifact != null) {
                        put("artifact", JSONObject().apply {
                            put("id", msg.artifact.id)
                            put("title", msg.artifact.title)
                            put("summary", msg.artifact.summary)
                            put("content", msg.artifact.content)
                            put("status", msg.artifact.status.name)
                        })
                    }
                }
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (_: Exception) {}
    }

    @Synchronized
    fun loadMessages(context: Context): List<ChatMessage> {
        val file = getStorageFile(context)
        if (!file.exists()) return emptyList()

        return try {
            val text = file.readText()
            val array = JSONArray(text)
            val list = mutableListOf<ChatMessage>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                val senderStr = obj.optString("sender", MessageSender.USER.name)
                val sender = try { MessageSender.valueOf(senderStr) } catch (_: Exception) { MessageSender.USER }
                val msgText = obj.optString("text", "")
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                val toolLog = if (obj.has("toolLog")) obj.getString("toolLog") else null
                val thinkingText = if (obj.has("thinkingText")) obj.getString("thinkingText") else null

                val artifact = if (obj.has("artifact")) {
                    val artObj = obj.getJSONObject("artifact")
                    val artStatusStr = artObj.optString("status", ArtifactStatus.PENDING_REVIEW.name)
                    val artStatus = try { ArtifactStatus.valueOf(artStatusStr) } catch (_: Exception) { ArtifactStatus.PENDING_REVIEW }
                    AgentArtifact(
                        id = artObj.optString("id", ""),
                        title = artObj.optString("title", ""),
                        summary = artObj.optString("summary", ""),
                        content = artObj.optString("content", ""),
                        status = artStatus
                    )
                } else null

                list.add(
                    ChatMessage(
                        id = id,
                        sender = sender,
                        text = msgText,
                        toolLog = toolLog,
                        thinkingText = thinkingText,
                        artifact = artifact,
                        timestamp = timestamp
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clearMessages(context: Context) {
        try {
            val file = getStorageFile(context)
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }

    fun exportToMarkdown(context: Context, messages: List<ChatMessage>): File {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(exportDir, "Antigravity_Chat_$timeStamp.md")

        val sb = StringBuilder()
        sb.append("# Antigravity Mobile Chat Session\n\n")
        sb.append("**Exported**: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        sb.append("**Total Messages**: ${messages.size}\n\n---\n\n")

        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        for (msg in messages) {
            val time = dateFormat.format(Date(msg.timestamp))
            when (msg.sender) {
                MessageSender.USER -> {
                    sb.append("### 👤 User ($time)\n\n")
                    sb.append(msg.text.trim()).append("\n\n")
                }
                MessageSender.AGENT -> {
                    sb.append("### 🤖 Antigravity Agent ($time)\n\n")
                    if (!msg.thinkingText.isNullOrBlank()) {
                        sb.append("> **Thinking Process:**\n> ")
                        sb.append(msg.thinkingText.replace("\n", "\n> ")).append("\n\n")
                    }
                    if (!msg.toolLog.isNullOrBlank()) {
                        sb.append("```shell\n# Tool Activity\n").append(msg.toolLog).append("\n```\n\n")
                    }
                    sb.append(msg.text.trim()).append("\n\n")
                    if (msg.artifact != null) {
                        sb.append("#### 📦 Artifact: ${msg.artifact.title} (${msg.artifact.summary})\n\n")
                        sb.append("```\n").append(msg.artifact.content).append("\n```\n\n")
                    }
                }
                MessageSender.SYSTEM -> {
                    sb.append("### ⚙️ System ($time)\n\n")
                    sb.append(msg.text.trim()).append("\n\n")
                }
            }
            sb.append("---\n\n")
        }

        file.writeText(sb.toString())
        return file
    }

    fun shareChatAsMarkdown(context: Context, messages: List<ChatMessage>) {
        val file = exportToMarkdown(context, messages)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Antigravity Agent Conversation")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Conversation via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
