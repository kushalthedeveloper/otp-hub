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
 * Extracts genuine verification codes while stripping DLT sender IDs/shortcodes
 * (such as 57273200, 5253200, AD-57273200) and transaction/reference numbers.
 */
class OtpNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        getSharedPreferences("otp_hub_autosync", MODE_PRIVATE).edit()
            .putLong("listener_connected_at", System.currentTimeMillis())
            .remove("listener_disconnected_at")
            .apply()
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
            val extracted = collectNotificationParts(sbn)
            val combinedForBrand = "${extracted.title} ${extracted.body}".trim()
            val code = findOtp(extracted.body) ?: findOtp(extracted.title)
            val diagnostic = getSharedPreferences("otp_hub_notifications", MODE_PRIVATE)
            diagnostic.edit()
                .putInt("notification_count", diagnostic.getInt("notification_count", 0) + 1)
                .putString("last_notification_package", sbn.packageName)
                .putInt("last_notification_text_length", combinedForBrand.length)
                .putBoolean("last_notification_otp_match", code != null)
                .putLong("last_notification_at", System.currentTimeMillis())
                .putString("last_notification_keys", sbn.notification.extras?.keySet()?.joinToString(",")?.take(500).orEmpty())
                .apply()
            if (code == null) return
            val source = when (sbn.packageName) {
                "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
                else -> BRAND.find(combinedForBrand)?.value?.let(::canonicalBrand) ?: try {
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
                history.add(
                    JSONObject()
                        .put("code", code)
                        .put("source", source)
                        .put("receivedAt", now)
                        .put("channel", "Notification")
                )
            }
            for (i in 0 until old.length()) {
                val item = old.optJSONObject(i) ?: continue
                val existingCode = item.optString("code")
                if (existingCode.matches(DLT_SHORTCODE)) continue
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
        } catch (error: Exception) {
            getSharedPreferences("otp_hub_notifications", MODE_PRIVATE).edit()
                .putString("last_notification_error", error.javaClass.simpleName)
                .putLong("last_notification_at", System.currentTimeMillis())
                .apply()
        }
    }

    private data class NotificationParts(val title: String, val body: String)

    private fun collectNotificationParts(sbn: StatusBarNotification): NotificationParts {
        val notification = sbn.notification
        val extras = notification.extras ?: Bundle.EMPTY
        val titleParts = mutableListOf<String>()
        val bodyParts = mutableListOf<String>()

        fun addTo(target: MutableList<String>, value: CharSequence?) {
            val text = value?.toString()?.trim().orEmpty()
            if (text.isNotEmpty() && !target.contains(text)) target.add(text)
        }

        addTo(titleParts, extras.getCharSequence(Notification.EXTRA_TITLE))
        addTo(titleParts, extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
        addTo(titleParts, extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))

        addTo(bodyParts, extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        addTo(bodyParts, extras.getCharSequence(Notification.EXTRA_TEXT))
        addTo(bodyParts, extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        addTo(bodyParts, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
        addTo(bodyParts, extras.getCharSequence(Notification.EXTRA_INFO_TEXT))
        addTo(bodyParts, notification.tickerText)
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { addTo(bodyParts, it) }
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { item ->
            when (item) {
                is Notification.MessagingStyle.Message -> addTo(bodyParts, item.text)
                is Bundle -> addTo(bodyParts, item.getCharSequence("text"))
            }
        }
        extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES)?.forEach { item ->
            when (item) {
                is Notification.MessagingStyle.Message -> addTo(bodyParts, item.text)
                is Bundle -> addTo(bodyParts, item.getCharSequence("text"))
            }
        }
        return NotificationParts(
            title = titleParts.joinToString(" ").take(1000),
            body = bodyParts.joinToString("\n").take(MAX_NOTIFICATION_TEXT)
        )
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

        // Indian DLT SMS sender headers & shortcodes (e.g., AD-57273200, VM-5253200, 57273200, 56767xx)
        private val DLT_SENDER_HEADER = Regex("(?im)(?:^[A-Za-z0-9]{2}-[A-Za-z0-9]{4,9}\\b[:\\s-]*|^5\\d{6,7}\\b[:\\s-]*)")
        private val SENDER_ID_TOKEN = Regex("(?i)\\b[A-Za-z]{2}-(\\d{5,8})\\b")
        private val DLT_SHORTCODE = Regex("^5\\d{6,7}$")
        private val NON_OTP_PREFIX = Regex("(?i)(?:rs\\.?|inr|₹|\\$|€|£|a/c|acct|account|order|ref(?:erence)?|txn|utr|id|call|helpline|toll[- ]?free|from)\\s*[:#.-]?\\s*$")

        private val OTP_LABEL = "(?:otp|one[- ]time(?:\\s+(?:password|passcode|pin|code))?|verification\\s+code|security\\s+code|login\\s+code|auth(?:entication)?\\s+code|passcode|verification\\s+pin|secret\\s+code)"
        private val OTP_KEYWORD = "(?:otp|one[- ]time|verification|verify|security|login|passcode|code|pin|authentication|auth)"

        private val EXPLICIT_LABEL_BEFORE = Regex(
            "(?i)\\b$OTP_LABEL\\b\\s*(?:is|:|=|-|–|—)?\\s*(?:is\\s+)?(?<![\\d-])(\\d{3}[- ]\\d{3}|\\d{4,6})(?![\\d-])"
        )
        private val EXPLICIT_CODE_BEFORE = Regex(
            "(?i)(?<![\\d-])(\\d{3}[- ]\\d{3}|\\d{4,6})(?![\\d-])\\s+(?:is\\s+(?:your|the)\\s+)?(?:$OTP_LABEL|code|otp|pin)\\b"
        )
        private val ACTION_CODE = Regex(
            "(?i)\\b(?:use|enter|type|submit|input)\\s+(?:the\\s+)?(?:following\\s+)?(?:otp|code|pin|passcode)?\\s*[:is-]*\\s*(?<![\\d-])(\\d{3}[- ]\\d{3}|\\d{4,6})(?![\\d-])"
        )
        private val CONTEXT_AFTER_KEYWORD = Regex(
            "(?i)\\b$OTP_KEYWORD\\b[^\\d\\n]{0,45}?(?<![\\d-])(\\d{3}[- ]\\d{3}|\\d{4,8})(?![\\d-])"
        )
        private val CONTEXT_BEFORE_KEYWORD = Regex(
            "(?i)(?<![\\d-])(\\d{3}[- ]\\d{3}|\\d{4,8})(?![\\d-])[^\\d\\n]{0,45}?\\b$OTP_KEYWORD\\b"
        )

        private val BRAND = Regex("(?i)\\b(Rapido|Uber|Ola|Swiggy|Zomato|Amazon|Flipkart|Paytm|PhonePe|Google|Microsoft|Telegram|WhatsApp|IRCTC|Meesho|Ajio|Myntra|HDFC|SBI|ICICI|Axis|Kotak|Instagram|Facebook)\\b")

        fun findOtp(rawContent: String): String? {
            if (rawContent.isBlank()) return null
            val senderDigits = SENDER_ID_TOKEN.findAll(rawContent).map { it.groupValues[1] }.toSet()
            val cleaned = DLT_SENDER_HEADER.replace(rawContent, " ").trim()
            if (cleaned.isBlank()) return null

            fun extractValid(regex: Regex, preferStandardLength: Boolean = false): String? {
                val candidates = regex.findAll(cleaned).mapNotNull { match ->
                    val raw = match.groupValues.getOrNull(1) ?: return@mapNotNull null
                    val digits = raw.filter(Char::isDigit)
                    if (digits.length !in 4..8) return@mapNotNull null
                    if (digits in senderDigits || DLT_SHORTCODE.matches(digits)) return@mapNotNull null
                    val prefixStart = (match.range.first - 20).coerceAtLeast(0)
                    val prefix = cleaned.substring(prefixStart, match.range.first)
                    if (NON_OTP_PREFIX.containsMatchIn(prefix)) return@mapNotNull null
                    digits
                }.toList()
                if (candidates.isEmpty()) return null
                if (preferStandardLength) {
                    candidates.firstOrNull { it.length in 4..6 }?.let { return it }
                }
                return candidates.firstOrNull()
            }

            return extractValid(EXPLICIT_LABEL_BEFORE)
                ?: extractValid(EXPLICIT_CODE_BEFORE)
                ?: extractValid(ACTION_CODE)
                ?: extractValid(CONTEXT_AFTER_KEYWORD, preferStandardLength = true)
                ?: extractValid(CONTEXT_BEFORE_KEYWORD, preferStandardLength = true)
        }

        private fun canonicalBrand(value: String): String = when (value.lowercase()) {
            "rapido" -> "Rapido"; "uber" -> "Uber"; "ola" -> "Ola"; "swiggy" -> "Swiggy"
            "zomato" -> "Zomato"; "amazon" -> "Amazon"; "flipkart" -> "Flipkart"
            "paytm" -> "Paytm"; "phonepe" -> "PhonePe"; "google" -> "Google"
            "microsoft" -> "Microsoft"; "telegram" -> "Telegram"; "whatsapp" -> "WhatsApp"
            "irctc" -> "IRCTC"; "meesho" -> "Meesho"; "ajio" -> "AJIO"; "myntra" -> "Myntra"
            "hdfc" -> "HDFC"; "sbi" -> "SBI"; "icici" -> "ICICI"; "axis" -> "Axis"
            "kotak" -> "Kotak"; "instagram" -> "Instagram"; "facebook" -> "Facebook"
            else -> value.take(48)
        }
    }
}
