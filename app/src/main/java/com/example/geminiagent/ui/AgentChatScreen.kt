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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.geminiagent.api.GeminiApiClient
import com.example.geminiagent.model.ChatMessage
import com.example.geminiagent.model.MessageSender
import com.example.geminiagent.os.OSPermissionsManager
import com.example.geminiagent.os.ShizukuBridge
import com.example.geminiagent.updater.AppUpdater
import com.example.geminiagent.updater.UpdateInfo
import com.example.geminiagent.usage.UsageStats
import com.example.geminiagent.voice.VoiceCommander
import com.example.geminiagent.artifacts.AgentArtifact
import com.example.geminiagent.artifacts.ArtifactStatus
import com.example.geminiagent.artifacts.ArtifactFeedbackStatus
import com.example.geminiagent.artifacts.ArtifactManager
import com.example.geminiagent.automation.AutomationManager
import com.example.geminiagent.knowledge.KnowledgeBaseManager
import com.example.geminiagent.swarm.SlashCommands
import com.example.geminiagent.swarm.SwarmCoordinator
import com.example.geminiagent.swarm.SwarmWorker
import com.example.geminiagent.swarm.WorkerStatus
import com.example.geminiagent.swarm.BoardMessage
import com.example.geminiagent.security.AppSecurityManager
import com.example.geminiagent.security.RiskLevel
import com.example.geminiagent.security.SecurityAuditLogger
import com.example.geminiagent.security.AuditLogEntry
import android.app.Activity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    var showAddContextSheet by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var showSideQuestionDialog by remember { mutableStateOf(false) }
    var sideQuestionText by remember { mutableStateOf("") }
    var sideQuestionAnswer by remember { mutableStateOf<String?>(null) }
    var isSideQuestionLoading by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var currentProject by remember { mutableStateOf("swarm/swarm_board") }
    var paletteSearchQuery by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showSwarmBoardDialog by remember { mutableStateOf(false) }
    var showKnowledgeBaseDialog by remember { mutableStateOf(false) }
    var showAutomationsDialog by remember { mutableStateOf(false) }
    var showSecurityAuditDialog by remember { mutableStateOf(false) }
    var isAppUnlocked by remember {
        mutableStateOf(!AppSecurityManager.isAppLockEnabled(context))
    }

    val biometricUnlockLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            isAppUnlocked = true
            Toast.makeText(context, "Authentication successful", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Authentication required to access agent", Toast.LENGTH_SHORT).show()
        }
    }

    var thinkingBudget by remember {
        mutableIntStateOf(prefs.getInt("thinking_budget", 2048))
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "attached_file"
            inputText = if (inputText.isBlank()) "@file: $fileName " else "$inputText @file: $fileName "
            Toast.makeText(context, "Attached $fileName to context", Toast.LENGTH_SHORT).show()
        }
    }

    // Send Message Handler with Slash Commands & Dynamic Thinking Control
    fun doSendMessage(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isEmpty() || isLoading) return
        if (apiKey.isEmpty()) {
            showSettingsDialog = true
            return
        }

        val slashResult = SlashCommands.parse(trimmed)
        val promptToSend = slashResult.transformedPrompt
        val effectiveThinkingBudget = if (slashResult.isBoostMode) 8192 else thinkingBudget

        inputText = ""
        val userMsg = ChatMessage(sender = MessageSender.USER, text = trimmed)
        messages = messages + userMsg
        isLoading = true

        scope.launch {
            listState.animateScrollToItem(messages.size - 1)
            val response = apiClient.sendMessage(apiKey, promptToSend, selectedModel, effectiveThinkingBudget)
            val toolLogs = if (response.logs.isNotEmpty()) response.logs.joinToString("\n") else null
            val agentMsg = ChatMessage(
                sender = MessageSender.AGENT,
                text = response.text,
                toolLog = toolLogs,
                thinkingText = response.thinkingText,
                artifact = response.artifact
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

    // App Lock (Biometric / PIN Verification Overlay)
    if (!isAppUnlocked) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F0F12)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF2563EB).copy(alpha = 0.2f),
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("Antigravity Agent Locked", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "App Lock 2FA is active. Verify your fingerprint, face, or device PIN to continue.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = {
                        val intent = AppSecurityManager.createDeviceCredentialIntent(context)
                        if (intent != null) {
                            biometricUnlockLauncher.launch(intent)
                        } else {
                            isAppUnlocked = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(0.75f).height(48.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock Agent", fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF131315),
                drawerContentColor = Color.White,
                modifier = Modifier.width(285.dp)
            ) {
                AntigravityDrawerContent(
                    currentProject = currentProject,
                    onSelectGeneral = {
                        scope.launch { drawerState.close() }
                        showSettingsDialog = true
                    },
                    onSelectApplication = {
                        scope.launch { drawerState.close() }
                        showSettingsDialog = true
                    },
                    onSelectAppearance = {
                        scope.launch { drawerState.close() }
                        Toast.makeText(context, "Appearance: JetBrains Dark (System)", Toast.LENGTH_SHORT).show()
                    },
                    onSelectShortcuts = {
                        scope.launch { drawerState.close() }
                        showAddContextSheet = true
                    },
                    onSelectNotifications = {
                        scope.launch { drawerState.close() }
                        OSPermissionsManager.openNotificationSettings(context)
                    },
                    onSelectModels = {
                        scope.launch { drawerState.close() }
                        showModelBottomSheet = true
                    },
                    onSelectBrowser = {
                        scope.launch { drawerState.close() }
                        showCommandPalette = true
                    },
                    onSelectSecurity = {
                        scope.launch { drawerState.close() }
                        showSecurityAuditDialog = true
                    },
                    onSelectProject = {
                        scope.launch { drawerState.close() }
                        showSwarmBoardDialog = true
                    },
                    onSelectKnowledge = {
                        scope.launch { drawerState.close() }
                        showKnowledgeBaseDialog = true
                    },
                    onSelectAutomations = {
                        scope.launch { drawerState.close() }
                        showAutomationsDialog = true
                    },
                    onSelectConversations = {
                        scope.launch { drawerState.close() }
                        apiClient.clearHistory()
                        messages = listOf(initialMessage)
                        Toast.makeText(context, "New conversation started", Toast.LENGTH_SHORT).show()
                    },
                    onGiveFeedback = {
                        scope.launch { drawerState.close() }
                        Toast.makeText(context, "Feedback: Antigravity Mobile IDE v${updater.currentVersionName}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                    },
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
                        IconButton(onClick = { showCommandPalette = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search & Context", tint = Color.LightGray)
                        }
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
                // Antigravity Projects Bar (Matching Image 4)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    color = Color(0xFF141417),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF232328))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showNewProjectDialog = true }
                        ) {
                            Text(
                                "Projects",
                                color = Color(0xFFE4E4E7),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF27272A)
                            ) {
                                Text(
                                    currentProject,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { Toast.makeText(context, "Active project: $currentProject", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = Color.LightGray, modifier = Modifier.size(17.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { showNewProjectDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Project", tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
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
                        AntigravityChatBubble(
                            msg = msg,
                            onArtifactProceed = { artifact ->
                                ArtifactManager.updateStatus(artifact.id, ArtifactStatus.PROCEED)
                                doSendMessage("Proceed with approved artifact: '${artifact.title}'. Begin phase 1 execution.")
                            },
                            onArtifactRequestChanges = { artifact ->
                                ArtifactManager.updateStatus(artifact.id, ArtifactStatus.REQUEST_CHANGES)
                                inputText = "Please adjust artifact '${artifact.title}': "
                            },
                            onArtifactReject = { artifact ->
                                ArtifactManager.updateStatus(artifact.id, ArtifactStatus.REJECTED)
                                Toast.makeText(context, "Artifact rejected: ${artifact.title}", Toast.LENGTH_SHORT).show()
                            }
                        )
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
                            // + Action Button (Add Context)
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF282A2D),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { showAddContextSheet = true }
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
    } // Closes ModalNavigationDrawer

    // Antigravity "Add Context" BottomSheet (Matching Image 1)
    if (showAddContextSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddContextSheet = false },
            containerColor = Color(0xFF18181B),
            scrimColor = Color.Black.copy(alpha = 0.65f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    "Add Context",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                AddContextItem(
                    icon = Icons.Default.AttachFile,
                    title = "Files",
                    onClick = {
                        showAddContextSheet = false
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }
                )

                AddContextItem(
                    icon = Icons.Default.Folder,
                    title = "Folder",
                    onClick = {
                        showAddContextSheet = false
                        inputText = if (inputText.isBlank()) "@folder: workspace/ " else "$inputText @folder: workspace/ "
                    }
                )

                AddContextItem(
                    icon = Icons.Default.AlternateEmail,
                    title = "Mentions",
                    onClick = {
                        showAddContextSheet = false
                        inputText = if (inputText.isBlank()) "@agent " else "$inputText @agent "
                    }
                )

                AddContextItem(
                    icon = Icons.Default.NorthEast,
                    title = "Actions",
                    onClick = {
                        showAddContextSheet = false
                        showQuickActionsSheet = true
                    }
                )

                AddContextItem(
                    icon = Icons.Default.Language,
                    title = "Browser",
                    onClick = {
                        showAddContextSheet = false
                        showCommandPalette = true
                    }
                )

                AddContextItem(
                    icon = Icons.Default.ChangeHistory,
                    title = "Drive",
                    trailingBadge = "Set up",
                    onClick = {
                        Toast.makeText(context, "Google Drive workspace integration setup", Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Antigravity Command Palette & Context Search BottomSheet (Matching Image 2)
    if (showCommandPalette) {
        ModalBottomSheet(
            onDismissRequest = { showCommandPalette = false },
            containerColor = Color(0xFF131316),
            scrimColor = Color.Black.copy(alpha = 0.65f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                // Search or enter a URL
                OutlinedTextField(
                    value = paletteSearchQuery,
                    onValueChange = { paletteSearchQuery = it },
                    placeholder = { Text("Search or enter a URL", color = Color(0xFF71717A), fontSize = 13.5.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1A1A1E),
                        unfocusedContainerColor = Color(0xFF1A1A1E),
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color(0xFF2D2D33),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (paletteSearchQuery.isNotBlank()) {
                            val q = paletteSearchQuery
                            paletteSearchQuery = ""
                            showCommandPalette = false
                            doSendMessage("Search or browse URL: $q")
                        }
                    })
                )

                Spacer(modifier = Modifier.height(14.dp))

                PaletteActionRow(icon = Icons.Default.Description, title = "Open File") {
                    showCommandPalette = false
                    filePickerLauncher.launch(arrayOf("*/*"))
                }
                PaletteActionRow(icon = Icons.Default.Language, title = "Browser") {
                    showCommandPalette = false
                    if (paletteSearchQuery.isNotBlank()) {
                        doSendMessage("Browse and summarize web content for: $paletteSearchQuery")
                        paletteSearchQuery = ""
                    } else {
                        inputText = "Browse: https://"
                    }
                }
                PaletteActionRow(icon = Icons.Default.ChatBubbleOutline, title = "Side Question") {
                    showCommandPalette = false
                    showSideQuestionDialog = true
                }
                PaletteActionRow(icon = Icons.Default.Extension, title = "Extensions", trailingIcon = Icons.Default.ChevronRight) {
                    showCommandPalette = false
                    showPermissionsSheet = true
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Background Tasks (Badge 51)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Background Tasks", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF27272A)
                    ) {
                        Text("51", fontSize = 10.sp, color = Color(0xFFE4E4E7), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            Toast.makeText(context, "Running task active in background", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(">_  ", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Run Android Studio installer silently", color = Color(0xFFE4E4E7), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                }
                Text(
                    "See all",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .clickable { Toast.makeText(context, "Viewing all background tasks", Toast.LENGTH_SHORT).show() }
                        .padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Uploads (Badge 24)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Uploads", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF27272A)
                    ) {
                        Text("24", fontSize = 10.sp, color = Color(0xFFE4E4E7), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val sampleUploads = listOf(
                    "Media 1795471288010 Ea02b769",
                    "Media 1795471000073 43e29f60",
                    "Media 1795470992174 Aed56450",
                    "Media 1795470984014 F701566c"
                )
                sampleUploads.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                inputText = if (inputText.isBlank()) "@upload: $item " else "$inputText @upload: $item "
                                showCommandPalette = false
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(item, color = Color.LightGray, fontSize = 12.sp)
                    }
                }
                Text(
                    "See all",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .clickable { Toast.makeText(context, "Viewing all uploads", Toast.LENGTH_SHORT).show() }
                        .padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Side Question Dialog
    if (showSideQuestionDialog) {
        AlertDialog(
            onDismissRequest = { showSideQuestionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Side Question", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column {
                    Text(
                        "Ask a quick question without affecting the main conversation thread.",
                        fontSize = 11.5.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sideQuestionText,
                        onValueChange = { sideQuestionText = it },
                        placeholder = { Text("Type your side question...", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    if (isSideQuestionLoading) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF38BDF8))
                    }
                    if (sideQuestionAnswer != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF27272A),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                sideQuestionAnswer ?: "",
                                color = Color(0xFFE4E4E7),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sideQuestionText.isNotBlank()) {
                            isSideQuestionLoading = true
                            sideQuestionAnswer = null
                            scope.launch {
                                val resp = apiClient.sendMessage(apiKey, "Side Question (be concise): $sideQuestionText", selectedModel)
                                sideQuestionAnswer = resp.text
                                isSideQuestionLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Ask", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSideQuestionDialog = false
                    sideQuestionText = ""
                    sideQuestionAnswer = null
                }) {
                    Text("Close", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E22)
        )
    }

    // New Project Dialog
    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch or Create Project", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column {
                    Text(
                        "Current active workspace project:",
                        fontSize = 11.5.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        currentProject,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        placeholder = { Text("Enter new project name...", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjectName.isNotBlank()) {
                            currentProject = newProjectName.trim()
                            newProjectName = ""
                            showNewProjectDialog = false
                            Toast.makeText(context, "Switched project to: $currentProject", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Select / Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E22)
        )
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
                QuickActionItem(icon = Icons.Default.Groups, title = "Swarm Message Board (/swarm)", subtitle = "Coordinate parallel multi-agent workers") {
                    showQuickActionsSheet = false
                    showSwarmBoardDialog = true
                }
                QuickActionItem(icon = Icons.AutoMirrored.Filled.MenuBook, title = "Knowledge Base", subtitle = "View and edit long-term agent memory and rules") {
                    showQuickActionsSheet = false
                    showKnowledgeBaseDialog = true
                }
                QuickActionItem(icon = Icons.Default.Schedule, title = "Scheduled Automations (/automation)", subtitle = "Manage periodic autonomous background tasks") {
                    showQuickActionsSheet = false
                    showAutomationsDialog = true
                }
                QuickActionItem(icon = Icons.Default.Security, title = "OS Permissions & Services", subtitle = "Enable WhatsApp, Screen Control & Phone permissions") {
                    showQuickActionsSheet = false
                    showPermissionsSheet = true
                }
                QuickActionItem(icon = Icons.Default.Lock, title = "Security & Audit Trail", subtitle = "View blocked commands, risk levels & audit logs") {
                    showQuickActionsSheet = false
                    showSecurityAuditDialog = true
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

                // Dynamic Thinking Budget Control
                Text(
                    text = "Dynamic Thinking Budget",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "Configures extended reasoning budget (tokens) for Gemini 3.8/3.1 and /boost mode.",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(8.dp))

                val thinkingOptions = listOf(
                    0 to "Off (0)",
                    1024 to "Low (1k)",
                    2048 to "Med (2k)",
                    4096 to "Deep (4k)",
                    8192 to "Boost (8k)"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    thinkingOptions.forEach { (budget, label) ->
                        val isSelected = thinkingBudget == budget
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    thinkingBudget = budget
                                    prefs.edit().putInt("thinking_budget", budget).apply()
                                },
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF222226),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF2E3035))
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1)
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

    // Swarm / Agent Teams Message Board Dialog
    if (showSwarmBoardDialog) {
        SwarmBoardDialog(
            onDismiss = { showSwarmBoardDialog = false },
            onSpawnWorker = { name, _, _ ->
                Toast.makeText(context, "Spawned worker: $name", Toast.LENGTH_SHORT).show()
            },
            onPostMessage = { _, _ ->
                Toast.makeText(context, "Posted to swarm board", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Knowledge Base Dialog
    if (showKnowledgeBaseDialog) {
        KnowledgeBaseDialog(
            context = context,
            onDismiss = { showKnowledgeBaseDialog = false }
        )
    }

    // Scheduled Automations Dialog
    if (showAutomationsDialog) {
        AutomationsDialog(
            context = context,
            onDismiss = { showAutomationsDialog = false },
            onRunTaskNow = { prompt ->
                doSendMessage(prompt)
            }
        )
    }

    // Security Audit Dialog
    if (showSecurityAuditDialog) {
        SecurityAuditDialog(
            context = context,
            onDismiss = { showSecurityAuditDialog = false }
        )
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
        var tempAppLock by remember { mutableStateOf(AppSecurityManager.isAppLockEnabled(context)) }
        var tempHitl by remember { mutableStateOf(AppSecurityManager.isHitlStepUpEnabled(context)) }
        var tempUseProxy by remember { mutableStateOf(AppSecurityManager.isUsingBackendProxy(context)) }
        var tempProxyUrl by remember { mutableStateOf(AppSecurityManager.getBackendProxyUrl(context)) }
        var tempProxyToken by remember { mutableStateOf(AppSecurityManager.getProxyAuthToken(context)) }

        AlertDialog(
            onDismissRequest = { if (apiKey.isNotEmpty() || tempUseProxy) showSettingsDialog = false },
            title = { Text("Agent Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
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
                        Text("Current: v", fontSize = 12.sp, color = Color.LightGray)

                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF38BDF8))
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isCheckingUpdate = true
                                    updateCheckResultText = null
                                    scope.launch {
                                        val repoName = tempRepo.trim().ifBlank { "gr30812/gemini-phone-agent" }
                                        val rawUrl = "https://raw.githubusercontent.com//main/version.json"
                                        val update = updater.checkUpdate(rawUrl)
                                        isCheckingUpdate = false
                                        if (update != null) {
                                            availableUpdate = update
                                            updateCheckResultText = "🚀 New update available ()!"
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
                                Text("Downloading: %", fontSize = 11.sp, color = Color.Gray)
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
                                Text("Update Now ()")
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

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(modifier = Modifier.height(10.dp))

                    // 5. Security, 2FA & Backend Proxy
                    Text("5. Security, 2FA & Backend Proxy:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // App Lock Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("App Lock (Biometric / PIN)", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                            Text("Layer 1 authentication on launch", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                        }
                        Switch(
                            checked = tempAppLock,
                            onCheckedChange = { tempAppLock = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF2563EB)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // HITL Guard Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("HITL Step-Up Guard", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                            Text("Require approval for dangerous shell/file tasks", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                        }
                        Switch(
                            checked = tempHitl,
                            onCheckedChange = { tempHitl = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF2563EB)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Backend Proxy Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Use Backend Proxy Server", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                            Text("Zero client secrets (no API key on phone)", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                        }
                        Switch(
                            checked = tempUseProxy,
                            onCheckedChange = { tempUseProxy = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF2563EB)
                            )
                        )
                    }

                    if (tempUseProxy) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Proxy Endpoint URL:", fontSize = 11.5.sp, color = Color(0xFF94A3B8))
                        OutlinedTextField(
                            value = tempProxyUrl,
                            onValueChange = { tempProxyUrl = it },
                            placeholder = { Text("https://my-proxy.workers.dev/api/v1/generate") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Proxy Bearer Token (optional):", fontSize = 11.5.sp, color = Color(0xFF94A3B8))
                        OutlinedTextField(
                            value = tempProxyToken,
                            onValueChange = { tempProxyToken = it },
                            placeholder = { Text("Bearer secret") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            showSettingsDialog = false
                            showSecurityAuditDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Security Audit Trail", fontSize = 12.sp)
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
                        AppSecurityManager.setAppLockEnabled(context, tempAppLock)
                        AppSecurityManager.setHitlStepUpEnabled(context, tempHitl)
                        AppSecurityManager.setUsingBackendProxy(context, tempUseProxy)
                        AppSecurityManager.setBackendProxyUrl(context, tempProxyUrl.trim())
                        AppSecurityManager.setProxyAuthToken(context, tempProxyToken.trim())
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Save Settings")
                }
            },
            dismissButton = {
                if (apiKey.isNotEmpty() || tempUseProxy) {
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
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OS Permissions & Voice Assistant", fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Enable OS permissions below so Antigravity Voice Agent can listen for 'Nord', answer 'Sir!', and control your phone.",
                    fontSize = 11.5.sp,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Default Digital Assistant
                var isDefaultAssist by remember { mutableStateOf(OSPermissionsManager.isDefaultAssistant(context)) }
                OSPermissionRow(
                    title = "Default Digital Assistant",
                    description = if (isDefaultAssist) "Nord Assistant is your default phone assistant! (Power hold / swipe)" else "Set as default assistant (replaces Google Assistant)",
                    isGranted = isDefaultAssist,
                    buttonText = if (isDefaultAssist) "Configured" else "Set as Default",
                    onAction = {
                        OSPermissionsManager.openDefaultAssistantSettings(context)
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Nord Wake Word Detection
                var isHotwordOn by remember { mutableStateOf(com.example.geminiagent.voice.NordHotwordService.isEnabled(context)) }
                OSPermissionRow(
                    title = "Nord Wake Word Detection",
                    description = if (isHotwordOn) "Active: Call 'Nord' -> Agent speaks 'Sir!' and listens" else "Disabled: Call 'Nord' to invoke assistant hands-free",
                    isGranted = isHotwordOn,
                    buttonText = if (isHotwordOn) "Stop" else "Enable",
                    onAction = {
                        if (isHotwordOn) {
                            com.example.geminiagent.voice.NordHotwordService.stop(context)
                            isHotwordOn = false
                        } else {
                            com.example.geminiagent.voice.NordHotwordService.start(context)
                            isHotwordOn = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

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

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Shizuku ADB Elevated Shell Bridge
                OSPermissionRow(
                    title = "Shizuku ADB Elevated Shell",
                    description = if (permStatus.shizukuRunning) {
                        if (permStatus.shizukuPermission) "Elevated ADB shell active (input tap, keyevent, pm, screencap)"
                        else "Shizuku is running. Tap to authorize elevated ADB bridge."
                    } else {
                        "Non-root ADB bridge. Tap to launch Shizuku and start via Wireless Debugging."
                    },
                    isGranted = permStatus.shizukuPermission,
                    buttonText = if (permStatus.shizukuRunning) {
                        if (permStatus.shizukuPermission) "Authorized" else "Authorize"
                    } else "Open Shizuku",
                    onAction = {
                        if (permStatus.shizukuRunning) {
                            ShizukuBridge.requestPermission()
                        } else {
                            ShizukuBridge.openShizukuApp(context)
                        }
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
fun AntigravityChatBubble(
    msg: ChatMessage,
    onArtifactProceed: ((AgentArtifact) -> Unit)? = null,
    onArtifactRequestChanges: ((AgentArtifact) -> Unit)? = null,
    onArtifactReject: ((AgentArtifact) -> Unit)? = null
) {
    val isUser = msg.sender == MessageSender.USER
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isToolsExpanded by remember { mutableStateOf(false) }
    var isThinkingExpanded by remember { mutableStateOf(false) }

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

        // Expandable Dynamic Thinking Process (Deep Reasoning & Boost)
        if (!isUser && !msg.thinkingText.isNullOrBlank()) {
            Surface(
                color = Color(0xFF141822),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF263045)),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(bottom = 6.dp)
                    .clickable { isThinkingExpanded = !isThinkingExpanded }
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Thinking Process (${msg.thinkingText.length} chars)",
                                color = Color(0xFFFCD34D),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            if (isThinkingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (isThinkingExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = Color(0xFF263045))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = msg.thinkingText,
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
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

        // Interactive Artifact Card with Direct Feedback
        if (!isUser && msg.artifact != null) {
            val artifact = msg.artifact
            var currentStatus by remember(artifact.status) { mutableStateOf(artifact.status) }

            Surface(
                color = Color(0xFF141519),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = artifact.title,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "ARTIFACT",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (artifact.summary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = artifact.summary,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF0C0D10),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (artifact.content.length > 350) artifact.content.take(350) + "\n... (truncated)" else artifact.content,
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    if (artifact.requiresFeedback && currentStatus == ArtifactStatus.PENDING_REVIEW) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    currentStatus = ArtifactStatus.PROCEED
                                    onArtifactProceed?.invoke(artifact)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Proceed", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    currentStatus = ArtifactStatus.REQUEST_CHANGES
                                    onArtifactRequestChanges?.invoke(artifact)
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF64748B)),
                                modifier = Modifier.weight(1.1f),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Changes", color = Color.LightGray, fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    currentStatus = ArtifactStatus.REJECTED
                                    onArtifactReject?.invoke(artifact)
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                modifier = Modifier.weight(0.9f),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reject", color = Color(0xFFEF4444), fontSize = 11.sp)
                            }
                        }
                    } else if (currentStatus != ArtifactStatus.PENDING_REVIEW) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Status: ${currentStatus.name}",
                            color = when (currentStatus) {
                                ArtifactStatus.PROCEED, ArtifactStatus.APPROVED -> Color(0xFF4ADE80)
                                ArtifactStatus.REQUEST_CHANGES -> Color(0xFFFBBF24)
                                ArtifactStatus.REJECTED -> Color(0xFFF87171)
                                else -> Color.Gray
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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

@Composable
fun AntigravityDrawerContent(
    currentProject: String,
    onSelectGeneral: () -> Unit,
    onSelectApplication: () -> Unit,
    onSelectAppearance: () -> Unit,
    onSelectShortcuts: () -> Unit,
    onSelectNotifications: () -> Unit,
    onSelectModels: () -> Unit,
    onSelectBrowser: () -> Unit,
    onSelectSecurity: () -> Unit,
    onSelectProject: () -> Unit,
    onSelectKnowledge: () -> Unit,
    onSelectAutomations: () -> Unit,
    onSelectConversations: () -> Unit,
    onGiveFeedback: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp, horizontal = 12.dp)
    ) {
        // Settings Section Header
        Text(
            "Settings",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF71717A),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )

        // Navigation Items (Matching Image 3)
        DrawerNavItem(title = "General", isSelected = true, onClick = onSelectGeneral)
        DrawerNavItem(title = "Application", isSelected = false, onClick = onSelectApplication)
        DrawerNavItem(title = "Appearance", isSelected = false, onClick = onSelectAppearance)
        DrawerNavItem(title = "Shortcuts", isSelected = false, onClick = onSelectShortcuts)
        DrawerNavItem(title = "Notifications", isSelected = false, onClick = onSelectNotifications)
        DrawerNavItem(title = "Models", isSelected = false, onClick = onSelectModels)
        DrawerNavItem(title = "Browser", isSelected = false, onClick = onSelectBrowser)
        DrawerNavItem(title = "Security & 2FA", isSelected = false, leadingIcon = Icons.Default.Security, onClick = onSelectSecurity)

        Spacer(modifier = Modifier.height(14.dp))

        // Projects Section
        Text(
            "Projects",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF71717A),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        DrawerNavItem(
            title = currentProject,
            isSelected = false,
            leadingIcon = Icons.Default.Folder,
            onClick = onSelectProject
        )
        DrawerNavItem(
            title = "Knowledge Base",
            isSelected = false,
            leadingIcon = Icons.AutoMirrored.Filled.MenuBook,
            onClick = onSelectKnowledge
        )
        DrawerNavItem(
            title = "Automations (/automation)",
            isSelected = false,
            leadingIcon = Icons.Default.Schedule,
            onClick = onSelectAutomations
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Not in Project Section
        Text(
            "Not in Project",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF71717A),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        DrawerNavItem(
            title = "Conversations",
            isSelected = false,
            leadingIcon = Icons.Default.ChatBubbleOutline,
            onClick = onSelectConversations
        )

        Spacer(modifier = Modifier.height(14.dp))

        DrawerNavItem(
            title = "Give Feedback",
            isSelected = false,
            onClick = onGiveFeedback
        )

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(color = Color(0xFF27272A), thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // Profile Tile (Matching Image 3: George Roy / gr30812@gmail.com)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF2563EB),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "GR",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "George Roy",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "gr30812@gmail.com",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun DrawerNavItem(
    title: String,
    isSelected: Boolean,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() },
        color = if (isSelected) Color(0xFF27272A) else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else Color(0xFF9CA3AF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                title,
                color = if (isSelected) Color.White else Color(0xFFD4D4D8),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun AddContextItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String = "",
    tint: Color = Color(0xFFCBD5E1),
    trailingBadge: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clickable { onClick() },
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(14.dp))
            if (subtitle.isBlank()) {
                Text(
                    title,
                    color = Color(0xFFE4E4E7),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    Text(subtitle, color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
            }
            if (trailingBadge != null) {
                Text(
                    trailingBadge,
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onClick() }
                )
            }
        }
    }
}

@Composable
fun PaletteActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onClick() },
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFE4E4E7), modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, color = Color(0xFFE4E4E7), fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun SwarmBoardDialog(
    onDismiss: () -> Unit,
    onSpawnWorker: (String, String, String) -> Unit,
    onPostMessage: (String, String) -> Unit
) {
    val workers by SwarmCoordinator.workers.collectAsState()
    val messages by SwarmCoordinator.boardMessages.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var newWorkerName by remember { mutableStateOf("") }
    var boardPostText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141417),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Swarm / Agent Teams (/swarm)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1E1E24),
                    contentColor = Color(0xFF38BDF8)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Workers (${workers.size})", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Message Board (${messages.size})", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(workers) { worker ->
                            Surface(
                                color = Color(0xFF1E2028),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF2C3240)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(worker.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${worker.role} • ${worker.model}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        if (worker.currentTask.isNotBlank()) {
                                            Text(worker.currentTask, color = Color(0xFF64748B), fontSize = 10.5.sp, maxLines = 1)
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = when (worker.status) {
                                            WorkerStatus.WORKING -> Color(0xFF22C55E).copy(alpha = 0.2f)
                                            WorkerStatus.COMPLETED -> Color(0xFF0284C7).copy(alpha = 0.2f)
                                            WorkerStatus.IDLE -> Color(0xFF64748B).copy(alpha = 0.2f)
                                            WorkerStatus.ERROR -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                        }
                                    ) {
                                        Text(
                                            text = worker.status.name,
                                            color = when (worker.status) {
                                                WorkerStatus.WORKING -> Color(0xFF4ADE80)
                                                WorkerStatus.COMPLETED -> Color(0xFF38BDF8)
                                                WorkerStatus.IDLE -> Color(0xFF94A3B8)
                                                WorkerStatus.ERROR -> Color(0xFFF87171)
                                            },
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newWorkerName,
                        onValueChange = { newWorkerName = it },
                        placeholder = { Text("Worker name (e.g. researcher)", fontSize = 11.sp, color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (newWorkerName.isNotBlank()) {
                                val w = SwarmCoordinator.spawnWorker(newWorkerName.trim(), "Worker", task = "Active worker")
                                onSpawnWorker(w.name, w.role, w.currentTask)
                                newWorkerName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text("Spawn Worker", fontSize = 11.5.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages) { msg ->
                            Surface(
                                color = Color(0xFF1E2028),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${msg.from} → @${msg.to}", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))
                                        Text(timeStr, color = Color.Gray, fontSize = 9.5.sp)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(msg.content, color = Color(0xFFE2E8F0), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = boardPostText,
                            onValueChange = { boardPostText = it },
                            placeholder = { Text("Post to swarm board...", fontSize = 11.sp, color = Color.Gray) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (boardPostText.isNotBlank()) {
                                    SwarmCoordinator.postMessage("coordinator", "all", boardPostText.trim())
                                    onPostMessage("coordinator", boardPostText.trim())
                                    boardPostText = ""
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color(0xFF38BDF8))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF38BDF8))
            }
        }
    )
}

@Composable
fun KnowledgeBaseDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    var items: List<com.example.geminiagent.knowledge.KnowledgeEntry> by remember { mutableStateOf(KnowledgeBaseManager.getAllKnowledge(context)) }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141417),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Knowledge Base", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Text(
                    "Long-term agent memory. Facts and rules stored here are injected into prompt context automatically.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items) { item ->
                        Surface(
                            color = Color(0xFF1E2028),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(item.content, color = Color(0xFFE2E8F0), fontSize = 11.sp)
                                }
                                IconButton(
                                    onClick = {
                                        KnowledgeBaseManager.deleteKnowledge(context, item.id)
                                        items = KnowledgeBaseManager.getAllKnowledge(context)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    placeholder = { Text("Topic / Key (e.g. user_preference)", fontSize = 11.sp, color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    placeholder = { Text("Fact / Rule content", fontSize = 11.sp, color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        if (newKey.isNotBlank() && newValue.isNotBlank()) {
                            KnowledgeBaseManager.saveKnowledge(context, newKey.trim(), newValue.trim())
                            items = KnowledgeBaseManager.getAllKnowledge(context)
                            newKey = ""
                            newValue = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("Add Knowledge", fontSize = 11.5.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF38BDF8))
            }
        }
    )
}

@Composable
fun AutomationsDialog(
    context: Context,
    onDismiss: () -> Unit,
    onRunTaskNow: (String) -> Unit
) {
    var automations: List<com.example.geminiagent.automation.ScheduledAutomation> by remember { mutableStateOf(AutomationManager.getAllAutomations(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141417),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scheduled Automations (/automation)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                Text(
                    "Autonomous background tasks executed by Android AlarmManager / WorkManager.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (automations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No active automations. Use /automation to schedule tasks.", color = Color.Gray, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(automations) { item ->
                            Surface(
                                color = Color(0xFF1E2028),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (item.isEnabled) Color(0xFF22C55E).copy(alpha = 0.2f) else Color(0xFF64748B).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = if (item.isEnabled) "ACTIVE" else "PAUSED",
                                                color = if (item.isEnabled) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Interval: ${item.intervalMinutes}m • Prompt: ${item.prompt}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                onRunTaskNow(item.prompt)
                                                onDismiss()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Run Now", fontSize = 10.5.sp, color = Color(0xFF38BDF8))
                                        }
                                        TextButton(
                                            onClick = {
                                                AutomationManager.cancelAutomation(context, item.id)
                                                automations = AutomationManager.getAllAutomations(context)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Cancel", fontSize = 10.5.sp, color = Color(0xFFEF4444))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF38BDF8))
            }
        }
    )
}



@Composable
fun SecurityAuditDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    var logs by remember { mutableStateOf(SecurityAuditLogger.getRecentLogs(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141417),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Security Audit Trail", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Events: ${logs.size} (Limit: 200)", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                    if (logs.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                SecurityAuditLogger.clearLogs(context)
                                logs = emptyList()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", color = Color(0xFFEF4444), fontSize = 11.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                        Text("No security events recorded yet.", color = Color.Gray, fontSize = 12.sp)
                    }
                } else {
                    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { entry ->
                            val (badgeBg, badgeFg) = when (entry.riskLevel) {
                                RiskLevel.SAFE -> Color(0xFF22C55E).copy(alpha = 0.15f) to Color(0xFF4ADE80)
                                RiskLevel.MODERATE -> Color(0xFF3B82F6).copy(alpha = 0.15f) to Color(0xFF60A5FA)
                                RiskLevel.HIGH_RISK -> Color(0xFFF97316).copy(alpha = 0.15f) to Color(0xFFFB923C)
                                RiskLevel.BLOCKED -> Color(0xFFEF4444).copy(alpha = 0.2f) to Color(0xFFF87171)
                            }

                            Surface(
                                color = Color(0xFF1E2028),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = if (entry.riskLevel == RiskLevel.BLOCKED) BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)) else null
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            entry.toolName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = badgeBg
                                        ) {
                                            Text(
                                                entry.riskLevel.name,
                                                color = badgeFg,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (entry.detail.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            entry.detail,
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 11.sp,
                                            maxLines = 3,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            entry.timeFormatted,
                                            color = Color(0xFF64748B),
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            entry.status,
                                            color = if (entry.riskLevel != RiskLevel.BLOCKED) Color(0xFF94A3B8) else Color(0xFFF87171),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF38BDF8))
            }
        }
    )
}