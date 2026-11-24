package com.example.geminiagent.security

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class PendingHitlAction(
    val id: String = UUID.randomUUID().toString().take(8),
    val toolName: String,
    val detail: String,
    val riskLevel: RiskLevel,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

object AppSecurityManager {

    private const val PREFS_NAME = "agent_security_prefs"
    private const val KEY_APP_LOCK = "app_lock_enabled"
    private const val KEY_HITL_STEP_UP = "hitl_step_up_enabled"
    private const val KEY_USE_BACKEND_PROXY = "use_backend_proxy"
    private const val KEY_BACKEND_PROXY_URL = "backend_proxy_url"
    private const val KEY_PROXY_AUTH_TOKEN = "proxy_auth_token"

    private val _pendingHitlAction = MutableStateFlow<PendingHitlAction?>(null)
    val pendingHitlAction: StateFlow<PendingHitlAction?> = _pendingHitlAction.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAppLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_APP_LOCK, false)
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_APP_LOCK, enabled).apply()
    }

    fun isHitlStepUpEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HITL_STEP_UP, true) // Enabled by default for safety
    }

    fun setHitlStepUpEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HITL_STEP_UP, enabled).apply()
    }

    fun isUsingBackendProxy(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_BACKEND_PROXY, false)
    }

    fun setUsingBackendProxy(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_BACKEND_PROXY, enabled).apply()
    }

    fun getBackendProxyUrl(context: Context): String {
        return getPrefs(context).getString(KEY_BACKEND_PROXY_URL, "") ?: ""
    }

    fun setBackendProxyUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_BACKEND_PROXY_URL, url.trim()).apply()
    }

    fun getProxyAuthToken(context: Context): String {
        return getPrefs(context).getString(KEY_PROXY_AUTH_TOKEN, "") ?: ""
    }

    fun setProxyAuthToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_PROXY_AUTH_TOKEN, token.trim()).apply()
    }

    fun isDeviceSecure(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isDeviceSecure ?: false
    }

    fun createDeviceCredentialIntent(context: Context, title: String = "Antigravity Agent Authentication", desc: String = "Verify biometric or device credential"): Intent? {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.createConfirmDeviceCredentialIntent(title, desc)
    }

    fun setPendingHitl(action: PendingHitlAction?) {
        _pendingHitlAction.value = action
    }

    fun clearPendingHitl() {
        _pendingHitlAction.value = null
    }
}
