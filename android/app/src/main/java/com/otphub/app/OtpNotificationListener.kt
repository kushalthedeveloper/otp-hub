package com.otphub.app

import android.content.ComponentName
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
 * Stores only numeric candidates, source app labels, and timestamps.
 * Upload is encrypted on-device and scheduled through WorkManager for retry.
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
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val body = extras.getCharSequence("android.text")?.toString().orEmpty()
        val big = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val content = "$title $body $big"
        if (!KEYWORDS.containsMatchIn(content)) return
        val code = CODE.find(content)?.value ?: return

        // Prefer the service brand when the notification explicitly names it.
        // Otherwise use the notification-posting app's public display label.
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
        // Keep distinct OTP events in history. Only suppress duplicate callbacks
        // for the same code/source arriving within a short 30-second window.
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
            val receivedAt = item.optLong("receivedAt")
            if (now - receivedAt <= 7L * 24L * 60L * 60L * 1000L) history.add(JSONObject(item.toString()))
        }
        // Newest first; cap local history to 50 events to avoid unbounded storage.
        history.sortByDescending { it.optLong("receivedAt") }
        val next = JSONArray()
        history.take(50).forEach { next.put(it) }
        prefs.edit().putString("detected", next.toString()).apply()

        val autoPrefs = getSharedPreferences("otp_hub_autosync", MODE_PRIVATE)
        if (autoPrefs.getBoolean("enabled", false)) scheduleEncryptedUpload()
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
        private val KEYWORDS = Regex("(?i)\\b(otp|one[- ]time|verification|verify|security code|login code|passcode)\\b")
        private val CODE = Regex("(?<!\\d)\\d{4,8}(?!\\d)")
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
