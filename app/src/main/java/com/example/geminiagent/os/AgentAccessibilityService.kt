package com.example.geminiagent.os

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class AgentAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AgentAccessibilityService? = null

        // Privacy blocklist: Prevent reading sensitive financial/password apps
        private val SENSITIVE_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",                       // PhonePe
            "net.one97.paytm",                       // Paytm
            "in.org.npci.upiapp",                    // BHIM
            "com.sbi.lotusintouch",                  // SBI Yono
            "com.axis.mobile",                       // Axis Bank
            "com.msf.kbank.mobile",                  // Kotak Bank
            "com.csam.icici.bank.imobile"            // ICICI Bank
        )

        fun isServiceEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val myPackage = context.packageName
            return enabledServices.any { it.resolveInfo.serviceInfo.packageName == myPackage }
        }

        fun openSettingsIntent(): Intent {
            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun getScreenContent(): String {
        val root = rootInActiveWindow ?: return "Error: Cannot capture screen (no active window)."
        val currentPackage = root.packageName?.toString() ?: ""

        if (SENSITIVE_PACKAGES.contains(currentPackage)) {
            return "Security Protection: Screen reading is blocked for sensitive banking/UPI app ($currentPackage)."
        }

        val sb = StringBuilder()
        sb.append("Current App: $currentPackage\nVisible UI Elements:\n")

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val viewId = node.viewIdResourceName ?: ""
            val isClickable = node.isClickable
            val isEditable = node.isEditable

            val label = if (text.isNotEmpty()) text else desc
            if (label.isNotEmpty() || isClickable || isEditable) {
                val indent = "  ".repeat(depth.coerceAtMost(5))
                val type = when {
                    isEditable -> "[Input]"
                    isClickable -> "[Button/Clickable]"
                    else -> "[Text]"
                }
                sb.append("$indent$type '$label'")
                if (viewId.isNotEmpty()) sb.append(" (id: ${viewId.substringAfterLast('/')})")
                sb.append("\n")
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth + 1)
                child.recycle()
            }
        }

        traverse(root, 0)
        root.recycle()
        return if (sb.length > 50) sb.toString() else "Screen appears empty or elements are non-readable."
    }

    fun clickElement(targetTextOrId: String): String {
        val root = rootInActiveWindow ?: return "Error: No active window to click."
        val query = targetTextOrId.trim().lowercase(Locale.ROOT)

        var foundNode: AccessibilityNodeInfo? = null

        fun find(node: AccessibilityNodeInfo) {
            if (foundNode != null) return
            val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
            val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
            val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

            if (text.contains(query) || desc.contains(query) || viewId.contains(query)) {
                // Find nearest clickable ancestor or self
                var curr: AccessibilityNodeInfo? = node
                while (curr != null) {
                    if (curr.isClickable) {
                        foundNode = curr
                        return
                    }
                    curr = curr.parent
                }
                // If not marked clickable, click self anyway
                foundNode = node
                return
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                find(child)
                if (foundNode == null) child.recycle()
            }
        }

        find(root)

        return if (foundNode != null) {
            val clicked = foundNode!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            foundNode!!.recycle()
            if (clicked) "Successfully clicked on '$targetTextOrId'" else "Failed to click on '$targetTextOrId'"
        } else {
            root.recycle()
            "Element matching '$targetTextOrId' not found on screen."
        }
    }

    fun executeGlobal(actionName: String): String {
        val action = when (actionName.uppercase(Locale.ROOT)) {
            "HOME" -> GLOBAL_ACTION_HOME
            "BACK" -> GLOBAL_ACTION_BACK
            "RECENTS" -> GLOBAL_ACTION_RECENTS
            "NOTIFICATIONS" -> GLOBAL_ACTION_NOTIFICATIONS
            "QUICK_SETTINGS" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return "Invalid global action: $actionName. Use HOME, BACK, RECENTS, or NOTIFICATIONS."
        }
        val res = performGlobalAction(action)
        return if (res) "Executed $actionName navigation." else "Failed to perform $actionName."
    }
}
