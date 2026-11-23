package com.example.geminiagent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.geminiagent.api.GeminiApiClient
import com.example.geminiagent.service.AgentForegroundService
import com.example.geminiagent.ui.AgentChatScreen
import com.example.geminiagent.ui.theme.GeminiAgentTheme

class MainActivity : ComponentActivity() {

    private lateinit var apiClient: GeminiApiClient

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                AgentForegroundService.startService(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = GeminiApiClient(applicationContext)

        checkAndStartForegroundService()

        setContent {
            GeminiAgentTheme {
                AgentChatScreen(apiClient = apiClient)
            }
        }
    }

    private fun checkAndStartForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                AgentForegroundService.startService(this)
            } else {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            AgentForegroundService.startService(this)
        }
    }
}
