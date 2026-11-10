package com.otphub.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads at most the 500 newest SMS inbox rows after explicit READ_SMS permission.
 * It returns the five newest OTP matches, not merely matches in the five newest messages.
 * Message bodies stay in memory and are never persisted or uploaded.
 */
object SmsOtpScanner {
    private const val MAX_MESSAGES_TO_EXAMINE = 500
    private const val MAX_OTP_MATCHES = 5

    private val contextBeforeCode = Regex(
        "(?i)\\b(?:otp|one[- ]time(?: password)?|verification|verify|security|login|passcode|password|code|pin|authentication)\\b[\\s\\S]{0,120}?(?<!\\d)(\\d{3}[- ]\\d{3}|\\d{4,8})(?!\\d)"
    )
    private val codeBeforeContext = Regex(
        "(?i)(?<!\\d)(\\d{3}[- ]\\d{3}|\\d{4,8})(?!\\d)[\\s\\S]{0,120}\\b(?:otp|one[- ]time|verification|verify|security|login|passcode|password|code|pin|authentication)\\b"
    )
    private val explicitCode = Regex(
        "(?i)\\b(?:use|enter|type|submit)\\s+(?:the\\s+)?(?:code|otp|pin)\\s*[:is-]*\\s*(\\d{4,8})\\b"
    )

    fun scanLastFive(context: Context): JSONArray {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("SMS permission is not enabled")
        }

        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY)
        val results = mutableListOf<JSONObject>()
        val seen = mutableSetOf<String>()
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, projection, null, null,
            "\${Telephony.Sms.DATE} DESC"
        ) ?: throw IllegalStateException("Android did not provide the SMS inbox cursor")

        cursor.use {
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            if (bodyIndex < 0 || dateIndex < 0) {
                throw IllegalStateException("The SMS provider returned an incomplete inbox record")
            }

            var examined = 0
            while (examined < MAX_MESSAGES_TO_EXAMINE && results.size < MAX_OTP_MATCHES && it.moveToNext()) {
                examined++
                val body = it.getString(bodyIndex).orEmpty()
                val rawCode = contextBeforeCode.find(body)?.groupValues?.getOrNull(1)
                    ?: codeBeforeContext.find(body)?.groupValues?.getOrNull(1)
                    ?: explicitCode.find(body)?.groupValues?.getOrNull(1)
                    ?: continue
                val code = rawCode.filter(Char::isDigit).takeIf { value -> value.length in 4..8 } ?: continue
                val address = if (addressIndex >= 0) it.getString(addressIndex).orEmpty() else ""
                val receivedAt = it.getLong(dateIndex)
                val key = "$code|$address|$receivedAt"
                if (!seen.add(key)) continue
                results.add(JSONObject()
                    .put("code", code)
                    .put("source", address.take(48).ifBlank { "SMS" })
                    .put("receivedAt", receivedAt)
                    .put("channel", "SMS"))
            }
        }
        return JSONArray(results)
    }
}
