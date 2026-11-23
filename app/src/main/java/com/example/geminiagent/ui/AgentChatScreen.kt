package com.example.geminiagent.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(apiClient: GeminiApiClient) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var githubRepo by remember {
        mutableStateOf(prefs.getString("github_repo", "") ?: "")
    }
    var showSettingsDialog by remember { mutableStateOf(apiKey.isEmpty()) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gemini Phone Agent", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Background Active • gemini-3.8-flash", fontSize = 11.sp, color = Color(0xFF4ADE80))
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
                            Text("Agent কাজ করছে...", fontSize = 13.sp, color = Color.Gray)
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
                                    val response = apiClient.sendMessage(apiKey, prompt)
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
                        placeholder = { Text("username/gemini-phone-agent") },
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
