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
import com.example.geminiagent.os.OSPermissionsManager
import com.example.geminiagent.service.AgentForegroundService
import com.example.geminiagent.ui.AgentChatScreen
import com.example.geminiagent.ui.theme.GeminiAgentTheme

class MainActivity : ComponentActivity() {

    private lateinit var apiClient: GeminiApiClient

    private val requestAllPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            AgentForegroundService.startService(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = GeminiApiClient(applicationContext)

        checkAndRequestPermissions()

        setContent {
            GeminiAgentTheme {
                AgentChatScreen(apiClient = apiClient)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        AgentForegroundService.startService(this)
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.addAll(OSPermissionsManager.REQUIRED_RUNTIME_PERMISSIONS)

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestAllPermissionsLauncher.launch(missing.toTypedArray())
        }
    }
}
