package com.otphub.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * Opt-in, on-device OTP notification detector.
 * It never stores the source notification text, sender, or full message.
 * Only a candidate numeric code and its receive time are retained locally.
 * Nothing is transmitted to a server by this service.
 */
class OtpNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val body = extras.getCharSequence("android.text")?.toString().orEmpty()
        val big = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val content = "$title $body $big"
        if (!KEYWORDS.containsMatchIn(content)) return
        val code = CODE.find(content)?.value ?: return
        val prefs = getSharedPreferences("otp_hub_notifications", MODE_PRIVATE)
        val old = try { JSONArray(prefs.getString("detected", "[]")) } catch (_: Exception) { JSONArray() }
        val next = JSONArray()
        next.put(JSONObject().put("code", code).put("receivedAt", System.currentTimeMillis()))
        for (i in 0 until minOf(old.length(), 9)) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optString("code") != code) next.put(item)
        }
        prefs.edit().putString("detected", next.toString()).apply()
    }

    companion object {
        private val KEYWORDS = Regex("(?i)\\b(otp|one[- ]time|verification|verify|security code|login code|passcode)\\b")
        private val CODE = Regex("(?<!\\d)\\d{4,8}(?!\\d)")
    }
}