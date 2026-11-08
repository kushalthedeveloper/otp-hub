package com.otphub.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.security.SecureRandom

class MainActivity : Activity() {
    private val endpoint = "https://xllstbnkvtustctpkbap.supabase.co"
    private val publicKey = "sb_publishable_Ot6DZiGzEaebJMDSjiC23Q_1BwRqCYP"
    private val prefs by lazy { getSharedPreferences("otp_hub_session", MODE_PRIVATE) }
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private var token = ""
    private var username = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = prefs.getString("token", "") ?: ""
        username = prefs.getString("username", "") ?: ""
        if (token.isNotBlank()) validateSession() else showAuth()
    }

    private fun callRpc(name: String, body: JSONObject): JSONObject {
        val conn = (URL("$endpoint/rest/v1/rpc/$name").openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.doOutput = true
        conn.setRequestProperty("apikey", publicKey)
        conn.setRequestProperty("Authorization", "Bearer $publicKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) throw Exception(JSONObject(text).optString("message", "Request failed"))
        return if (text.startsWith("{")) JSONObject(text) else JSONObject().put("result", text)
    }

    private fun async(action: () -> Unit) {
        worker.execute {
            try { action() } catch (e: Exception) { runOnUiThread { message(e.message ?: "Something went wrong") } }
        }
    }

    private fun validateSession() = async {
        val result = callRpc("otp_hub_session", JSONObject().put("p_token", token))
        username = result.optString("username")
        if (username.isBlank()) { token = ""; prefs.edit().clear().apply(); runOnUiThread { showAuth() } }
        else runOnUiThread { showHome() }
    }

    private fun baseLayout(title: String, subtitle: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(24))
            background = rounded(0xFFF6F5FA.toInt(), 0, dp(24))
        }
        val scroll = ScrollView(this).apply { clipToPadding = false; setPadding(0, 0, 0, dp(12)); addView(root) }
        setContentView(scroll)
        root.addView(TextView(this).apply { text = "◈  OTP HUB"; textSize = 16f; setTextColor(0xFF6547C8.toInt()); setTypeface(null, Typeface.BOLD) })
        root.addView(TextView(this).apply { text = title; textSize = 27f; setTextColor(0xFF171522.toInt()); setTypeface(null, Typeface.BOLD); setPadding(0, dp(28), 0, dp(10)) })
        root.addView(TextView(this).apply { text = subtitle; textSize = 15f; setTextColor(0xFF555266.toInt()); setPadding(0, 0, 0, dp(22)) })
    }

    private fun field(hint: String, password: Boolean = false): EditText {
        val edit = EditText(this).apply {
            this.hint = hint
            textSize = 16f
            setTextColor(0xFF201D2B.toInt())
            setHintTextColor(0xFF777487.toInt())
            setSingleLine(true)
            minHeight = dp(62)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xFFFFFFFF.toInt())
            if (password) inputType = 129
        }
        root.addView(edit, LinearLayout.LayoutParams(-1, dp(66)).apply { bottomMargin = dp(16) })
        return edit
    }

    private fun sectionLabel(label: String) {
        root.addView(TextView(this).apply {
            text = label
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(0xFF77718D.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(2), dp(14), 0, dp(8))
        })
    }

    private fun button(label: String, action: () -> Unit) {
        val control = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            minHeight = dp(58)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(0xFF6246C7.toInt(), 0, dp(16))
            isClickable = true
            isFocusable = true
            elevation = dp(2).toFloat()
            setOnClickListener { action() }
        }
        root.addView(control, LinearLayout.LayoutParams(-1, dp(60)).apply { bottomMargin = dp(13); topMargin = dp(2) })
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun message(value: String) {
        root.addView(TextView(this).apply { text = value; textSize = 14f; setTextColor(0xFFB3261E.toInt()); setPadding(0, 12, 0, 12) })
    }

    private fun showAuth() {
        runOnUiThread {
            baseLayout("Your OTP inbox", "One simple place to detect and securely sync your verification codes.")
            val user = field("Username")
            val pass = field("Password (12+ characters)", true)
            button("Sign in") {
                async {
                    val res = callRpc("otp_hub_login", JSONObject().put("p_username", user.text.toString()).put("p_password", pass.text.toString()))
                    saveSession(res); runOnUiThread { showHome() }
                }
            }
            button("Create OTP Hub account") {
                async {
                    val res = callRpc("otp_hub_register", JSONObject().put("p_username", user.text.toString()).put("p_password", pass.text.toString()))
                    saveSession(res)
                    val recovery = res.optString("recovery_code")
                    runOnUiThread {
                        baseLayout("Save your recovery code", "This is shown only once. Keep it somewhere private and offline.")
                        root.addView(TextView(this).apply { text = recovery; textSize = 25f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF29213F.toInt()); gravity = Gravity.CENTER; setPadding(0, 28, 0, 28) })
                        button("I saved it — continue") { showHome() }
                    }
                }
            }
            root.addView(TextView(this).apply { text = "Username: 3–24 letters, numbers or underscores. Password: 12–128 characters."; textSize = 12f; setTextColor(0xFF777488.toInt()); setPadding(0, 16, 0, 0) })
        }
    }

    private fun saveSession(res: JSONObject) {
        token = res.getString("session_token")
        username = res.getString("username")
        prefs.edit().putString("token", token).putString("username", username).apply()
    }

    private fun showHome() {
        runOnUiThread {
            baseLayout("Welcome, $username", "Your verification codes, kept simple and private.")
            sectionLabel("PHONE SETUP")
            button("1  ·  Enable notification access") {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            sectionLabel("YOUR OTP INBOX")
            button("2  ·  View detected OTPs") { showDetectedOtps() }
            sectionLabel("SECURE WEBSITE SYNC")
            val pass = field("OTP Hub account password", true)
            button("Sync OTPs to website") {
                val phrase = pass.text.toString()
                if (phrase.length < 12) { message("Enter your OTP Hub account password (12+ characters)."); return@button }
                async {
                    val prefs = getSharedPreferences("otp_hub_notifications", MODE_PRIVATE)
                    val detected = try { JSONArray(prefs.getString("detected", "[]")) } catch (_: Exception) { JSONArray() }
                    val now = System.currentTimeMillis()
                    val recent = JSONArray()
                    for (i in 0 until detected.length()) {
                        val item = detected.optJSONObject(i) ?: continue
                        if (now - item.optLong("receivedAt") <= 24L * 60L * 60L * 1000L) recent.put(item)
                    }
                    val encrypted = encryptOtpSnapshot(recent, phrase)
                    callRpc("otp_hub_otp_inbox_save", JSONObject().put("p_token", token).put("p_encrypted_payload", encrypted))
                    runOnUiThread { message("Encrypted OTP inbox synced. Open the website and unlock it with the same passphrase.") }
                }
            }
            sectionLabel("NEED HELP?")
            button("How to use OTP Hub") { showManual() }
            button("Sign out") {
                async { callRpc("otp_hub_logout", JSONObject().put("p_token", token)); prefs.edit().clear().apply(); token = ""; runOnUiThread { showAuth() } }
            }
            root.addView(TextView(this).apply { text = "Your account password encrypts the OTP snapshot before it is uploaded. Sync is manual. The website uses the same password to unlock it."; textSize = 13f; setTextColor(0xFF686477.toInt()); setPadding(0, 20, 0, 0) })
        }
    }


    private fun showManual() {
        runOnUiThread {
            baseLayout("Quick start guide", "Follow these three steps to see your phone’s detected OTPs on the website.")
            val sections = listOf(
                "1. Sign in" to "Create an OTP Hub account in the app or on the website. Use the same username and account password on both. Keep your password private.",
                "2. Enable notification access" to "Tap Enable notification access on the home screen. Android opens Notification Access settings. Find OTP Hub and enable it only if you are comfortable allowing it to read notification previews. Return to the app.",
                "3. Detect and sync OTPs" to "When a new verification notification arrives, tap View detected OTPs. Enter your OTP Hub account password and tap Sync OTPs to website. Open the website, sign in to the same account, and unlock the inbox with the same password. Sync is manual; it does not happen automatically.",
                "Important privacy notes" to "Notification Access is a broad Android permission. OTP Hub keeps numeric code candidates rather than full notification text. Do not share OTPs or your account password. You can disable Notification Access any time in Android Settings. This is an early testing build, not a security-audited product."
            )
            for ((heading, body) in sections) {
                root.addView(TextView(this).apply { text = heading; textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF29213F.toInt()); setPadding(0, dp(16), 0, dp(6)) })
                root.addView(TextView(this).apply { text = body; textSize = 14f; setTextColor(0xFF555266.toInt()); setLineSpacing(dp(3).toFloat(), 1.08f); setPadding(0, 0, 0, dp(10)) })
            }
            button("Back to OTP Hub") { showHome() }
        }
    }

    private fun showDetectedOtps() {
        runOnUiThread {
            val prefs = getSharedPreferences("otp_hub_notifications", MODE_PRIVATE)
            val detected = try { JSONArray(prefs.getString("detected", "[]")) } catch (_: Exception) { JSONArray() }
            baseLayout("Detected OTPs", "Only numeric code candidates are kept locally on this phone. Full notification text is not stored.")
            if (detected.length() == 0) {
                root.addView(TextView(this).apply {
                    text = "No OTP notifications detected yet. Enable notification access, then wait for a new verification notification."
                    textSize = 15f
                    setTextColor(0xFF656174.toInt())
                    setPadding(0, 18, 0, 18)
                })
            }
            for (i in 0 until detected.length()) {
                val item = detected.optJSONObject(i) ?: continue
                val code = item.optString("code")
                val time = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.optLong("receivedAt")))
                root.addView(TextView(this).apply {
                    text = "$code\n$time"
                    textSize = 24f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(0xFF27213B.toInt())
                    setPadding(18, 18, 18, 18)
                    background = rounded(0xFFFFFFFF.toInt(), 0xFFE8E4F0.toInt(), dp(16))\n                    elevation = dp(1).toFloat()
                })
                button("Copy code") {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OTP", code))
                    message("Code copied.")
                }
            }
            button("Clear detected codes") {
                prefs.edit().remove("detected").apply()
                showDetectedOtps()
            }
            button("Back") { showHome() }
        }
    }

    private fun encryptOtpSnapshot(data: JSONArray, passphrase: String): JSONObject {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 310000, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(raw, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(data.toString().toByteArray())
        return JSONObject().put("salt", Base64.encodeToString(salt, Base64.NO_WRAP)).put("iv", Base64.encodeToString(iv, Base64.NO_WRAP)).put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
    }

}
