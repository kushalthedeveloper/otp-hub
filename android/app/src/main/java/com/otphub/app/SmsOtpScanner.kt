package com.otphub.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads only the newest five SMS inbox rows after explicit READ_SMS permission.
 * Bodies are inspected in memory and never persisted; at most five OTP candidates
 * are returned.
 */
object SmsOtpScanner {
    private val contextBeforeCode = Regex(
        "(?i)\\b(?:otp|one[- ]time|verification|verify|security|login|passcode|password|code|pin)\\b[\\s\\S]{0,80}?(?<!\\d)(\\d{3}[- ]\\d{3}|\\d{4,8})(?!\\d)"
    )
    private val codeBeforeContext = Regex(
        "(?i)(?<!\\d)(\\d{3}[- ]\\d{3}|\\d{4,8})(?!\\d)[\\s\\S]{0,80}\\b(?:otp|one[- ]time|verification|verify|security|login|passcode|password|code|pin)\\b"
    )

    fun scanLastFive(context: Context): JSONArray {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("SMS permission is not enabled")
        }

        val results = mutableListOf<JSONObject>()
        val seen = mutableSetOf<String>()
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY)
        // Android's ContentResolver query sortOrder does not reliably accept SQL LIMIT.
        // Sort descending and stop after five cursor rows explicitly.
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            var examined = 0
            while (cursor.moveToNext() && examined < 5) {
                examined++
                if (bodyIndex < 0) continue
                val body = cursor.getString(bodyIndex).orEmpty()
                val rawCode = contextBeforeCode.find(body)?.groupValues?.getOrNull(1)
                    ?: codeBeforeContext.find(body)?.groupValues?.getOrNull(1)
                    ?: continue
                val code = rawCode.filter { it.isDigit() }.takeIf { it.length in 4..8 } ?: continue
                val address = if (addressIndex >= 0) cursor.getString(addressIndex).orEmpty() else ""
                val receivedAt = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
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
