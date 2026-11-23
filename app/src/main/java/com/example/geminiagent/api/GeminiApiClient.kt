package com.example.geminiagent.api

import android.content.Context
import com.example.geminiagent.usage.UsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class GeminiApiClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val workspaceDir: File = File(context.filesDir, "workspace").apply { mkdirs() }
    private val history = JSONArray()
    val usageTracker = UsageTracker(context)

    // Available models with fallback
    private val availableModels = listOf("gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.5-pro")

    private fun safePath(path: String): File {
        val target = File(workspaceDir, path).canonicalFile
        if (!target.path.startsWith(workspaceDir.canonicalPath)) {
            throw SecurityException("Access outside workspace is denied.")
        }
        return target
    }

    private fun readFile(path: String): String {
        return try {
            val file = safePath(path)
            if (!file.exists()) "Error: File does not exist." else file.readText()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun writeFile(path: String, content: String): String {
        return try {
            val file = safePath(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            "File '${file.name}' successfully written."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun listFiles(): String {
        return try {
            val files = workspaceDir.listFiles()?.map { it.name } ?: emptyList()
            if (files.isEmpty()) "Workspace is empty." else files.joinToString(", ")
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun getToolsDefinition(): JSONArray {
        val toolsArray = JSONArray()
        val toolObject = JSONObject()
        val funcDeclarations = JSONArray()

        funcDeclarations.put(JSONObject().apply {
            put("name", "read_file")
            put("description", "Reads content from a file in the workspace.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().put("type", "string"))
                })
                put("required", JSONArray().put("path"))
            })
        })

        funcDeclarations.put(JSONObject().apply {
            put("name", "write_file")
            put("description", "Writes content to a file in the workspace.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().put("type", "string"))
                    put("content", JSONObject().put("type", "string"))
                })
                put("required", JSONArray().put("path").put("content"))
            })
        })

        funcDeclarations.put(JSONObject().apply {
            put("name", "list_files")
            put("description", "Lists all files in the agent workspace.")
            put("parameters", JSONObject().apply {
                put("type", "object")
            })
        })

        toolObject.put("function_declarations", funcDeclarations)
        toolsArray.put(toolObject)
        return toolsArray
    }

    data class AgentResponse(
        val text: String,
        val logs: List<String>
    )

    suspend fun sendMessage(
        apiKey: String,
        userText: String,
        selectedModel: String = "gemini-3.8-flash"
    ): AgentResponse = withContext(Dispatchers.IO) {
        val logs = mutableListOf<String>()
        val userTurn = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userText)))
        }
        history.put(userTurn)

        val modelsToTry = buildList {
            add(selectedModel)
            if (selectedModel != "gemini-3.8-flash") add("gemini-3.8-flash")
            if (selectedModel != "gemini-2.5-flash") add("gemini-2.5-flash")
            if (selectedModel != "gemini-2.5-pro") add("gemini-2.5-pro")
        }.distinct()

        var finalReply = ""

        while (true) {
            val payload = JSONObject().apply {
                put("contents", history)
                put("tools", getToolsDefinition())
            }

            var successResponse: JSONObject? = null
            var lastErrorMessage = ""

            modelLoop@ for (model in modelsToTry) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

                for (attempt in 1..2) {
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .post(payload.toString().toRequestBody("application/json".toMediaType()))
                            .build()

                        val response = client.newCall(request).execute()
                        val responseBody = response.body?.string() ?: ""

                        if (response.isSuccessful) {
                            successResponse = JSONObject(responseBody)
                            break@modelLoop
                        }

                        if (response.code == 503 || response.code == 429) {
                            lastErrorMessage = "Model $model experiencing high demand (${response.code})."
                            delay(1500)
                            continue
                        } else {
                            lastErrorMessage = "Error ${response.code}: $responseBody"
                            break
                        }
                    } catch (e: Exception) {
                        lastErrorMessage = "Connection error: ${e.message}"
                        delay(1000)
                    }
                }
            }

            if (successResponse == null) {
                return@withContext AgentResponse(
                    "⚠️ সার্ভারে লোড বেশি থাকায় উত্তর পাওয়া যায়নি। কয়েক সেকেন্ড পর আবার চেষ্টা করুন।\n($lastErrorMessage)",
                    logs
                )
            }

            // Record usage tokens and request stats
            val usageMeta = successResponse.optJSONObject("usageMetadata")
            val tokens = usageMeta?.optLong("totalTokenCount", 0L) ?: 0L
            usageTracker.recordRequest(tokens)

            val candidates = successResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext AgentResponse("No response received from Gemini.", logs)
            }

            val candidateContent = candidates.getJSONObject(0).getJSONObject("content")
            history.put(candidateContent)

            val parts = candidateContent.optJSONArray("parts") ?: JSONArray()
            var functionCallObj: JSONObject? = null

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    finalReply += part.getString("text") + "\n"
                }
                if (part.has("functionCall")) {
                    functionCallObj = part.getJSONObject("functionCall")
                }
            }

            if (functionCallObj == null) {
                break
            }

            val fnName = functionCallObj.getString("name")
            val fnArgs = functionCallObj.optJSONObject("args") ?: JSONObject()

            val toolResult: String = when (fnName) {
                "read_file" -> {
                    val p = fnArgs.optString("path", "")
                    logs.add("Reading file: $p")
                    readFile(p)
                }
                "write_file" -> {
                    val p = fnArgs.optString("path", "")
                    val c = fnArgs.optString("content", "")
                    logs.add("Writing file: $p")
                    writeFile(p, c)
                }
                "list_files" -> {
                    logs.add("Listing files in workspace")
                    listFiles()
                }
                else -> {
                    logs.add("Unknown tool: $fnName")
                    "Unknown tool: $fnName"
                }
            }

            val toolResponseTurn = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().apply {
                    put("functionResponse", JSONObject().apply {
                        put("name", fnName)
                        put("response", JSONObject().put("result", toolResult))
                    })
                }))
            }
            history.put(toolResponseTurn)
        }

        AgentResponse(finalReply.trim().ifEmpty { "Task completed." }, logs)
    }
}
