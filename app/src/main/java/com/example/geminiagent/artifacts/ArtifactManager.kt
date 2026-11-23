package com.example.geminiagent.artifacts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ArtifactStatus {
    PENDING_REVIEW,
    APPROVED,
    PROCEED,
    REQUEST_CHANGES,
    REJECTED
}

typealias ArtifactFeedbackStatus = ArtifactStatus

data class AgentArtifact(
    val id: String = UUID.randomUUID().toString().take(8),
    val title: String,
    val summary: String,
    val content: String,
    val requiresFeedback: Boolean = true,
    var status: ArtifactStatus = ArtifactStatus.PENDING_REVIEW,
    val timestamp: Long = System.currentTimeMillis()
)

object ArtifactManager {

    private val _artifacts = MutableStateFlow<List<AgentArtifact>>(emptyList())
    val artifacts: StateFlow<List<AgentArtifact>> = _artifacts.asStateFlow()

    fun createArtifact(title: String, summary: String, content: String, requiresFeedback: Boolean = true): AgentArtifact {
        val artifact = AgentArtifact(
            title = title,
            summary = summary,
            content = content,
            requiresFeedback = requiresFeedback
        )
        _artifacts.value = _artifacts.value + artifact
        return artifact
    }

    fun updateStatus(id: String, newStatus: ArtifactStatus) {
        _artifacts.value = _artifacts.value.map {
            if (it.id == id) it.copy(status = newStatus) else it
        }
    }

    fun updateFeedbackStatus(id: String, newStatus: ArtifactStatus) {
        updateStatus(id, newStatus)
    }

    fun getArtifact(id: String): AgentArtifact? {
        return _artifacts.value.find { it.id == id }
    }
}
