package com.example.geminiagent.knowledge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class KnowledgeEntry(
    val id: String = UUID.randomUUID().toString().take(8),
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

object KnowledgeBaseManager {

    private fun getStorageFile(context: Context): File {
        val dir = File(context.filesDir, "knowledge")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "knowledge_base.json")
    }

    fun getAllEntries(context: Context): List<KnowledgeEntry> {
        val file = getStorageFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            val list = mutableListOf<KnowledgeEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
                val tags = mutableListOf<String>()
                for (j in 0 until tagsArr.length()) {
                    tags.add(tagsArr.getString(j))
                }
                list.add(
                    KnowledgeEntry(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        content = obj.getString("content"),
                        tags = tags,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getAllKnowledge(context: Context): List<KnowledgeEntry> = getAllEntries(context)

    fun saveKnowledge(context: Context, key: String, value: String): KnowledgeEntry =
        saveEntry(context, key, value)

    fun deleteKnowledge(context: Context, keyOrId: String): Boolean {
        val entries = getAllEntries(context).toMutableList()
        val removed = entries.removeAll { it.id == keyOrId || it.title == keyOrId }
        if (removed) persist(context, entries)
        return removed
    }

    fun saveEntry(context: Context, title: String, content: String, tags: List<String> = emptyList()): KnowledgeEntry {
        val entries = getAllEntries(context).toMutableList()
        val newEntry = KnowledgeEntry(title = title, content = content, tags = tags)
        entries.add(newEntry)
        persist(context, entries)
        return newEntry
    }

    fun deleteEntry(context: Context, id: String): Boolean {
        val entries = getAllEntries(context).toMutableList()
        val removed = entries.removeAll { it.id == id }
        if (removed) persist(context, entries)
        return removed
    }

    fun searchKnowledge(context: Context, query: String): List<KnowledgeEntry> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return getAllEntries(context)
        return getAllEntries(context).filter {
            it.title.lowercase().contains(q) ||
            it.content.lowercase().contains(q) ||
            it.tags.any { tag -> tag.lowercase().contains(q) }
        }
    }

    fun getKnowledgeContext(context: Context, query: String, maxEntries: Int = 3): String {
        val matches = searchKnowledge(context, query).take(maxEntries)
        if (matches.isEmpty()) return ""
        val sb = StringBuilder("\n[KNOWLEDGE BASE RELEVANT CONTEXT]:\n")
        matches.forEach { e ->
            sb.append("Topic: ${e.title} (Tags: ${e.tags.joinToString()})\n")
            sb.append("Information: ${e.content}\n---\n")
        }
        return sb.toString()
    }

    private fun persist(context: Context, entries: List<KnowledgeEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("title", e.title)
                put("content", e.content)
                put("timestamp", e.timestamp)
                put("tags", JSONArray(e.tags))
            })
        }
        val file = getStorageFile(context)
        file.writeText(arr.toString())
    }
}
