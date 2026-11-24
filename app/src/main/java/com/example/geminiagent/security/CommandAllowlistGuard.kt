package com.example.geminiagent.security

import android.content.Context
import java.io.File
import java.util.Locale

data class CommandEvaluation(
    val isAllowed: Boolean,
    val riskLevel: RiskLevel,
    val reason: String
)

object CommandAllowlistGuard {

    // Strictly blocked destructive command patterns (never permitted)
    private val LETHAL_PATTERNS = listOf(
        Regex("""rm\s+-[a-zA-Z]*r[a-zA-Z]*f?\s+/\s*"""),     // rm -rf /
        Regex("""rm\s+-[a-zA-Z]*r[a-zA-Z]*f?\s+/\*"""),    // rm -rf /*
        Regex("""rm\s+-[a-zA-Z]*r[a-zA-Z]*f?\s+~\s*"""),    // rm -rf ~
        Regex("""mkfs(\.[a-zA-Z0-9]+)?\s+"""),               // filesystem formatting
        Regex("""dd\s+if=/dev/(zero|urandom|null)\s+of=/dev/"""), // disk wiping
        Regex(""":\(\)\s*\{\s*:\s*\|\s*:\s*&\s*\}\s*;\s*:"""), // fork bomb
        Regex("""reboot\s+(recovery|bootloader)"""),
        Regex("""chmod\s+-[a-zA-Z]*R\s+777\s+/"""),
        Regex("""pm\s+uninstall\s+(com\.android\.|com\.google\.android\.)"""),
        Regex("""curl\s+.*\|\s*(ba)?sh"""),                   // curl pipe to shell
        Regex("""wget\s+.*\|\s*(ba)?sh""")
    )

    // High risk command triggers (require HITL Step-Up confirmation if enabled)
    private val HIGH_RISK_COMMANDS = setOf(
        "rm", "reboot", "pm", "kill", "pkill", "settings", "su", "wipe", "factory_reset"
    )

    // Positive allowlist of standard safe command utilities
    private val SAFE_COMMAND_UTILITIES = setOf(
        "ls", "cat", "echo", "pwd", "date", "whoami", "uname", "id",
        "grep", "find", "stat", "head", "tail", "wc", "sort", "uniq",
        "df", "free", "ps", "top", "uptime", "printenv", "env",
        "git", "python", "python3", "sh", "mkdir", "cp", "mv", "touch",
        "dumpsys", "input", "am"
    )

    fun evaluateCommand(command: String): CommandEvaluation {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return CommandEvaluation(true, RiskLevel.SAFE, "Empty command")
        }

        // 1. Check for lethal patterns
        for (pattern in LETHAL_PATTERNS) {
            if (pattern.containsMatchIn(trimmed)) {
                return CommandEvaluation(
                    isAllowed = false,
                    riskLevel = RiskLevel.BLOCKED,
                    reason = "Lethal system-destructive pattern detected and blocked by security policy."
                )
            }
        }

        // 2. Extract primary binary name
        val primaryCmd = trimmed
            .split(" ", ";", "&&", "||", "|", "\n")
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.substringAfterLast('/')
            ?.lowercase(Locale.ROOT) ?: ""

        // 3. High risk checks
        if (HIGH_RISK_COMMANDS.contains(primaryCmd) || trimmed.contains("rm ") || trimmed.contains("pm uninstall")) {
            return CommandEvaluation(
                isAllowed = true,
                riskLevel = RiskLevel.HIGH_RISK,
                reason = "Command modifies system state or deletes data ('$primaryCmd')."
            )
        }

        // 4. Check if within safe allowlist
        if (SAFE_COMMAND_UTILITIES.contains(primaryCmd)) {
            val isReadMod = primaryCmd in setOf("mkdir", "cp", "mv", "touch", "git")
            return CommandEvaluation(
                isAllowed = true,
                riskLevel = if (isReadMod) RiskLevel.MODERATE else RiskLevel.SAFE,
                reason = if (isReadMod) "Standard filesystem modification utility" else "Safe diagnostic / inspection utility"
            )
        }

        // Default unknown binaries to moderate risk
        return CommandEvaluation(
            isAllowed = true,
            riskLevel = RiskLevel.MODERATE,
            reason = "Standard execution utility ('$primaryCmd')"
        )
    }

    fun isPathWithinWorkspace(context: Context, targetPath: String): Boolean {
        return try {
            val workspace = File(context.filesDir, "workspace").canonicalFile
            val file = if (targetPath.startsWith("/")) File(targetPath).canonicalFile else File(workspace, targetPath).canonicalFile
            file.path.startsWith(workspace.path)
        } catch (_: Exception) {
            false
        }
    }
}
