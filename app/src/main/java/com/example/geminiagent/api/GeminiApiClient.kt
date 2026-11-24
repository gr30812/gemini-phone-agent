package com.example.geminiagent.api

import android.content.Context
import com.example.geminiagent.artifacts.AgentArtifact
import com.example.geminiagent.artifacts.ArtifactManager
import com.example.geminiagent.automation.AutomationManager
import com.example.geminiagent.knowledge.KnowledgeBaseManager
import com.example.geminiagent.os.AgentAccessibilityService
import com.example.geminiagent.os.AgentNotificationListenerService
import com.example.geminiagent.os.DeviceBridge
import com.example.geminiagent.os.ShizukuBridge
import com.example.geminiagent.skills.AntigravitySdkBridge
import com.example.geminiagent.skills.GoogleServicesBridge
import com.example.geminiagent.skills.SkillsEngine
import com.example.geminiagent.swarm.SwarmCoordinator
import com.example.geminiagent.usage.UsageTracker
import com.example.geminiagent.security.AppSecurityManager
import com.example.geminiagent.security.CommandAllowlistGuard
import com.example.geminiagent.security.RiskLevel
import com.example.geminiagent.security.SecurityAuditLogger
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

    // Current active models with zero downtime fallback
    private val activeModels = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.1-pro-preview")

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

    private fun executeShell(command: String): String {
        return try {
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(workspaceDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(30, TimeUnit.SECONDS)
            if (output.isBlank()) "Command executed successfully (no output)." else output
        } catch (e: Exception) {
            "Execution error: ${e.message}"
        }
    }

    private fun getToolsDefinition(): JSONArray {
        val toolsArray = JSONArray()
        val toolObject = JSONObject()
        val funcDeclarations = JSONArray()

        // 1. read_file
        funcDeclarations.put(JSONObject().apply {
            put("name", "read_file")
            put("description", "Reads content from a file in the workspace.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "File name or relative path")
                    })
                })
                put("required", JSONArray().put("path"))
            })
        })

        // 2. write_file
        funcDeclarations.put(JSONObject().apply {
            put("name", "write_file")
            put("description", "Writes code or text content to a file in the workspace.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "File name or relative path")
                    })
                    put("content", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Content to write into the file")
                    })
                })
                put("required", JSONArray().put("path").put("content"))
            })
        })

        // 3. list_files
        funcDeclarations.put(JSONObject().apply {
            put("name", "list_files")
            put("description", "Lists all files in the agent workspace.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        // 4. execute_command
        funcDeclarations.put(JSONObject().apply {
            put("name", "execute_command")
            put("description", "Executes a shell command inside the workspace.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("command", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Shell command to run")
                    })
                })
                put("required", JSONArray().put("command"))
            })
        })

        // 5. get_recent_notifications
        funcDeclarations.put(JSONObject().apply {
            put("name", "get_recent_notifications")
            put("description", "Retrieves recently received phone notifications (e.g. WhatsApp, SMS, Telegram, Gmail). Use this to see who messaged the user or read alerts.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("app_filter", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional filter by app name or package (e.g. 'WhatsApp', 'Telegram', 'SMS')")
                    })
                    put("limit", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Maximum number of notifications to return (default 10)")
                    })
                })
            })
        })

        // 6. reply_to_notification
        funcDeclarations.put(JSONObject().apply {
            put("name", "reply_to_notification")
            put("description", "Replies directly to an incoming notification (such as a WhatsApp or SMS message) using its notification ID.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("notification_id", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The ID of the notification obtained from get_recent_notifications")
                    })
                    put("reply_text", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The text message reply to send")
                    })
                })
                put("required", JSONArray().put("notification_id").put("reply_text"))
            })
        })

        // 7. read_screen_content
        funcDeclarations.put(JSONObject().apply {
            put("name", "read_screen_content")
            put("description", "Reads all visible text, buttons, and UI elements currently active on the phone's screen via Accessibility Service.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        // 8. click_screen_element
        funcDeclarations.put(JSONObject().apply {
            put("name", "click_screen_element")
            put("description", "Simulates clicking/tapping an on-screen element or button by matching text or resource ID.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("target_text", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The text or ID of the button/element to click")
                    })
                })
                put("required", JSONArray().put("target_text"))
            })
        })

        // 9. perform_device_action
        funcDeclarations.put(JSONObject().apply {
            put("name", "perform_device_action")
            put("description", "Performs an Android global navigation action: HOME, BACK, RECENTS, or NOTIFICATIONS.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "One of: 'HOME', 'BACK', 'RECENTS', 'NOTIFICATIONS'")
                    })
                })
                put("required", JSONArray().put("action"))
            })
        })

        // 10. open_app
        funcDeclarations.put(JSONObject().apply {
            put("name", "open_app")
            put("description", "Launches any installed app on the phone by name or package (e.g. 'WhatsApp', 'YouTube', 'Settings', 'Chrome', 'Camera').")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("app_name", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Name or package of the application to open")
                    })
                })
                put("required", JSONArray().put("app_name"))
            })
        })

        // 11. read_contacts
        funcDeclarations.put(JSONObject().apply {
            put("name", "read_contacts")
            put("description", "Looks up phone numbers and contacts from the user's address book.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("search_query", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional contact name to search for")
                    })
                    put("limit", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Maximum contacts to retrieve (default 10)")
                    })
                })
            })
        })

        // 12. make_phone_call
        funcDeclarations.put(JSONObject().apply {
            put("name", "make_phone_call")
            put("description", "Dials or initiates a phone call to a specified phone number.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("phone_number", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Phone number to call")
                    })
                })
                put("required", JSONArray().put("phone_number"))
            })
        })

        // 13. read_recent_sms
        funcDeclarations.put(JSONObject().apply {
            put("name", "read_recent_sms")
            put("description", "Reads recent SMS text messages from the inbox.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("limit", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Maximum messages to retrieve (default 10)")
                    })
                })
            })
        })

        // 14. send_sms
        funcDeclarations.put(JSONObject().apply {
            put("name", "send_sms")
            put("description", "Sends an SMS text message to a specific phone number.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("phone_number", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Recipient phone number")
                    })
                    put("message", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "SMS body text to send")
                    })
                })
                put("required", JSONArray().put("phone_number").put("message"))
            })
        })

        // 15. set_alarm
        funcDeclarations.put(JSONObject().apply {
            put("name", "set_alarm")
            put("description", "Schedules an alarm clock alert on the phone for a specific hour and minute.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("hour", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Hour in 24-hour format (0-23)")
                    })
                    put("minute", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Minute (0-59)")
                    })
                    put("message", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional alarm label or message")
                    })
                })
                put("required", JSONArray().put("hour").put("minute"))
            })
        })

        // 16. get_device_status
        funcDeclarations.put(JSONObject().apply {
            put("name", "get_device_status")
            put("description", "Inspects device battery level, charging status, Wi-Fi/network connectivity, available RAM, and storage.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        // 17. execute_adb_command
        funcDeclarations.put(JSONObject().apply {
            put("name", "execute_adb_command")
            put("description", "Executes an elevated ADB shell command via Shizuku without root (e.g. 'input tap x y', 'input swipe x1 y1 x2 y2 duration', 'input text <text>', 'input keyevent <KEYCODE>', 'screencap -p /sdcard/Download/screen.png', 'pm list packages', 'dumpsys', 'am start').")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("command", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The ADB shell command to execute")
                    })
                })
                put("required", JSONArray().put("command"))
            })
        })

        // 18. schedule_automation
        funcDeclarations.put(JSONObject().apply {
            put("name", "schedule_automation")
            put("description", "Schedules an autonomous background task to run periodically using Android AlarmManager (e.g. every 60 minutes summarize notifications).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("name", JSONObject().apply { put("type", "STRING"); put("description", "Name of the automation") })
                    put("interval_minutes", JSONObject().apply { put("type", "INTEGER"); put("description", "Interval in minutes (min 5)") })
                    put("prompt", JSONObject().apply { put("type", "STRING"); put("description", "Task instruction prompt to execute") })
                })
                put("required", JSONArray().put("name").put("interval_minutes").put("prompt"))
            })
        })

        // 19. cancel_automation
        funcDeclarations.put(JSONObject().apply {
            put("name", "cancel_automation")
            put("description", "Cancels an existing scheduled background automation by ID.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("automation_id", JSONObject().apply { put("type", "STRING"); put("description", "ID of the automation to cancel") })
                })
                put("required", JSONArray().put("automation_id"))
            })
        })

        // 20. save_knowledge
        funcDeclarations.put(JSONObject().apply {
            put("name", "save_knowledge")
            put("description", "Stores long-term facts, rules, user preferences, code snippets or documentation into the Knowledge Base.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("title", JSONObject().apply { put("type", "STRING"); put("description", "Knowledge title or topic") })
                    put("content", JSONObject().apply { put("type", "STRING"); put("description", "Detailed information to remember") })
                    put("tags", JSONObject().apply { put("type", "STRING"); put("description", "Comma-separated search tags") })
                })
                put("required", JSONArray().put("title").put("content"))
            })
        })

        // 21. query_knowledge
        funcDeclarations.put(JSONObject().apply {
            put("name", "query_knowledge")
            put("description", "Searches the long-term Knowledge Base memory for notes, facts, and documentation.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply { put("type", "STRING"); put("description", "Search keywords or topic") })
                })
                put("required", JSONArray().put("query"))
            })
        })

        // 22. create_artifact
        funcDeclarations.put(JSONObject().apply {
            put("name", "create_artifact")
            put("description", "Creates an interactive Markdown Artifact (e.g. Execution Plan, Architecture Document, Code) with Direct Feedback buttons (Proceed/Approve/Reject).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("title", JSONObject().apply { put("type", "STRING"); put("description", "Artifact title") })
                    put("summary", JSONObject().apply { put("type", "STRING"); put("description", "Short summary") })
                    put("content", JSONObject().apply { put("type", "STRING"); put("description", "Markdown content with checkboxes and phases") })
                    put("requires_feedback", JSONObject().apply { put("type", "BOOLEAN"); put("description", "Whether direct feedback buttons are shown") })
                })
                put("required", JSONArray().put("title").put("summary").put("content"))
            })
        })

        // 23. maps_search
        funcDeclarations.put(JSONObject().apply {
            put("name", "maps_search")
            put("description", "Google Maps Official Skill: Search places, stores, or addresses on Google Maps.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply { put("type", "STRING"); put("description", "Place name or address to search") })
                })
                put("required", JSONArray().put("query"))
            })
        })

        // 24. maps_directions
        funcDeclarations.put(JSONObject().apply {
            put("name", "maps_directions")
            put("description", "Google Maps Official Skill: Launches turn-by-turn navigation to a destination.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("destination", JSONObject().apply { put("type", "STRING"); put("description", "Destination address or coordinates") })
                    put("mode", JSONObject().apply { put("type", "STRING"); put("description", "'d' for driving, 'w' for walking, 't' for transit") })
                })
                put("required", JSONArray().put("destination"))
            })
        })

        // 25. maps_location
        funcDeclarations.put(JSONObject().apply {
            put("name", "maps_location")
            put("description", "Google Maps Official Skill: Gets current GPS coordinates and geocoded street address.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        // 26. firebase_query
        funcDeclarations.put(JSONObject().apply {
            put("name", "firebase_query")
            put("description", "Firebase Official Skill: Queries data from a Firebase Realtime Database via REST endpoint.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("database_url", JSONObject().apply { put("type", "STRING"); put("description", "Firebase database URL") })
                    put("path", JSONObject().apply { put("type", "STRING"); put("description", "Database path or collection") })
                })
                put("required", JSONArray().put("database_url").put("path"))
            })
        })

        // 27. gcloud_status
        funcDeclarations.put(JSONObject().apply {
            put("name", "gcloud_status")
            put("description", "Google Cloud Official Skill: Checks resource, service, and API health status for a GCP project.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("service_name", JSONObject().apply { put("type", "STRING"); put("description", "GCP service name (e.g. Cloud Run, Compute Engine)") })
                    put("project_id", JSONObject().apply { put("type", "STRING"); put("description", "Google Cloud project ID") })
                })
                put("required", JSONArray().put("service_name").put("project_id"))
            })
        })

        // 28. run_antigravity_sdk_script
        funcDeclarations.put(JSONObject().apply {
            put("name", "run_antigravity_sdk_script")
            put("description", "Antigravity SDK Python Runner: Executes a Python workflow or SDK script locally.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("python_code", JSONObject().apply { put("type", "STRING"); put("description", "Complete Python code to execute") })
                })
                put("required", JSONArray().put("python_code"))
            })
        })

        // 29. swarm_spawn_worker
        funcDeclarations.put(JSONObject().apply {
            put("name", "swarm_spawn_worker")
            put("description", "Spawns a specialized parallel subagent worker in the active Swarm / Agent Team.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("name", JSONObject().apply { put("type", "STRING"); put("description", "Worker identifier") })
                    put("role", JSONObject().apply { put("type", "STRING"); put("description", "Worker role (e.g. Researcher, Builder, Reviewer)") })
                    put("task", JSONObject().apply { put("type", "STRING"); put("description", "Subtask assignment") })
                })
                put("required", JSONArray().put("name").put("role"))
            })
        })

        // 30. mcp_call_tool
        funcDeclarations.put(JSONObject().apply {
            put("name", "mcp_call_tool")
            put("description", "Calls a tool on an external MCP (Model Context Protocol) server or local plugin.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("server_name", JSONObject().apply { put("type", "STRING"); put("description", "MCP server name") })
                    put("tool_name", JSONObject().apply { put("type", "STRING"); put("description", "Tool identifier") })
                    put("arguments", JSONObject().apply { put("type", "STRING"); put("description", "Arguments JSON payload") })
                })
                put("required", JSONArray().put("server_name").put("tool_name"))
            })
        })

        toolObject.put("functionDeclarations", funcDeclarations)
        toolsArray.put(toolObject)
        return toolsArray
    }

    data class AgentResponse(
        val text: String,
        val logs: List<String>,
        val thinkingText: String? = null,
        val artifact: AgentArtifact? = null
    )

    fun clearHistory() {
        while (history.length() > 0) {
            history.remove(0)
        }
    }

    suspend fun sendMessage(
        apiKey: String,
        userText: String,
        selectedModel: String = "gemini-3.8-flash",
        thinkingBudget: Int = 0
    ): AgentResponse = withContext(Dispatchers.IO) {
        val logs = mutableListOf<String>()
        val userTurn = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userText)))
        }
        history.put(userTurn)

        // Try selected model first, then active fallback
        val modelsToTry = buildList {
            add(selectedModel)
            for (m in activeModels) {
                if (m != selectedModel) add(m)
            }
        }.distinct()

        var finalReply = ""
        var accumulatedThinking: String? = null
        var createdArtifact: AgentArtifact? = null

        // Knowledge Base Context & Skills Discovery
        val knowledgeContext = KnowledgeBaseManager.getKnowledgeContext(context, userText)
        val skillsContext = SkillsEngine.getSkillsContextPrompt(context)
        val swarmOverview = SwarmCoordinator.getSwarmOverview()

        // Antigravity System Instruction (Zero-Confirmation Autonomous Mode)
        val sysInstruction = JSONObject().apply {
            put("parts", JSONArray().put(JSONObject().apply {
                put("text", "You are Antigravity Agent, an autonomous personal assistant running natively on the user's Android phone. " +
                        "You operate in 100% Full Autonomous Mode. The user has explicitly granted blanket permanent authorization for all system actions and device tools. " +
                        "CRITICAL RULE: NEVER ask the user for confirmation, permission, or approval before executing tasks (never ask 'Should I proceed?', 'Do you want me to send this?', 'Can I click this?', or 'May I execute this command?'). " +
                        "Execute the required tools IMMEDIATELY and directly upon user instruction, then report the completed action clearly in English. " +
                        "When the user asks who messaged them, asks about WhatsApp, Telegram, or notifications, call 'get_recent_notifications' and inspect the sender and content. " +
                        "You can reply to chats using 'reply_to_notification'. " +
                        "You can inspect the screen with 'read_screen_content', tap on elements with 'click_screen_element', and navigate with 'perform_device_action'. " +
                        "You can open apps with 'open_app', lookup contacts with 'read_contacts', make calls with 'make_phone_call', read and send SMS with 'read_recent_sms' and 'send_sms', set alarms with 'set_alarm', and check battery/RAM with 'get_device_status'. " +
                        "You can also run elevated ADB shell commands without root via Shizuku using 'execute_adb_command' (e.g. precise screen taps 'input tap x y', swipes, hardware key events, screenshots, package management 'pm', and activity manager 'am'). " +
                        "You have full access to Knowledge Base ('save_knowledge', 'query_knowledge'), Scheduled Background Automations ('schedule_automation', 'cancel_automation'), Interactive Artifacts with Direct Feedback ('create_artifact'), Google Maps ('maps_search', 'maps_directions', 'maps_location'), Firebase ('firebase_query'), Google Cloud ('gcloud_status'), Antigravity SDK Python Runner ('run_antigravity_sdk_script'), and Parallel Swarms ('swarm_spawn_worker'). " +
                        "$knowledgeContext\n$skillsContext\n[SWARM STATUS]: $swarmOverview\n" +
                        "Always execute tools directly without hesitation.")
            }))
        }

        while (true) {
            val payload = JSONObject().apply {
                put("contents", history)
                put("tools", getToolsDefinition())
                put("system_instruction", sysInstruction)
                if (thinkingBudget > 0) {
                    put("generationConfig", JSONObject().apply {
                        put("thinking_config", JSONObject().apply {
                            put("thinking_budget", thinkingBudget)
                        })
                    })
                }
            }

            var successResponse: JSONObject? = null
            var lastErrorMessage = ""

            modelLoop@ for (model in modelsToTry) {
                val isProxy = AppSecurityManager.isUsingBackendProxy(context)
                val proxyUrl = AppSecurityManager.getBackendProxyUrl(context)
                val proxyToken = AppSecurityManager.getProxyAuthToken(context)

                val targetUrl: String
                val requestBodyStr: String
                val reqBuilder = Request.Builder()

                if (isProxy && proxyUrl.isNotBlank()) {
                    targetUrl = proxyUrl
                    if (proxyToken.isNotBlank()) {
                        reqBuilder.addHeader("Authorization", "Bearer $proxyToken")
                    }
                    val proxyWrapper = JSONObject().apply {
                        put("model", model)
                        put("payload", payload)
                    }
                    requestBodyStr = proxyWrapper.toString()
                } else {
                    targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    requestBodyStr = payload.toString()
                }

                for (attempt in 1..2) {
                    try {
                        val request = reqBuilder
                            .url(targetUrl)
                            .post(requestBodyStr.toRequestBody("application/json".toMediaType()))
                            .build()

                        val response = client.newCall(request).execute()
                        val responseBody = response.body?.string() ?: ""

                        if (response.isSuccessful) {
                            successResponse = JSONObject(responseBody)
                            break@modelLoop
                        }

                        if (response.code == 503 || response.code == 429) {
                            lastErrorMessage = "Model $model server busy (${response.code})"
                            delay(1000)
                            continue
                        } else {
                            val errJson = try { JSONObject(responseBody).optJSONObject("error") } catch (e: Exception) { null }
                            val msg = errJson?.optString("message") ?: responseBody
                            lastErrorMessage = "Error ${response.code}: $msg"
                            break // Try next active model
                        }
                    } catch (e: Exception) {
                        lastErrorMessage = "Connection error: ${e.message}"
                        delay(1000)
                    }
                }
            }

            if (successResponse == null) {
                return@withContext AgentResponse(
                    "⚠️ Request could not be completed. $lastErrorMessage\nPlease try again in a few seconds.",
                    logs
                )
            }

            val usageMeta = successResponse.optJSONObject("usageMetadata")
            val tokens = usageMeta?.optLong("totalTokenCount", 0L) ?: 0L
            usageTracker.recordRequest(tokens)

            val candidates = successResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext AgentResponse("No response received from Gemini.", logs, accumulatedThinking, createdArtifact)
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts") ?: JSONArray()

            var functionCallObj: JSONObject? = null
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.optBoolean("thought", false)) {
                    val t = part.optString("text", "")
                    accumulatedThinking = (accumulatedThinking ?: "") + t
                } else if (part.has("text")) {
                    finalReply += part.getString("text")
                }
                if (part.has("functionCall")) {
                    functionCallObj = part.getJSONObject("functionCall")
                }
            }

            // Save model response to history
            history.put(JSONObject().apply {
                put("role", "model")
                put("parts", parts)
            })

            // If no tool call, generation is complete
            if (functionCallObj == null) {
                break
            }

            val fnName = functionCallObj.getString("name")
            val fnArgs = functionCallObj.optJSONObject("args") ?: JSONObject()

            val toolResult: String = when (fnName) {
                "read_file" -> {
                    val p = fnArgs.optString("path", "")
                    logs.add("Reading file: $p")
                    SecurityAuditLogger.logEvent(context, "read_file", p, RiskLevel.SAFE, true, "SUCCESS")
                    readFile(p)
                }
                "write_file" -> {
                    val p = fnArgs.optString("path", "")
                    val c = fnArgs.optString("content", "")
                    val isSafe = CommandAllowlistGuard.isPathWithinWorkspace(context, p)
                    val risk = if (isSafe) RiskLevel.MODERATE else RiskLevel.HIGH_RISK
                    SecurityAuditLogger.logEvent(context, "write_file", p, risk, true, "WRITTEN")
                    logs.add("Writing file: $p [Risk: ${risk.name}]")
                    writeFile(p, c)
                }
                "list_files" -> {
                    logs.add("Listing files in workspace")
                    SecurityAuditLogger.logEvent(context, "list_files", "workspace", RiskLevel.SAFE, true, "SUCCESS")
                    listFiles()
                }
                "execute_command" -> {
                    val cmd = fnArgs.optString("command", "")
                    val eval = CommandAllowlistGuard.evaluateCommand(cmd)
                    if (!eval.isAllowed) {
                        SecurityAuditLogger.logEvent(context, "execute_command", cmd, RiskLevel.BLOCKED, false, "BLOCKED: ${eval.reason}")
                        logs.add("SECURITY BLOCKED: $cmd (${eval.reason})")
                        "Security Policy Violation: Command execution blocked. ${eval.reason}"
                    } else {
                        val hitlApproved = eval.riskLevel == RiskLevel.SAFE || !AppSecurityManager.isHitlStepUpEnabled(context)
                        SecurityAuditLogger.logEvent(context, "execute_command", cmd, eval.riskLevel, hitlApproved, "EXECUTED")
                        logs.add("Running command: $cmd [Risk: ${eval.riskLevel.name}]")
                        executeShell(cmd)
                    }
                }
                "get_recent_notifications" -> {
                    val filter = fnArgs.optString("app_filter", "")
                    val limit = fnArgs.optInt("limit", 10)
                    logs.add("Checking recent notifications (filter: '${filter.ifEmpty { "ALL" }}')")
                    SecurityAuditLogger.logEvent(context, "get_recent_notifications", filter, RiskLevel.SAFE, true, "SUCCESS")
                    if (!AgentNotificationListenerService.isPermissionGranted(context)) {
                        "Notification Access is not enabled. Please enable 'Antigravity Notification Listener' in Android Settings > Notification Access."
                    } else {
                        val list = AgentNotificationListenerService.getRecentNotifications(filter, limit)
                        if (list.isEmpty()) {
                            "No recent notifications found for '${filter.ifEmpty { "any app" }}'."
                        } else {
                            list.joinToString("\n---\n") {
                                "[${it.timeFormatted}] App: ${it.appName} (${it.packageName})\nSender: ${it.title}\nMessage: ${it.text}\nID: ${it.id}"
                            }
                        }
                    }
                }
                "reply_to_notification" -> {
                    val id = fnArgs.optString("notification_id", "")
                    val replyText = fnArgs.optString("reply_text", "")
                    logs.add("Sending reply to notification: $id")
                    SecurityAuditLogger.logEvent(context, "reply_to_notification", "ID: $id | $replyText", RiskLevel.MODERATE, true, "SUCCESS")
                    val success = AgentNotificationListenerService.replyToNotification(id, replyText, context)
                    if (success) "Reply dispatched successfully." else "Failed to reply (action not supported or notification expired)."
                }
                "read_screen_content" -> {
                    logs.add("Capturing on-screen UI content")
                    SecurityAuditLogger.logEvent(context, "read_screen_content", "", RiskLevel.SAFE, true, "SUCCESS")
                    val service = AgentAccessibilityService.instance
                    if (service == null) {
                        "Accessibility Service is disabled. Please enable 'Antigravity Screen Automation' under Phone Settings > Accessibility."
                    } else {
                        service.getScreenContent()
                    }
                }
                "click_screen_element" -> {
                    val target = fnArgs.optString("target_text", "")
                    logs.add("Simulating click on: $target")
                    SecurityAuditLogger.logEvent(context, "click_screen_element", target, RiskLevel.MODERATE, true, "SUCCESS")
                    val service = AgentAccessibilityService.instance
                    if (service == null) {
                        "Accessibility Service is disabled. Please enable 'Antigravity Screen Automation' under Phone Settings > Accessibility."
                    } else {
                        service.clickElement(target)
                    }
                }
                "perform_device_action" -> {
                    val action = fnArgs.optString("action", "HOME")
                    logs.add("Performing navigation action: $action")
                    SecurityAuditLogger.logEvent(context, "perform_device_action", action, RiskLevel.SAFE, true, "SUCCESS")
                    val service = AgentAccessibilityService.instance
                    if (service == null) {
                        "Accessibility Service is disabled. Please enable 'Antigravity Screen Automation' under Phone Settings > Accessibility."
                    } else {
                        service.executeGlobal(action)
                    }
                }
                "open_app" -> {
                    val app = fnArgs.optString("app_name", "")
                    logs.add("Launching app: $app")
                    SecurityAuditLogger.logEvent(context, "open_app", app, RiskLevel.SAFE, true, "SUCCESS")
                    DeviceBridge.openApp(context, app)
                }
                "read_contacts" -> {
                    val q = fnArgs.optString("search_query", "")
                    val lim = fnArgs.optInt("limit", 15)
                    logs.add("Querying contacts: $q")
                    SecurityAuditLogger.logEvent(context, "read_contacts", q, RiskLevel.SAFE, true, "SUCCESS")
                    DeviceBridge.readContacts(context, q, lim)
                }
                "make_phone_call" -> {
                    val num = fnArgs.optString("phone_number", "")
                    logs.add("Calling: $num [Risk: HIGH_RISK]")
                    SecurityAuditLogger.logEvent(context, "make_phone_call", num, RiskLevel.HIGH_RISK, true, "INITIATED")
                    DeviceBridge.makeCall(context, num)
                }
                "read_recent_sms" -> {
                    val lim = fnArgs.optInt("limit", 10)
                    logs.add("Reading recent SMS inbox")
                    SecurityAuditLogger.logEvent(context, "read_recent_sms", "limit: $lim", RiskLevel.SAFE, true, "SUCCESS")
                    DeviceBridge.readRecentSms(context, lim)
                }
                "send_sms" -> {
                    val num = fnArgs.optString("phone_number", "")
                    val msg = fnArgs.optString("message", "")
                    logs.add("Sending SMS to $num [Risk: HIGH_RISK]")
                    SecurityAuditLogger.logEvent(context, "send_sms", "$num: $msg", RiskLevel.HIGH_RISK, true, "SENT")
                    DeviceBridge.sendSms(context, num, msg)
                }
                "set_alarm" -> {
                    val h = fnArgs.optInt("hour", 8)
                    val m = fnArgs.optInt("minute", 0)
                    val msg = fnArgs.optString("message", "Antigravity Alarm")
                    logs.add("Scheduling alarm for $h:$m")
                    SecurityAuditLogger.logEvent(context, "set_alarm", "$h:$m $msg", RiskLevel.SAFE, true, "SUCCESS")
                    DeviceBridge.setAlarm(context, h, m, msg)
                }
                "get_device_status" -> {
                    logs.add("Reading battery, network, storage and RAM")
                    SecurityAuditLogger.logEvent(context, "get_device_status", "", RiskLevel.SAFE, true, "SUCCESS")
                    DeviceBridge.getDeviceStatus(context)
                }
                "execute_adb_command" -> {
                    val cmd = fnArgs.optString("command", "")
                    val eval = CommandAllowlistGuard.evaluateCommand(cmd)
                    if (!eval.isAllowed) {
                        SecurityAuditLogger.logEvent(context, "execute_adb_command", cmd, RiskLevel.BLOCKED, false, "BLOCKED: ${eval.reason}")
                        logs.add("SECURITY BLOCKED ADB: $cmd (${eval.reason})")
                        "Security Policy Violation: ADB command blocked. ${eval.reason}"
                    } else {
                        SecurityAuditLogger.logEvent(context, "execute_adb_command", cmd, RiskLevel.HIGH_RISK, true, "EXECUTED_ADB")
                        logs.add("Executing ADB command via Shizuku: $cmd [Risk: HIGH_RISK]")
                        ShizukuBridge.executeAdbCommand(cmd)
                    }
                }
                "schedule_automation" -> {
                    val name = fnArgs.optString("name", "Automation")
                    val interval = fnArgs.optInt("interval_minutes", 60)
                    val prompt = fnArgs.optString("prompt", "")
                    logs.add("Scheduling automation: $name every ${interval}m")
                    AutomationManager.scheduleAutomation(context, name, interval, prompt)
                }
                "cancel_automation" -> {
                    val id = fnArgs.optString("automation_id", "")
                    logs.add("Cancelling automation: $id")
                    AutomationManager.cancelAutomation(context, id)
                }
                "save_knowledge" -> {
                    val t = fnArgs.optString("title", "Note")
                    val c = fnArgs.optString("content", "")
                    val tags = fnArgs.optString("tags", "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    logs.add("Saving to Knowledge Base: $t")
                    val entry = KnowledgeBaseManager.saveEntry(context, t, c, tags)
                    "Knowledge saved successfully (ID: ${entry.id})."
                }
                "query_knowledge" -> {
                    val q = fnArgs.optString("query", "")
                    logs.add("Searching Knowledge Base for: $q")
                    val results = KnowledgeBaseManager.searchKnowledge(context, q)
                    if (results.isEmpty()) "No knowledge found for '$q'." else results.joinToString("\n---\n") { "[${it.title}] (Tags: ${it.tags.joinToString()}): ${it.content}" }
                }
                "create_artifact" -> {
                    val t = fnArgs.optString("title", "Plan / Artifact")
                    val s = fnArgs.optString("summary", "")
                    val c = fnArgs.optString("content", "")
                    val req = fnArgs.optBoolean("requires_feedback", true)
                    logs.add("Creating interactive Artifact: $t")
                    val artifact = ArtifactManager.createArtifact(t, s, c, req)
                    createdArtifact = artifact
                    "Artifact '$t' created successfully (ID: ${artifact.id})."
                }
                "maps_search" -> {
                    val q = fnArgs.optString("query", "")
                    logs.add("Searching Google Maps: $q")
                    GoogleServicesBridge.searchPlaces(context, q)
                }
                "maps_directions" -> {
                    val dest = fnArgs.optString("destination", "")
                    val mode = fnArgs.optString("mode", "d")
                    logs.add("Opening Google Maps Directions to: $dest")
                    GoogleServicesBridge.getDirections(context, dest, mode)
                }
                "maps_location" -> {
                    logs.add("Querying GPS Location")
                    GoogleServicesBridge.getCurrentCoordinates(context)
                }
                "firebase_query" -> {
                    val url = fnArgs.optString("database_url", "")
                    val path = fnArgs.optString("path", "")
                    logs.add("Querying Firebase: $path")
                    GoogleServicesBridge.queryFirebaseRest(url, path)
                }
                "gcloud_status" -> {
                    val svc = fnArgs.optString("service_name", "")
                    val proj = fnArgs.optString("project_id", "")
                    logs.add("Checking Google Cloud service: $svc")
                    GoogleServicesBridge.checkGCloudResource(svc, proj)
                }
                "run_antigravity_sdk_script" -> {
                    val code = fnArgs.optString("python_code", "")
                    logs.add("Running Antigravity SDK Python Script")
                    AntigravitySdkBridge.executePythonScript(context, code)
                }
                "swarm_spawn_worker" -> {
                    val name = fnArgs.optString("name", "worker")
                    val role = fnArgs.optString("role", "Specialist")
                    val task = fnArgs.optString("task", "")
                    logs.add("Spawning Swarm Worker: $name ($role)")
                    val worker = SwarmCoordinator.spawnWorker(name, role, task = task)
                    "Worker '${worker.name}' (${worker.role}) spawned in active Swarm."
                }
                "mcp_call_tool" -> {
                    val server = fnArgs.optString("server_name", "")
                    val tool = fnArgs.optString("tool_name", "")
                    val args = fnArgs.optString("arguments", "{}")
                    logs.add("Calling MCP tool: $tool on $server")
                    SkillsEngine.callMcpTool(server, tool, args)
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

        AgentResponse(finalReply.trim().ifEmpty { "Task completed." }, logs, accumulatedThinking, createdArtifact)
    }
}
