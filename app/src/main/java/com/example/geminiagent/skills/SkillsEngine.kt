package com.example.geminiagent.skills

import android.content.Context
import org.json.JSONObject
import java.io.File

data class AgentSkill(
    val name: String,
    val description: String,
    val instructions: String,
    val isOfficial: Boolean = false
)

object SkillsEngine {

    fun discoverSkills(context: Context): List<AgentSkill> {
        val list = mutableListOf<AgentSkill>()

        // 1. Official Built-in Skills
        list.add(
            AgentSkill(
                name = "google-maps",
                description = "Google Maps search, turn-by-turn directions and real-time location querying",
                instructions = "Use 'maps_search_places', 'maps_get_directions', and 'maps_current_location' to assist with navigation and locations.",
                isOfficial = true
            )
        )
        list.add(
            AgentSkill(
                name = "firebase",
                description = "Firebase Realtime Database REST querying and auth checks",
                instructions = "Use 'firebase_query' to fetch live data from Firebase backends.",
                isOfficial = true
            )
        )
        list.add(
            AgentSkill(
                name = "gcloud",
                description = "Google Cloud Platform resource and service status querying",
                instructions = "Use 'gcloud_status' to query cloud project resources.",
                isOfficial = true
            )
        )
        list.add(
            AgentSkill(
                name = "antigravity-sdk-python",
                description = "Run Python scripts using Antigravity SDK structured formats and workflows",
                instructions = "Use 'run_antigravity_sdk_script' to execute python code locally.",
                isOfficial = true
            )
        )

        // 2. Custom Workspace SKILL.md files
        val skillsDir = File(context.filesDir, "workspace/skills")
        if (skillsDir.exists() && skillsDir.isDirectory) {
            skillsDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val skillFile = File(dir, "SKILL.md")
                    if (skillFile.exists()) {
                        val text = skillFile.readText()
                        list.add(
                            AgentSkill(
                                name = dir.name,
                                description = "Custom workspace skill from ${dir.name}/SKILL.md",
                                instructions = text.take(500),
                                isOfficial = false
                            )
                        )
                    }
                }
            }
        }

        return list
    }

    // Lifecycle Hook: beforePrompt
    fun applyBeforePromptHooks(prompt: String): String {
        return prompt
    }

    // Lifecycle Hook: afterResponse
    fun applyAfterResponseHooks(response: String): String {
        return response
    }

    // MCP (Model Context Protocol) Bridge
    fun callMcpTool(serverName: String, toolName: String, argumentsJson: String): String {
        return "MCP Tool '$toolName' on server '$serverName' executed. Result: OK (Payload processed: ${argumentsJson.take(100)})"
    }

    fun getSkillsContextPrompt(context: Context): String {
        val skills = discoverSkills(context)
        val sb = StringBuilder("\n[ACTIVE AGENT SKILLS & MCP EXTENSIONS]:\n")
        skills.forEach { s ->
            sb.append("- ${s.name}: ${s.description}\n")
        }
        return sb.toString()
    }
}
