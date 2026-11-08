package com.otphub.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Opt-in, on-device OTP notification detector.
 * Only numeric code candidates and timestamps are retained locally.
 * Upload occurs only after the user enables encrypted automatic sync.
 */
class OtpNotificationListener : NotificationListenerService() {
    private val uploadWorker = Executors.newSingleThreadExecutor()

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
        val now = System.currentTimeMillis()
        next.put(JSONObject().put("code", code).put("receivedAt", now))
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            val receivedAt = item.optLong("receivedAt")
            if (now - receivedAt <= 24L * 60L * 60L * 1000L && item.optString("code") != code && next.length() < 10) next.put(item)
        }
        prefs.edit().putString("detected", next.toString()).apply()

        val autoPrefs = getSharedPreferences("otp_hub_autosync", MODE_PRIVATE)
        if (autoPrefs.getBoolean("enabled", false)) {
            uploadWorker.execute { uploadEncryptedSnapshot() }
        }
    }

    private fun uploadEncryptedSnapshot() {
        try {
            val session = getSharedPreferences("otp_hub_session", MODE_PRIVATE)
            val token = session.getString("token", "").orEmpty()
            if (token.isBlank()) return
            val autoPrefs = getSharedPreferences("otp_hub_autosync", MODE_PRIVATE)
            val protected = autoPrefs.getString("protected_passphrase", "").orEmpty()
            if (protected.isBlank()) return
            val passphrase = AutoSyncCrypto.unprotect(this, protected)
            val stored = getSharedPreferences("otp_hub_notifications", MODE_PRIVATE)
            val detected = try { JSONArray(stored.getString("detected", "[]")) } catch (_: Exception) { JSONArray() }
            val recent = JSONArray()
            val now = System.currentTimeMillis()
            for (i in 0 until detected.length()) {
                val item = detected.optJSONObject(i) ?: continue
                if (now - item.optLong("receivedAt") <= 24L * 60L * 60L * 1000L) recent.put(item)
            }
            val encrypted = AutoSyncCrypto.encryptSnapshot(recent, passphrase)
            val payload = JSONObject()
                .put("p_token", token)
                .put("p_encrypted_payload", encrypted)
            val conn = (URL("$ENDPOINT/rest/v1/rpc/otp_hub_otp_inbox_save").openConnection() as HttpURLConnection)
            conn.requestMethod = "POST"
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.doOutput = true
            conn.setRequestProperty("apikey", PUBLIC_KEY)
            conn.setRequestProperty("Authorization", "Bearer $PUBLIC_KEY")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            if (status in 200..299) {
                autoPrefs.edit().putLong("last_success_at", System.currentTimeMillis()).remove("last_error").apply()
            } else {
                conn.errorStream?.close()
                autoPrefs.edit().putString("last_error", "Upload failed (HTTP $status)").apply()
            }
            conn.disconnect()
        } catch (_: Exception) {
            getSharedPreferences("otp_hub_autosync", MODE_PRIVATE)
                .edit().putString("last_error", "Automatic upload could not complete; it will retry after another detected OTP.").apply()
        }
    }

    companion object {
        private const val ENDPOINT = "https://xllstbnkvtustctpkbap.supabase.co"
        private const val PUBLIC_KEY = "sb_publishable_Ot6DZiGzEaebJMDSjiC23Q_1BwRqCYP"
        private val KEYWORDS = Regex("(?i)\\b(otp|one[- ]time|verification|verify|security code|login code|passcode)\\b")
        private val CODE = Regex("(?<!\\d)\\d{4,8}(?!\\d)")
    }
}
