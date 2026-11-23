package com.example.geminiagent.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geminiagent.api.GeminiApiClient
import com.example.geminiagent.model.ChatMessage
import com.example.geminiagent.model.MessageSender
import com.example.geminiagent.updater.AppUpdater
import com.example.geminiagent.updater.UpdateInfo
import com.example.geminiagent.usage.UsageStats
import kotlinx.coroutines.launch

data class ModelOption(
    val id: String,
    val displayName: String,
    val badge: String,
    val description: String
)

val AVAILABLE_MODELS = listOf(
    ModelOption("gemini-3.8-flash", "Gemini 3.8 Flash", "High Speed", "গুগলের সবচেয়ে দ্রুত ও লেটেস্ট মডেল"),
    ModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", "Balanced", "দ্রুত এবং নিখুঁত পারফরম্যান্স"),
    ModelOption("gemini-2.5-pro", "Gemini 2.5 Pro", "Deep Thinking", "জটিল কোডিং এবং গভীর যুক্তি বিশ্লেষণ"),
    ModelOption("gemini-1.5-pro", "Gemini 1.5 Pro", "Stable", "অত্যন্ত নির্ভরযোগ্য ও দীর্ঘ কনটেক্সট")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(apiClient: GeminiApiClient) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var githubRepo by remember { mutableStateOf(prefs.getString("github_repo", "") ?: "") }
    var selectedModel by remember {
        mutableStateOf(prefs.getString("selected_model", "gemini-3.8-flash") ?: "gemini-3.8-flash")
    }

    var showSettingsDialog by remember { mutableStateOf(apiKey.isEmpty()) }
    var showModelBottomSheet by remember { mutableStateOf(false) }
    var showUsageDialog by remember { mutableStateOf(false) }

    val updater = remember { AppUpdater(context) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    sender = MessageSender.AGENT,
                    text = "স্বাগতম! আমি আপনার নিজস্ব মোবাইল এআই এজেন্ট। আমি ব্যাকগ্রাউন্ডে সক্রিয় আছি। কোড এডিট বা কোনো কাজ করতে বলুন।"
                )
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Check updates automatically if GitHub repo is configured
    LaunchedEffect(githubRepo) {
        if (githubRepo.isNotEmpty()) {
            val rawVersionUrl = "https://raw.githubusercontent.com/$githubRepo/main/version.json"
            val update = updater.checkUpdate(rawVersionUrl)
            if (update != null) {
                availableUpdate = update
            }
        }
    }

    val currentModelOption = AVAILABLE_MODELS.find { it.id == selectedModel } ?: AVAILABLE_MODELS[0]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gemini Phone Agent", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        // Clickable Model Selector Chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showModelBottomSheet = true }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                "⚡ ${currentModelOption.displayName}",
                                fontSize = 11.5.sp,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Select Model",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "• Background Active",
                                fontSize = 10.5.sp,
                                color = Color(0xFF4ADE80)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.LightGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Update Banner if available
            availableUpdate?.let { update ->
                Surface(
                    color = Color(0xFF1E3A8A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🚀 নতুন আপডেট (${update.versionName}) প্রস্তুত!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                update.changelog,
                                color = Color(0xFF93C5FD),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                        if (isDownloadingUpdate) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("আপডেট করুন", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(msg)
                }
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("${currentModelOption.displayName} কাজ করছে...", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // Input Area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E1E1E),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("এজেন্টকে নির্দেশনা দিন...", color = Color.Gray) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF2A2A2A),
                            unfocusedContainerColor = Color(0xFF2A2A2A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF2563EB),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val prompt = inputText.trim()
                            if (prompt.isNotEmpty() && !isLoading) {
                                if (apiKey.isEmpty()) {
                                    showSettingsDialog = true
                                    return@IconButton
                                }
                                inputText = ""
                                val userMsg = ChatMessage(sender = MessageSender.USER, text = prompt)
                                messages = messages + userMsg
                                isLoading = true

                                scope.launch {
                                    listState.animateScrollToItem(messages.size - 1)
                                    val response = apiClient.sendMessage(apiKey, prompt, selectedModel)
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
                        },
                        enabled = inputText.isNotBlank() && !isLoading,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF333333),
                            disabledContentColor = Color.Gray
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }

    // Model Selector BottomSheet
    if (showModelBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelBottomSheet = false },
            containerColor = Color(0xFF1E1E1E),
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "মডেল নির্বাচন করুন (Model)",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "আপনার কাজের জন্য সেরা মডেলটি বেছে নিন:",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                AVAILABLE_MODELS.forEach { model ->
                    val isSelected = model.id == selectedModel
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                selectedModel = model.id
                                prefs.edit().putString("selected_model", model.id).apply()
                                showModelBottomSheet = false
                            },
                        color = if (isSelected) Color(0xFF2563EB).copy(alpha = 0.2f) else Color(0xFF262626),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2563EB)) else null
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
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = if (isSelected) Color(0xFF2563EB) else Color(0xFF374151),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = model.badge,
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = model.description,
                                    fontSize = 11.sp,
                                    color = Color.LightGray
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

                HorizontalDivider(
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // View Usage Link
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showModelBottomSheet = false
                            showUsageDialog = true
                        },
                    color = Color(0xFF2A2A2A),
                    shape = RoundedCornerShape(12.dp)
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
                            Column {
                                Text("View Usage & Rate Limits", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("কোটা ও রিমেইনিং লিমিট দেখুন", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // View Usage Dialog
    if (showUsageDialog) {
        val stats: UsageStats = remember { apiClient.usageTracker.getUsageStats() }
        AlertDialog(
            onDismissRequest = { showUsageDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QueryStats, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("API Usage & Rate Limits", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        "Gemini Models",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Daily Limit Row
                    UsageLimitRow(
                        title = "Daily limit",
                        resetsIn = stats.dailyResetsIn,
                        percentLeft = stats.dailyPercentLeft,
                        used = stats.dailyUsed,
                        total = stats.dailyLimit
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5h Limit Row
                    UsageLimitRow(
                        title = "5h rolling limit",
                        resetsIn = stats.fiveHourResetsIn,
                        percentLeft = stats.fiveHourPercentLeft,
                        used = stats.fiveHourUsed,
                        total = stats.fiveHourLimit
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total tokens used:", fontSize = 12.sp, color = Color.Gray)
                        Text("${stats.totalTokens} tokens", fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUsageDialog = false }) {
                    Text("ঠিক আছে")
                }
            },
            containerColor = Color(0xFF181818)
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        var tempKey by remember { mutableStateOf(apiKey) }
        var tempRepo by remember { mutableStateOf(githubRepo) }

        AlertDialog(
            onDismissRequest = { if (apiKey.isNotEmpty()) showSettingsDialog = false },
            title = { Text("এজেন্ট সেটিংস") },
            text = {
                Column {
                    Text("১. Gemini API Key:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("২. GitHub Repo (OTA আপডেটের জন্য):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Format: username/repo-name", fontSize = 11.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = tempRepo,
                        onValueChange = { tempRepo = it },
                        placeholder = { Text("gr30812/gemini-phone-agent") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    }
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                if (apiKey.isNotEmpty()) {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("বাতিল")
                    }
                }
            }
        )
    }
}

@Composable
fun UsageLimitRow(
    title: String,
    resetsIn: String,
    percentLeft: Int,
    used: Int,
    total: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text("Resets in $resetsIn", color = Color.Gray, fontSize = 11.5.sp)
            Text("$used / $total requests", color = Color(0xFF6B7280), fontSize = 10.5.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$percentLeft% left",
                color = if (percentLeft > 20) Color(0xFF4ADE80) else Color(0xFFF87171),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { percentLeft / 100f },
                    modifier = Modifier.size(24.dp),
                    color = if (percentLeft > 20) Color(0xFF4ADE80) else Color(0xFFF87171),
                    trackColor = Color(0xFF2A2A2A),
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.sender == MessageSender.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser && msg.toolLog != null) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 4.dp)
            ) {
                Text(
                    text = "⚙️ Actions:\n${msg.toolLog}",
                    color = Color(0xFF7DD3FC),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        Surface(
            color = if (isUser) Color(0xFF2563EB) else Color(0xFF242424),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = msg.text,
                color = Color.White,
                fontSize = 14.5.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
