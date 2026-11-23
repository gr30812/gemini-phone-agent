package com.example.geminiagent.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.geminiagent.api.GeminiApiClient
import com.example.geminiagent.model.ChatMessage
import com.example.geminiagent.model.MessageSender
import com.example.geminiagent.os.OSPermissionsManager
import com.example.geminiagent.updater.AppUpdater
import com.example.geminiagent.updater.UpdateInfo
import com.example.geminiagent.usage.UsageStats
import com.example.geminiagent.voice.VoiceCommander
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ModelOption(
    val id: String,
    val displayName: String,
    val badge: String,
    val tag: String,
    val description: String,
    val isAvailable: Boolean = true
)

val ANTIGRAVITY_MODELS = listOf(
    ModelOption("gemini-3.8-flash", "Gemini 3.8 Flash", "High", "Recommended", "Google's flagship latest high-speed agent model"),
    ModelOption("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Ultra Stable", "Fast", "Ultra-stable lightweight model with zero downtime"),
    ModelOption("gemini-3.1-pro-preview", "Gemini 3.1 Pro (Thinking)", "Deep Thinking", "Preview", "Complex coding, multi-file refactoring and deep reasoning"),
    ModelOption("claude-sonnet-4-6", "Claude Sonnet 4.6 (Thinking)", "Notice", "Alternative", "Claude Sonnet coding engine", isAvailable = false),
    ModelOption("gpt-oss-120b", "GPT-OSS 120B (Medium)", "Notice", "Alternative", "Open-source hybrid reasoning model", isAvailable = false),
    ModelOption("gemini-4-carbon", "Gemini 4 Carbon", "Unavailable", "Next-Gen", "Upcoming next-gen multimodal model", isAvailable = false)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun AgentChatScreen(apiClient: GeminiApiClient) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var githubRepo by remember { mutableStateOf(prefs.getString("github_repo", "gr30812/gemini-phone-agent") ?: "gr30812/gemini-phone-agent") }
    var selectedModel by remember {
        mutableStateOf(prefs.getString("selected_model", "gemini-3.8-flash") ?: "gemini-3.8-flash")
    }

    // Voice Commander Integration
    val voiceCommander = remember { VoiceCommander(context) }
    val isListening by voiceCommander.isListening.collectAsState()
    val partialSpeech by voiceCommander.partialText.collectAsState()
    val soundLevel by voiceCommander.soundLevel.collectAsState()
    val voiceError by voiceCommander.errorMessage.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(apiKey.isEmpty()) }
    var showModelBottomSheet by remember { mutableStateOf(false) }
    var showUsageDialog by remember { mutableStateOf(false) }
    var showQuickActionsSheet by remember { mutableStateOf(false) }
    var showPermissionsSheet by remember { mutableStateOf(false) }

    val requestRuntimePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        Toast.makeText(context, "Permissions updated", Toast.LENGTH_SHORT).show()
    }

    val updater = remember { AppUpdater(context) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateCheckResultText by remember { mutableStateOf<String?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    val initialMessage = ChatMessage(
        sender = MessageSender.AGENT,
        text = "Welcome! I am your personal mobile Antigravity AI Agent running persistently in the background. Ask me to write code, manage files, run shell commands, or give voice instructions."
    )
    var messages by remember { mutableStateOf(listOf(initialMessage)) }

    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Send Message Handler
    fun doSendMessage(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isEmpty() || isLoading) return
        if (apiKey.isEmpty()) {
            showSettingsDialog = true
            return
        }

        inputText = ""
        val userMsg = ChatMessage(sender = MessageSender.USER, text = trimmed)
        messages = messages + userMsg
        isLoading = true

        scope.launch {
            listState.animateScrollToItem(messages.size - 1)
            val response = apiClient.sendMessage(apiKey, trimmed, selectedModel)
            val toolLogs = if (response.logs.isNotEmpty()) response.logs.joinToString("\n") else null
            val agentMsg = ChatMessage(
                sender = MessageSender.AGENT,
                text = response.text,
                toolLog = toolLogs
            )
            messages = messages + agentMsg
            isLoading = false
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Audio Permission Launcher
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceCommander.startListening { voiceResult ->
                if (voiceResult.isNotBlank()) {
                    doSendMessage(voiceResult)
                }
            }
        } else {
            Toast.makeText(context, "Microphone permission is required for voice commands", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleVoiceListening() {
        if (isListening) {
            voiceCommander.stopListening()
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                voiceCommander.startListening { voiceResult ->
                    if (voiceResult.isNotBlank()) {
                        doSendMessage(voiceResult)
                    }
                }
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Pull to Refresh Handler
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                delay(400)
                val repoName = githubRepo.trim().ifBlank { "gr30812/gemini-phone-agent" }
                val rawUrl = "https://raw.githubusercontent.com/$repoName/main/version.json"
                availableUpdate = updater.checkUpdate(rawUrl)
                delay(300)
                isRefreshing = false
            }
        }
    )

    val currentModelOption = ANTIGRAVITY_MODELS.find { it.id == selectedModel } ?: ANTIGRAVITY_MODELS[0]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2563EB).copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Antigravity",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Antigravity Agent", fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF1E293B)
                                ) {
                                    Text("v${updater.currentVersionName}", fontSize = 9.5.sp, color = Color(0xFF7DD3FC), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF4ADE80), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Background Active", fontSize = 10.5.sp, color = Color(0xFF4ADE80))
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        apiClient.clearHistory()
                        messages = listOf(initialMessage)
                        Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", tint = Color.LightGray)
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.LightGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF131314),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F0F10)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main Chat Stream with Pull-To-Refresh
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pullRefresh(pullRefreshState)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        AntigravityChatBubble(msg)
                    }
                    if (isLoading) {
                        item {
                            AntigravityThinkingIndicator(modelName = currentModelOption.displayName)
                        }
                    }
                }

                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF38BDF8)
                )
            }

            // Live Voice Recording Banner (if active)
            AnimatedVisibility(visible = isListening) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pulsing Voice Wave Dot
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.8f,
                            targetValue = 1.4f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseScale"
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(pulseScale + soundLevel)
                                .background(Color(0xFFEF4444), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Listening... (Speak your command)", color = Color(0xFFFCA5A5), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (partialSpeech.isNotEmpty()) {
                                Text(partialSpeech, color = Color.White, fontSize = 13.5.sp)
                            }
                        }
                        IconButton(onClick = { voiceCommander.stopListening() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Voice", tint = Color.LightGray)
                        }
                    }
                }
            }

            // ANTIGRAVITY FLOATING PROMPT BAR
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF1E1F22),
                border = BorderStroke(1.dp, Color(0xFF2E3035)),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Top Input Text Area
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp, max = 120.dp),
                        placeholder = {
                            Text(
                                "Ask anything, @ to mention, / for actions",
                                color = Color(0xFF71717A),
                                fontSize = 13.5.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF38BDF8),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bottom Control Row: + Button, Model Pill, Mic, Send/Stop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Group: + Button & Model Pill
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // + Action Button
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF282A2D),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { showQuickActionsSheet = true }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Actions",
                                        tint = Color(0xFFE4E4E7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Model Pill Selector (Antigravity Style)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF282A2D),
                                border = BorderStroke(1.dp, Color(0xFF3B3E45)),
                                modifier = Modifier.clickable { showModelBottomSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${currentModelOption.displayName} High",
                                        fontSize = 11.sp,
                                        color = Color(0xFFE4E4E7),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color(0xFF9CA3AF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Right Group: Mic (Voice Command) & Send/Stop Button
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Voice Microphone Button
                            Surface(
                                shape = CircleShape,
                                color = if (isListening) Color(0xFFEF4444).copy(alpha = 0.25f) else Color(0xFF282A2D),
                                border = if (isListening) BorderStroke(1.5.dp, Color(0xFFEF4444)) else null,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { toggleVoiceListening() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "Voice Command",
                                        tint = if (isListening) Color(0xFFEF4444) else Color(0xFFE4E4E7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Send or Stop Button
                            if (isLoading) {
                                // Antigravity Stop Button
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFDC2626),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clickable {
                                            isLoading = false
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Stop,
                                            contentDescription = "Stop Generation",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                // Antigravity Send Button
                                Surface(
                                    shape = CircleShape,
                                    color = if (inputText.isNotBlank()) Color(0xFF2563EB) else Color(0xFF333539),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clickable(enabled = inputText.isNotBlank()) {
                                            doSendMessage(inputText)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send Prompt",
                                            tint = if (inputText.isNotBlank()) Color.White else Color(0xFF71717A),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick Actions Sheet (+ Button)
    if (showQuickActionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickActionsSheet = false },
            containerColor = Color(0xFF1A1A1E),
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("Quick Actions & Commands", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(14.dp))

                QuickActionItem(icon = Icons.Default.Mic, title = "Voice Command", subtitle = "Speak voice instructions to generate code or tasks") {
                    showQuickActionsSheet = false
                    toggleVoiceListening()
                }
                QuickActionItem(icon = Icons.Default.Folder, title = "List Workspace Files", subtitle = "View all files in agent workspace (/list_files)") {
                    showQuickActionsSheet = false
                    doSendMessage("List all files currently in the workspace")
                }
                QuickActionItem(icon = Icons.Default.Terminal, title = "Execute Shell Command", subtitle = "Run a terminal shell command") {
                    showQuickActionsSheet = false
                    inputText = "execute_command: ls -la"
                }
                QuickActionItem(icon = Icons.Default.QueryStats, title = "View Usage & Rate Limits", subtitle = "Check 5h rolling & daily API quotas") {
                    showQuickActionsSheet = false
                    showUsageDialog = true
                }
                QuickActionItem(icon = Icons.Default.Security, title = "OS Permissions & Services", subtitle = "Enable WhatsApp, Screen Control & Phone permissions") {
                    showQuickActionsSheet = false
                    showPermissionsSheet = true
                }
                QuickActionItem(icon = Icons.Default.Settings, title = "Agent Settings", subtitle = "Configure API Key and OTA updates") {
                    showQuickActionsSheet = false
                    showSettingsDialog = true
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Antigravity Model Selector BottomSheet
    if (showModelBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelBottomSheet = false },
            containerColor = Color(0xFF18181B),
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Model",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))

                ANTIGRAVITY_MODELS.forEach { model ->
                    val isSelected = (model.id == selectedModel)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = model.isAvailable) {
                                selectedModel = model.id
                                prefs.edit().putString("selected_model", model.id).apply()
                                showModelBottomSheet = false
                            },
                        color = if (isSelected) Color(0xFF2563EB).copy(alpha = 0.15f) else Color(0xFF222226),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF2563EB)) else BorderStroke(1.dp, Color(0xFF2A2A2E))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = model.displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = if (model.isAvailable) Color.White else Color.Gray
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = if (model.badge == "High" || model.badge == "Ultra Stable") Color(0xFF1E3A8A) else Color(0xFF2D3036),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = model.badge,
                                            fontSize = 9.5.sp,
                                            color = if (model.isAvailable) Color(0xFF93C5FD) else Color.LightGray,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                    if (model.tag.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = model.tag,
                                            fontSize = 9.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = model.description,
                                    fontSize = 11.sp,
                                    color = if (model.isAvailable) Color(0xFFA1A1AA) else Color(0xFF52525B)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF2E3035), modifier = Modifier.padding(vertical = 12.dp))

                // View Usage Link
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showModelBottomSheet = false
                            showUsageDialog = true
                        },
                    color = Color(0xFF222226),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF2E3035))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QueryStats, contentDescription = "Usage", tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("View Usage & Rate Limits", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    // Antigravity Rate Limits Dialog
    if (showUsageDialog) {
        val stats: UsageStats = remember { apiClient.usageTracker.getUsageStats() }
        AlertDialog(
            onDismissRequest = { showUsageDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QueryStats, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Usage & Rate Limits", fontSize = 16.5.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1F22), shape = RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text("Gemini Models", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    AntigravityUsageRow(
                        title = "Weekly limit",
                        resetsIn = "6d 23h",
                        percentLeft = 99
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    AntigravityUsageRow(
                        title = "5h limit",
                        resetsIn = stats.fiveHourResetsIn,
                        percentLeft = stats.fiveHourPercentLeft
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF2E3035))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Claude and GPT models", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    AntigravityUsageRow(
                        title = "Weekly limit",
                        resetsIn = "1d 19h",
                        percentLeft = 0
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    AntigravityUsageRow(
                        title = "5h limit",
                        resetsIn = "4h 59m",
                        percentLeft = 100
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showUsageDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Close")
                }
            },
            containerColor = Color(0xFF18181B)
        )
    }

    // In-Settings Dialog
    if (showSettingsDialog) {
        var tempKey by remember { mutableStateOf(apiKey) }
        var tempRepo by remember { mutableStateOf(githubRepo) }

        AlertDialog(
            onDismissRequest = { if (apiKey.isNotEmpty()) showSettingsDialog = false },
            title = { Text("Agent Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("1. Gemini API Key:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("2. GitHub Repo (for OTA Updates):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = tempRepo,
                        onValueChange = { tempRepo = it },
                        placeholder = { Text("gr30812/gemini-phone-agent") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(modifier = Modifier.height(10.dp))

                    // In-Settings Update Check
                    Text("3. App Version & Updates:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current: v${updater.currentVersionName}", fontSize = 12.sp, color = Color.LightGray)

                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF38BDF8))
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isCheckingUpdate = true
                                    updateCheckResultText = null
                                    scope.launch {
                                        val repoName = tempRepo.trim().ifBlank { "gr30812/gemini-phone-agent" }
                                        val rawUrl = "https://raw.githubusercontent.com/$repoName/main/version.json"
                                        val update = updater.checkUpdate(rawUrl)
                                        isCheckingUpdate = false
                                        if (update != null) {
                                            availableUpdate = update
                                            updateCheckResultText = "🚀 New update available (${update.versionName})!"
                                        } else {
                                            updateCheckResultText = "✅ Your app is up to date!"
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Check", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check Update", fontSize = 11.sp)
                            }
                        }
                    }

                    updateCheckResultText?.let { msg ->
                        Text(
                            text = msg,
                            color = if (availableUpdate != null) Color(0xFF38BDF8) else Color(0xFF4ADE80),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    availableUpdate?.let { update ->
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isDownloadingUpdate) {
                            Column {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF2563EB)
                                )
                                Text("Downloading: ${(downloadProgress * 100).toInt()}%", fontSize = 11.sp, color = Color.Gray)
                            }
                        } else {
                            Button(
                                onClick = {
                                    isDownloadingUpdate = true
                                    scope.launch {
                                        updater.downloadAndInstall(
                                            update.downloadUrl,
                                            onProgress = { downloadProgress = it }
                                        )
                                        isDownloadingUpdate = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Update Now (${update.versionName})")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Android OS Services & Permissions
                    Text("4. OS Services & Automation:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WhatsApp, Screen & Calls", fontSize = 12.sp, color = Color.LightGray)
                        OutlinedButton(
                            onClick = {
                                showSettingsDialog = false
                                showPermissionsSheet = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manage", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        apiKey = tempKey.trim()
                        githubRepo = tempRepo.trim()
                        prefs.edit()
                            .putString("api_key", apiKey)
                            .putString("github_repo", githubRepo)
                            .apply()
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Save Settings")
                }
            },
            dismissButton = {
                if (apiKey.isNotEmpty()) {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            containerColor = Color(0xFF1E1E22)
        )
    }

    // Android OS Permissions & Services BottomSheet
    if (showPermissionsSheet) {
        val permStatus = remember { OSPermissionsManager.getStatus(context) }
        ModalBottomSheet(
            onDismissRequest = { showPermissionsSheet = false },
            containerColor = Color(0xFF18181B),
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OS Permissions & Automation", fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Enable OS permissions below so Antigravity Agent can read incoming WhatsApp messages, inspect the phone screen, simulate taps, and make calls.",
                    fontSize = 11.5.sp,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(14.dp))

                // 1. Notification Listener Access (WhatsApp & Chat alerts)
                OSPermissionRow(
                    title = "Notification Listener Access",
                    description = "Reads incoming WhatsApp, Telegram & SMS messages",
                    isGranted = permStatus.notificationAccess,
                    buttonText = "Open Settings",
                    onAction = { OSPermissionsManager.openNotificationSettings(context) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Accessibility Automation Service (Screen & Touch Control)
                OSPermissionRow(
                    title = "Accessibility Automation Service",
                    description = "Inspects active screen content and clicks elements",
                    isGranted = permStatus.accessibilityService,
                    buttonText = "Enable Service",
                    onAction = { OSPermissionsManager.openAccessibilitySettings(context) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Runtime Permissions (Phone, SMS, Contacts)
                val runtimeGranted = permStatus.contactsPermission && permStatus.phoneCallPermission && permStatus.smsPermission
                OSPermissionRow(
                    title = "Phone Calls, SMS & Contacts",
                    description = "Dials numbers, reads contacts & sends SMS messages",
                    isGranted = runtimeGranted,
                    buttonText = "Grant Permissions",
                    onAction = {
                        requestRuntimePermissionsLauncher.launch(OSPermissionsManager.REQUIRED_RUNTIME_PERMISSIONS)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun QuickActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        color = Color(0xFF242429),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF2563EB).copy(alpha = 0.2f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                Text(subtitle, color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun AntigravityThinkingIndicator(modelName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = Color(0xFF38BDF8)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$modelName is thinking and executing...",
            fontSize = 12.5.sp,
            color = Color(0xFF94A3B8)
        )
    }
}

@Composable
fun AntigravityUsageRow(
    title: String,
    resetsIn: String,
    percentLeft: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text("Resets in $resetsIn", color = Color(0xFF9CA3AF), fontSize = 11.5.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$percentLeft% left",
                color = if (percentLeft > 20) Color(0xFF4ADE80) else Color(0xFF6B7280),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { percentLeft / 100f },
                    modifier = Modifier.size(20.dp),
                    color = if (percentLeft > 20) Color(0xFF4ADE80) else Color(0xFF4B5563),
                    trackColor = Color(0xFF262626),
                    strokeWidth = 2.5.dp
                )
            }
        }
    }
}

@Composable
fun AntigravityChatBubble(msg: ChatMessage) {
    val isUser = msg.sender == MessageSender.USER
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isToolsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Agent Avatar / Sparkle Header
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    "Antigravity Agent",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Expandable Antigravity Tool Logs / Action Status Pill
        if (!isUser && msg.toolLog != null) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(bottom = 6.dp)
                    .clickable { isToolsExpanded = !isToolsExpanded }
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tool Executions (${msg.toolLog.lines().size} actions)",
                                color = Color(0xFF7DD3FC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            if (isToolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (isToolsExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = Color(0xFF334155))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = msg.toolLog,
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Message Content Bubble
        if (isUser) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Text(
                    text = msg.text,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        } else {
            // Render Agent Response with Code Block Highlighting & Copy Buttons
            val parsedBlocks = remember(msg.text) { parseMarkdownBlocks(msg.text) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
            ) {
                parsedBlocks.forEach { block ->
                    when (block) {
                        is ContentBlock.TextBlock -> {
                            Text(
                                text = block.content,
                                color = Color(0xFFE4E4E7),
                                fontSize = 14.5.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        is ContentBlock.CodeBlock -> {
                            Surface(
                                color = Color(0xFF121316),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF27272A)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column {
                                    // Code Card Header with Language & Copy Button
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1C1D21))
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = block.language.ifEmpty { "code" },
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(
                                            modifier = Modifier
                                                .clickable {
                                                    clipboardManager.setText(AnnotatedString(block.code))
                                                    Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy Code",
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy", color = Color(0xFF38BDF8), fontSize = 11.sp)
                                        }
                                    }

                                    // Code text
                                    Text(
                                        text = block.code,
                                        color = Color(0xFFF1F5F9),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed class ContentBlock {
    data class TextBlock(val content: String) : ContentBlock()
    data class CodeBlock(val language: String, val code: String) : ContentBlock()
}

fun parseMarkdownBlocks(rawText: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val codeFenceRegex = Regex("```([a-zA-Z0-9_+-]*)\\n([\\s\\S]*?)```")
    var lastIndex = 0

    codeFenceRegex.findAll(rawText).forEach { match ->
        if (match.range.first > lastIndex) {
            val textBefore = rawText.substring(lastIndex, match.range.first).trim()
            if (textBefore.isNotEmpty()) {
                blocks.add(ContentBlock.TextBlock(textBefore))
            }
        }
        val lang = match.groupValues[1].trim()
        val code = match.groupValues[2].trimEnd()
        blocks.add(ContentBlock.CodeBlock(lang, code))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < rawText.length) {
        val remaining = rawText.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            blocks.add(ContentBlock.TextBlock(remaining))
        }
    }

    if (blocks.isEmpty() && rawText.isNotEmpty()) {
        blocks.add(ContentBlock.TextBlock(rawText))
    }

    return blocks
}

@Composable
fun OSPermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF222226),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isGranted) Color(0xFF1E3A8A) else Color(0xFF333338))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isGranted) Color(0xFF14532D) else Color(0xFF450A0A)
                    ) {
                        Text(
                            text = if (isGranted) "Enabled" else "Disabled",
                            color = if (isGranted) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, color = Color.Gray, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (!isGranted) {
                Button(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text(buttonText, fontSize = 10.5.sp)
                }
            } else {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(20.dp))
            }
        }
    }
}
