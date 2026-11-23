package com.example.geminiagent.model

import com.example.geminiagent.artifacts.AgentArtifact

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val toolLog: String? = null,
    val thinkingText: String? = null,
    val artifact: AgentArtifact? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageSender {
    USER,
    AGENT,
    SYSTEM
}
