package com.example.geminiagent.swarm

data class SlashCommandResult(
    val handled: Boolean,
    val transformedPrompt: String,
    val bannerText: String? = null,
    val isBoostMode: Boolean = false
)

object SlashCommands {

    fun parse(input: String): SlashCommandResult {
        val trimmed = input.trim()
        if (!trimmed.startsWith("/")) {
            return SlashCommandResult(handled = false, transformedPrompt = input)
        }

        val parts = trimmed.split(" ", limit = 2)
        val cmd = parts[0].lowercase()
        val rest = if (parts.size > 1) parts[1].trim() else ""

        return when (cmd) {
            "/swarm" -> {
                SwarmCoordinator.clearSwarm()
                SwarmCoordinator.spawnWorker("researcher", "Knowledge & System Scout", task = "Analyze environment and dependencies for: $rest")
                SwarmCoordinator.spawnWorker("executor", "Code & ADB Automator", task = "Execute primary implementation for: $rest")
                SwarmCoordinator.spawnWorker("verifier", "Quality Assurance & Reviewer", task = "Verify correctness and safety of: $rest")
                SlashCommandResult(
                    handled = true,
                    transformedPrompt = "[SWARM MODE ACTIVE] Coordinated task: $rest\n" +
                            "You are the Swarm Coordinator. Coordinate the 3 spawned worker agents (researcher, executor, verifier) to solve this task in parallel. Break down sub-tasks, execute tools, and aggregate findings.",
                    bannerText = "🐝 Swarm Mode: 3 Parallel Agents Active (researcher, executor, verifier)"
                )
            }
            "/teamwork" -> {
                SwarmCoordinator.clearSwarm()
                SwarmCoordinator.spawnWorker("lead-architect", "System Designer", task = "Decompose architecture for: $rest")
                SwarmCoordinator.spawnWorker("specialist", "Domain Implementer", task = "Build core deliverables for: $rest")
                SlashCommandResult(
                    handled = true,
                    transformedPrompt = "[AGENT TEAM MODE ACTIVE] Team Goal: $rest\n" +
                            "Operate as a collaborative Agent Team. Deconstruct requirements, draft modular components, and provide step-by-step verified execution.",
                    bannerText = "👥 Agent Teamwork: 2 Specialized Collaborators Active"
                )
            }
            "/plan" -> {
                SlashCommandResult(
                    handled = true,
                    transformedPrompt = "[PLANNING MODE] Plan Request: $rest\n" +
                            "Generate a comprehensive, hierarchical Markdown Execution Plan with clear Phases, Deliverables, and Markdown Checkboxes (- [ ]). Include risks and direct approval options.",
                    bannerText = "📋 Planning Mode: Generating Hierarchical Execution Plan"
                )
            }
            "/grill-me" -> {
                SlashCommandResult(
                    handled = true,
                    transformedPrompt = "[GRILL-ME INTERVIEW MODE] Topic: $rest\n" +
                            "Do NOT jump straight to code. Act as a Principal Systems Architect. Conduct a concise, rigorous design interview with 3-4 critical clarifying questions to resolve trade-offs, architecture decisions, and constraints.",
                    bannerText = "🎙️ Grill-Me: Interactive Design Alignment Interview"
                )
            }
            "/boost" -> {
                SlashCommandResult(
                    handled = true,
                    transformedPrompt = "[DEEP REASONING BOOST MODE] Goal: $rest\n" +
                            "Engage extended thinking and multi-perspective verification. Analyze edge cases, explore alternative designs, stress-test security, and verify every step before delivering the final answer.",
                    bannerText = "🚀 Boost Mode: Deep Multi-Perspective Reasoning Active",
                    isBoostMode = true
                )
            }
            "/automation" -> {
                SlashCommandResult(
                    handled = true,
                    transformedPrompt = "[SCHEDULED AUTOMATION] Specification: $rest\n" +
                            "Parse schedule timing (e.g. daily, hourly, every X minutes) and task payload, then invoke 'schedule_automation' to register with Android OS.",
                    bannerText = "⏰ Automation Mode: Scheduling Background Task"
                )
            }
            else -> SlashCommandResult(handled = false, transformedPrompt = input)
        }
    }
}
