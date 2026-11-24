package com.example.geminiagent.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geminiagent.MainActivity
import com.example.geminiagent.api.GeminiApiClient
import kotlinx.coroutines.launch

class VoiceAssistActivity : ComponentActivity() {

    private lateinit var voiceCommander: VoiceCommander
    private lateinit var apiClient: GeminiApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        VoiceTtsManager.init(this)
        voiceCommander = VoiceCommander(this)
        apiClient = GeminiApiClient(this)

        val autoListen = intent.getBooleanExtra("AUTO_LISTEN", false)

        setContent {
            VoiceAssistBottomSheet(
                voiceCommander = voiceCommander,
                apiClient = apiClient,
                startAutoListen = autoListen,
                onDismiss = { finish() },
                onOpenFullApp = {
                    val fullAppIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(fullAppIntent)
                    finish()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceCommander.stopListening()
        VoiceTtsManager.stop()
    }
}

@Composable
fun VoiceAssistBottomSheet(
    voiceCommander: VoiceCommander,
    apiClient: GeminiApiClient,
    startAutoListen: Boolean,
    onDismiss: () -> Unit,
    onOpenFullApp: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isListening by voiceCommander.isListening.collectAsState()
    val partialText by voiceCommander.partialText.collectAsState()
    val soundLevel by voiceCommander.soundLevel.collectAsState()
    val errorMessage by voiceCommander.errorMessage.collectAsState()

    var statusText by remember { mutableStateOf("Ready") }
    var userPrompt by remember { mutableStateOf("") }
    var agentResponse by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    fun startListening() {
        agentResponse = ""
        userPrompt = ""
        statusText = "Listening for your order..."
        voiceCommander.startListening { spokenText ->
            if (spokenText.isNotBlank()) {
                userPrompt = spokenText
                statusText = "Processing: \"$spokenText\""
                isProcessing = true
                coroutineScope.launch {
                    val prefs = context.getSharedPreferences("gemini_prefs", android.content.Context.MODE_PRIVATE)
                    val apiKey = prefs.getString("api_key", "") ?: ""
                    val response = apiClient.sendMessage(apiKey = apiKey, userText = spokenText)
                    isProcessing = false
                    agentResponse = response.text
                    statusText = "Done"
                    // Speak back agent's answer
                    VoiceTtsManager.speak(response.text)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (startAutoListen) {
            startListening()
        } else {
            // Speak greeting "Sir!" then start listening
            VoiceTtsManager.speakAck {
                startListening()
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Full screen overlay with semi-transparent background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF141418),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E36)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2563EB).copy(alpha = 0.2f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("N", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Nord Voice Assistant",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onOpenFullApp, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Full App", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Pulsing Mic Visualizer
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .scale(if (isListening) pulseScale else 1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isListening) Color(0xFF2563EB).copy(alpha = 0.25f + soundLevel * 0.4f) else Color(0xFF27272A),
                        modifier = Modifier.fillMaxSize()
                    ) {}
                    Surface(
                        shape = CircleShape,
                        color = if (isListening) Color(0xFF2563EB) else Color(0xFF3F3F46),
                        modifier = Modifier
                            .size(54.dp)
                            .clickable {
                                if (isListening) {
                                    voiceCommander.stopListening()
                                } else {
                                    startListening()
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Mic",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status & Live Transcription
                Text(
                    text = statusText,
                    fontSize = 12.5.sp,
                    color = if (isListening) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )

                if (isListening && partialText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "\"$partialText\"",
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = error, fontSize = 11.5.sp, color = Color(0xFFEF4444))
                }

                // Processing Indicator
                AnimatedVisibility(visible = isProcessing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(3.dp),
                            color = Color(0xFF38BDF8),
                            trackColor = Color(0xFF27272A)
                        )
                    }
                }

                // Response Card (if response available)
                if (agentResponse.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color(0xFF1E1E24),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = agentResponse,
                                color = Color(0xFFE4E4E7),
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = { startListening() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Ask Another", fontSize = 11.5.sp)
                        }
                        Button(
                            onClick = onOpenFullApp,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Open in App", fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }
    }
}
