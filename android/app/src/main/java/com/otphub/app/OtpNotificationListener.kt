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
 *
 * Android delivers silent notifications to this listener too, provided the
 * source app actually posts a notification and Notification Access is enabled.
 * The listener extracts only a likely numeric OTP and source label; it never
 * uploads full notification text.
 */
class OtpNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        getSharedPreferences("otp_hub_autosync", MODE_PRIVATE).edit()
            .putLong("listener_connected_at", System.currentTimeMillis())
            .remove("listener_disconnected_at")
            .apply()
    }

    override fun onListenerDisconnected() {
        getSharedPreferences("otp_hub_autosync", MODE_PRIVATE).edit()
            .putLong("listener_disconnected_at", System.currentTimeMillis())
            .apply()
        requestRebind(ComponentName(this, OtpNotificationListener::class.java))
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        val content = collectNotificationText(sbn)
        val code = findOtp(content) ?: return

        val source = BRAND.find(content)?.value?.let(::canonicalBrand) ?: try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString().take(48)
        } catch (_: Exception) {
            sbn.packageName.substringAfterLast('.').take(48).ifBlank { "Unknown app" }
        }

        val prefs = getSharedPreferences("otp_hub_notifications", MODE_PRIVATE)
        val old = try { JSONArray(prefs.getString("detected", "[]")) } catch (_: Exception) { JSONArray() }
        val now = System.currentTimeMillis()
        val history = mutableListOf<JSONObject>()
        val duplicateCallback = (0 until old.length()).any { i ->
            val item = old.optJSONObject(i) ?: return@any false
            item.optString("code") == code &&
                item.optString("source", "Unknown app") == source &&
                kotlin.math.abs(now - item.optLong("receivedAt")) <= 30_000L
        }
        if (!duplicateCallback) {
            history.add(JSONObject().put("code", code).put("source", source).put("receivedAt", now))
        }
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            if (now - item.optLong("receivedAt") <= 7L * 24L * 60L * 60L * 1000L) {
                history.add(JSONObject(item.toString()))
            }
        }
        history.sortByDescending { it.optLong("receivedAt") }
        val next = JSONArray()
        history.take(50).forEach { next.put(it) }
        prefs.edit().putString("detected", next.toString()).apply()

        val autoPrefs = getSharedPreferences("otp_hub_autosync", MODE_PRIVATE)
        if (autoPrefs.getBoolean("enabled", false)) scheduleEncryptedUpload()
    }

    /**
     * Read the common Android notification fields, including InboxStyle lines
     * and MessagingStyle message bundles. This also covers notifications marked
     * silent; silence changes alert behavior, not listener delivery.
     */
    private fun collectNotificationText(sbn: StatusBarNotification): String {
        val notification = sbn.notification
        val extras = notification.extras ?: Bundle.EMPTY
        val parts = mutableListOf<String>()

        fun add(value: CharSequence?) {
            val text = value?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) parts.add(text)
        }

        add(extras.getCharSequence(Notification.EXTRA_TITLE))
        add(extras.getCharSequence(Notification.EXTRA_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_INFO_TEXT))
        add(notification.tickerText)

        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach(::add)

        // MessagingStyle stores message text in Parcelable Bundle entries.
        extras.getParcelableArray("android.messages")?.forEach { item ->
            val message = item as? Bundle ?: return@forEach
            add(message.getCharSequence("text"))
        }

        return parts.joinToString(" ").take(6000)
    }

    private fun findOtp(content: String): String? {
        // Match the code near a meaningful OTP/login/verification keyword in
        // either order. This avoids treating arbitrary dates or phone numbers
        // elsewhere in a notification as OTPs.
        val raw = CONTEXT_BEFORE_CODE.find(content)?.groupValues?.getOrNull(1)
            ?: CODE_BEFORE_CONTEXT.find(content)?.groupValues?.getOrNull(1)
        return raw?.filter { it.isDigit() }?.takeIf { it.length in 4..8 }
    }

    private fun scheduleEncryptedUpload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<OtpUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "otp-hub-otp-upload",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    companion object {
        private val OTP_CONTEXT = "(?:otp|one[- ]time|verification|verify|security|login|passcode|password|code|pin|authentication|auth|account)"
        private val CONTEXT_BEFORE_CODE = Regex("(?i)\\b$OTP_CONTEXT\\b[\\s\\S]{0,80}?(?<!\\d)(\\d{3}[- ]\\d{3}|\\d{4,8})(?!\\d)")
        private val CODE_BEFORE_CONTEXT = Regex("(?i)(?<!\\d)(\\d{3}[- ]\\d{3}|\\d{4,8})(?!\\d)[\\s\\S]{0,80}\\b$OTP_CONTEXT\\b")
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
