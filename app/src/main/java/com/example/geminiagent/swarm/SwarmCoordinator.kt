package com.example.geminiagent.swarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class WorkerStatus {
    IDLE,
    WORKING,
    COMPLETED,
    ERROR
}

data class SwarmWorker(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val role: String,
    val model: String = "gemini-3.8-flash",
    var status: WorkerStatus = WorkerStatus.IDLE,
    var currentTask: String = "",
    var resultSummary: String? = null
)

data class BoardMessage(
    val id: String = UUID.randomUUID().toString().take(6),
    val from: String,
    val to: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

object SwarmCoordinator {

    private val _workers = MutableStateFlow<List<SwarmWorker>>(emptyList())
    val workers: StateFlow<List<SwarmWorker>> = _workers.asStateFlow()

    private val _boardMessages = MutableStateFlow<List<BoardMessage>>(emptyList())
    val boardMessages: StateFlow<List<BoardMessage>> = _boardMessages.asStateFlow()

    fun spawnWorker(name: String, role: String, model: String = "gemini-3.8-flash", task: String = ""): SwarmWorker {
        val worker = SwarmWorker(
            name = name,
            role = role,
            model = model,
            status = if (task.isNotBlank()) WorkerStatus.WORKING else WorkerStatus.IDLE,
            currentTask = task
        )
        _workers.value = _workers.value + worker
        postMessage("coordinator", name, "Worker spawned with role: $role. Task: ${task.ifEmpty { "Awaiting instructions" }}")
        return worker
    }

    fun updateWorkerStatus(id: String, status: WorkerStatus, result: String? = null) {
        _workers.value = _workers.value.map {
            if (it.id == id) it.copy(status = status, resultSummary = result ?: it.resultSummary) else it
        }
    }

    fun postMessage(from: String, to: String, content: String) {
        val msg = BoardMessage(from = from, to = to, content = content)
        _boardMessages.value = _boardMessages.value + msg
    }

    fun clearSwarm() {
        _workers.value = emptyList()
        _boardMessages.value = emptyList()
    }

    fun getSwarmOverview(): String {
        val list = _workers.value
        if (list.isEmpty()) return "Swarm is currently empty. No active parallel workers."
        val sb = StringBuilder("Active Swarm Roster (${list.size} agents):\n")
        list.forEach { w ->
            sb.append("- [${w.name}] (${w.role}) | Model: ${w.model} | Status: ${w.status} | Task: ${w.currentTask}\n")
            w.resultSummary?.let { sb.append("  Output: $it\n") }
        }
        return sb.toString()
    }
}
