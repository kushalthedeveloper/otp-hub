package com.otphub.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Encrypted inbox uploader. WorkManager persists and retries this task when
 * connectivity returns or Android temporarily defers background work.
 */
class OtpUploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val autoPrefs = applicationContext.getSharedPreferences("otp_hub_autosync", Context.MODE_PRIVATE)
        if (!autoPrefs.getBoolean("enabled", false)) return Result.success()

        return try {
            val session = applicationContext.getSharedPreferences("otp_hub_session", Context.MODE_PRIVATE)
            val token = session.getString("token", "").orEmpty()
            val protected = autoPrefs.getString("protected_passphrase", "").orEmpty()
            if (token.isBlank() || protected.isBlank()) return Result.success()

            val passphrase = AutoSyncCrypto.unprotect(applicationContext, protected)
            val stored = applicationContext.getSharedPreferences("otp_hub_notifications", Context.MODE_PRIVATE)
            val detected = try { JSONArray(stored.getString("detected", "[]")) } catch (_: Exception) { JSONArray() }
            val recent = JSONArray()
            val now = System.currentTimeMillis()
            for (i in 0 until detected.length()) {
                val item = detected.optJSONObject(i) ?: continue
                if (now - item.optLong("receivedAt") <= 24L * 60L * 60L * 1000L) recent.put(item)
            }

            val payload = JSONObject()
                .put("p_token", token)
                .put("p_encrypted_payload", AutoSyncCrypto.encryptSnapshot(recent, passphrase))
            val conn = (URL("$ENDPOINT/rest/v1/rpc/otp_hub_otp_inbox_save").openConnection() as HttpURLConnection)
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("apikey", PUBLIC_KEY)
            conn.setRequestProperty("Authorization", "Bearer $PUBLIC_KEY")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            if (status in 200..299) {
                autoPrefs.edit().putLong("last_success_at", System.currentTimeMillis()).remove("last_error").apply()
                conn.inputStream?.close()
                conn.disconnect()
                Result.success()
            } else {
                conn.errorStream?.close()
                conn.disconnect()
                autoPrefs.edit().putString("last_error", "Upload failed (HTTP $status)").apply()
                if (status == 408 || status == 429 || status >= 500) Result.retry() else Result.failure()
            }
        } catch (_: Exception) {
            autoPrefs.edit().putString("last_error", "Automatic upload is waiting for a retry.").apply()
            Result.retry()
        }
    }

    companion object {
        private const val ENDPOINT = "https://xllstbnkvtustctpkbap.supabase.co"
        private const val PUBLIC_KEY = "sb_publishable_Ot6DZiGzEaebJMDSjiC23Q_1BwRqCYP"
    }
}
