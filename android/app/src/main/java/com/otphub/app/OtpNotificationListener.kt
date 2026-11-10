package com.otphub.app

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Opt-in, on-device OTP notification detector.
 * It processes only notification text Android exposes to this listener.
 * Full notification content is never persisted or uploaded.
 */
class OtpNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        getSharedPreferences("otp_hub_autosync", MODE_PRIVATE).edit()
            .putLong("listener_connected_at", System.currentTimeMillis())
            .remove("listener_disconnected_at")
            .apply()
        // Catch notifications that are still active when Android reconnects.
        // Dismissed/hidden notification history is not accessible.
        try {
            activeNotifications?.forEach(::processNotification)
        } catch (_: SecurityException) {
            // Android may temporarily withhold active-notification access.
        }
    }

    override fun onListenerDisconnected() {
        getSharedPreferences("otp_hub_autosync", MODE_PRIVATE).edit()
            .putLong("listener_disconnected_at", System.currentTimeMillis())
            .apply()
        requestRebind(ComponentName(this, OtpNotificationListener::class.java))
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn)
    }

    private fun processNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        try {
            val content = collectNotificationText(sbn)
            val code = findOtp(content) ?: return
            val source = when (sbn.packageName) {
                "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
                else -> BRAND.find(content)?.value?.let(::canonicalBrand) ?: try {
                    val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString().take(48)
                } catch (_: Exception) {
                    sbn.packageName.substringAfterLast('.').take(48).ifBlank { "Unknown app" }
                }
            }

            val prefs = getSharedPreferences("otp_hub_notifications", MODE_PRIVATE)
            val old = try { JSONArray(prefs.getString("detected", "[]")) } catch (_: Exception) { JSONArray() }
            val now = System.currentTimeMillis()
            val history = mutableListOf<JSONObject>()
            val duplicate = (0 until old.length()).any { i ->
                val item = old.optJSONObject(i) ?: return@any false
                item.optString("code") == code &&
                    item.optString("source", "Unknown app") == source &&
                    kotlin.math.abs(now - item.optLong("receivedAt")) <= 30_000L
            }
            if (!duplicate) {
                history.add(JSONObject().put("code", code).put("source", source)
                    .put("receivedAt", now).put("channel", "Notification"))
            }
            for (i in 0 until old.length()) {
                val item = old.optJSONObject(i) ?: continue
                if (now - item.optLong("receivedAt") <= HISTORY_WINDOW_MS) {
                    history.add(JSONObject(item.toString()))
                }
            }
            history.sortByDescending { it.optLong("receivedAt") }
            val next = JSONArray()
            history.take(MAX_HISTORY).forEach(next::put)
            prefs.edit().putString("detected", next.toString()).apply()

            if (getSharedPreferences("otp_hub_autosync", MODE_PRIVATE).getBoolean("enabled", false)) {
                scheduleEncryptedUpload()
            }
        } catch (_: Exception) {
            // A malformed notification must not crash Android's listener service.
        }
    }

    private fun collectNotificationText(sbn: StatusBarNotification): String {
        val notification = sbn.notification
        val extras = notification.extras ?: Bundle.EMPTY
        val parts = mutableListOf<String>()
        fun add(value: CharSequence?) {
            val text = value?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) parts.add(text)
        }
        add(extras.getCharSequence(Notification.EXTRA_TITLE))
        add(extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
        add(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))
        add(extras.getCharSequence(Notification.EXTRA_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_INFO_TEXT))
        add(notification.tickerText)
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach(::add)
        // MessagingStyle notifications (including WhatsApp) commonly expose
        // message text as Bundle entries in android.messages.
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { item ->
            when (item) {
                is Notification.MessagingStyle.Message -> add(item.text)
                is Bundle -> add(item.getCharSequence("text"))
            }
        }
        extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES)?.forEach { item ->
            when (item) {
                is Notification.MessagingStyle.Message -> add(item.text)
                is Bundle -> add(item.getCharSequence("text"))
            }
        }
        return parts.joinToString(" ").take(MAX_NOTIFICATION_TEXT)
    }

    private fun findOtp(content: String): String? {
        val raw = CONTEXT_BEFORE_CODE.find(content)?.groupValues?.getOrNull(1)
            ?: CODE_BEFORE_CONTEXT.find(content)?.groupValues?.getOrNull(1)
            ?: EXPLICIT_CODE.find(content)?.groupValues?.getOrNull(1)
        return raw?.filter(Char::isDigit)?.takeIf { it.length in 4..8 }
    }

    private fun scheduleEncryptedUpload() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<OtpUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "otp-hub-otp-upload", ExistingWorkPolicy.APPEND_OR_REPLACE, request
        )
    }

    companion object {
        private const val HISTORY_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
        private const val MAX_HISTORY = 50
        private const val MAX_NOTIFICATION_TEXT = 12000
        private const val OTP_CONTEXT = "(?:otp|one[- ]time|verification|verify|security|login|passcode|password|code|pin|authentication|auth|account|whatsapp)"
        private val CONTEXT_BEFORE_CODE = Regex("(?i)\\b$OTP_CONTEXT\\b[\\s\\S]{0,100}?(?<!\\d)(\\d{3}[- ]\\d{3}|\\d{4,8})(?!\\d)")
        private val CODE_BEFORE_CONTEXT = Regex("(?i)(?<!\\d)(\\d{3}[- ]\\d{3}|\\d{4,8})(?!\\d)[\\s\\S]{0,100}\\b$OTP_CONTEXT\\b")
        private val EXPLICIT_CODE = Regex("(?i)\\b(?:use|enter|type|submit)\\s+(?:the\\s+)?(?:code|otp|pin)\\s*[:is-]*\\s*(\\d{4,8})\\b")
        private val BRAND = Regex("(?i)\\b(Rapido|Uber|Ola|Swiggy|Zomato|Amazon|Flipkart|Paytm|PhonePe|Google|Microsoft|Telegram|WhatsApp|IRCTC|Meesho|Ajio|Myntra)\\b")

        private fun canonicalBrand(value: String): String = when (value.lowercase()) {
            "rapido" -> "Rapido"; "uber" -> "Uber"; "ola" -> "Ola"; "swiggy" -> "Swiggy"
            "zomato" -> "Zomato"; "amazon" -> "Amazon"; "flipkart" -> "Flipkart"
            "paytm" -> "Paytm"; "phonepe" -> "PhonePe"; "google" -> "Google"
            "microsoft" -> "Microsoft"; "telegram" -> "Telegram"; "whatsapp" -> "WhatsApp"
            "irctc" -> "IRCTC"; "meesho" -> "Meesho"; "ajio" -> "AJIO"; "myntra" -> "Myntra"
            else -> value.take(48)
        }
    }
}
