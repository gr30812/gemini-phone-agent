package com.example.geminiagent.skills

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

object AntigravitySdkBridge {

    fun executePythonScript(context: Context, scriptContent: String): String {
        return try {
            val scriptFile = File(context.cacheDir, "temp_antigravity_script.py")
            scriptFile.writeText(scriptContent)

            // Attempt execution via python3 or sh
            val pb = ProcessBuilder("python3", scriptFile.absolutePath)
                .directory(context.filesDir)
                .redirectErrorStream(true)

            val proc = try {
                pb.start()
            } catch (_: Exception) {
                // Fallback to sh
                ProcessBuilder("sh", "-c", "python3 ${scriptFile.absolutePath} || python ${scriptFile.absolutePath}")
                    .directory(context.filesDir)
                    .redirectErrorStream(true)
                    .start()
            }

            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor(30, TimeUnit.SECONDS)
            scriptFile.delete()

            if (output.isBlank()) "Python script executed successfully with no output." else output.trim()
        } catch (e: Exception) {
            "Antigravity SDK Runner error: ${e.message}\nNote: Ensure Python3 is available via Termux/ADB environment."
        }
    }
}
