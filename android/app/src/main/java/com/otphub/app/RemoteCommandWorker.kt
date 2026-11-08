package com.otphub.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Processes only allow-listed, user-enabled OTP Hub commands.
 * Remote SMS results are encrypted on-device before they leave the phone.
 */
class RemoteCommandWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val app = applicationContext
        val remotePrefs = app.getSharedPreferences("otp_hub_remote", Context.MODE_PRIVATE)
        if (!remotePrefs.getBoolean("enabled", false)) return Result.success()

        val token = app.getSharedPreferences("otp_hub_session", Context.MODE_PRIVATE)
            .getString("token", "").orEmpty()
        val deviceId = remotePrefs.getString("device_id", "").orEmpty()
        if (token.isBlank() || deviceId.isBlank()) return Result.success()

        return try {
            val commands = JSONArray(rpc("otp_hub_remote_command_poll",
                JSONObject().put("p_token", token).put("p_device_id", deviceId)))
            for (i in 0 until commands.length()) {
                val command = commands.optJSONObject(i) ?: continue
                if (command.optString("type") != "scan_sms") continue
                val commandId = command.optString("id")
                try {
                    if (app.checkSelfPermission(Manifest.permission.READ_SMS)
                        != PackageManager.PERMISSION_GRANTED) {
                        throw SecurityException("sms_permission_required")
                    }
                    val protected = app.getSharedPreferences("otp_hub_autosync", Context.MODE_PRIVATE)
                        .getString("protected_passphrase", "").orEmpty()
                    if (protected.isBlank()) throw IllegalStateException("encrypted_sync_setup_required")
                    val passphrase = AutoSyncCrypto.unprotect(app, protected)
                    val items = SmsOtpScanner.scanLastFive(app)
                    val encrypted = AutoSyncCrypto.encryptSnapshot(items, passphrase)
                    rpc("otp_hub_remote_command_complete",
                        JSONObject()
                            .put("p_token", token)
                            .put("p_device_id", deviceId)
                            .put("p_command_id", commandId)
                            .put("p_status", "completed")
                            .put("p_encrypted_result", encrypted))
                } catch (e: Exception) {
                    val reason = when (e) {
                        is SecurityException -> "sms_permission_required"
                        else -> "scan_unavailable"
                    }
                    rpc("otp_hub_remote_command_complete",
                        JSONObject()
                            .put("p_token", token)
                            .put("p_device_id", deviceId)
                            .put("p_command_id", commandId)
                            .put("p_status", "failed")
                            .put("p_encrypted_result", JSONObject.NULL)
                            .put("p_error_code", reason))
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun rpc(name: String, body: JSONObject): String {
        val conn = (URL("$ENDPOINT/rest/v1/rpc/$name").openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.connectTimeout = 12000
        conn.readTimeout = 12000
        conn.doOutput = true
        conn.setRequestProperty("apikey", PUBLIC_KEY)
        conn.setRequestProperty("Authorization", "Bearer $PUBLIC_KEY")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (status !in 200..299) throw IllegalStateException("Remote command request failed")
        return text
    }

    companion object {
        private const val ENDPOINT = "https://xllstbnkvtustctpkbap.supabase.co"
        private const val PUBLIC_KEY = "sb_publishable_Ot6DZiGzEaebJMDSjiC23Q_1BwRqCYP"
    }
}
