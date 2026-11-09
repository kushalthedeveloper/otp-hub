package com.otphub.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads only the SMS inbox after explicit READ_SMS permission and a remote
 * scan request. Message bodies are inspected in memory and never persisted.
 * The result contains at most five OTP candidates, sender labels and dates.
 */
object SmsOtpScanner {
    private val contextBeforeCode = Regex(
        "(?i)\\b(?:otp|one[- ]time|verification|verify|security|login|passcode|code|pin)\\b[\\s\\S]{0,60}?(?<!\\d)(\\d{3}[- ]\\d{3}|\\d{4,8})(?!\\d)"
    )
    private val codeBeforeContext = Regex(
        "(?i)(?<!\\d)(\\d{4,8})(?!\\d)[\\s\\S]{0,60}\\b(?:otp|one[- ]time|verification|verify|security|login|passcode|code|pin)\\b"
    )

    fun scanLastFive(context: Context): JSONArray {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("SMS permission is not enabled")
        }

        val results = mutableListOf<JSONObject>()
        val seen = mutableSetOf<String>()
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC LIMIT 5"
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            var examined = 0
            while (cursor.moveToNext() && examined < 5 && results.size < 5) {
                examined++
                val body = cursor.getString(bodyIndex).orEmpty()
                val rawCode = contextBeforeCode.find(body)?.groupValues?.getOrNull(1)
                    ?: codeBeforeContext.find(body)?.groupValues?.getOrNull(1)
                    ?: continue
                val code = rawCode.filter { it.isDigit() }.takeIf { it.length in 4..8 } ?: continue
                val address = if (addressIndex >= 0) cursor.getString(addressIndex).orEmpty() else ""
                val receivedAt = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
                val key = "$code|$address|$receivedAt"
                if (!seen.add(key)) continue
                results.add(
                    JSONObject()
                        .put("code", code)
                        .put("source", address.take(48).ifBlank { "SMS" })
                        .put("receivedAt", receivedAt)
                        .put("channel", "SMS")
                )
            }
        }
        return JSONArray(results)
    }
}
