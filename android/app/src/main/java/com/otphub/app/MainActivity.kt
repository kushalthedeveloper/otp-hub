package com.otphub.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import android.net.Uri
import com.google.zxing.integration.android.IntentIntegrator
import java.security.SecureRandom

class MainActivity : Activity() {
    private val endpoint = "https://xllstbnkvtustctpkbap.supabase.co"
    private val publicKey = "sb_publishable_Ot6DZiGzEaebJMDSjiC23Q_1BwRqCYP"
    private val prefs by lazy { getSharedPreferences("otp_hub_session", MODE_PRIVATE) }
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private var token = ""
    private var username = ""
    private var vaultPass = ""
    private var entries = JSONArray()

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
            setPadding(28, 36, 28, 24)
            setBackgroundColor(0xFFF6F5FA.toInt())
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        root.addView(TextView(this).apply { text = "◈  OTP HUB"; textSize = 16f; setTextColor(0xFF6547C8.toInt()); setTypeface(null, Typeface.BOLD) })
        root.addView(TextView(this).apply { text = title; textSize = 30f; setTextColor(0xFF171522.toInt()); setTypeface(null, Typeface.BOLD); setPadding(0, 34, 0, 8) })
        root.addView(TextView(this).apply { text = subtitle; textSize = 14f; setTextColor(0xFF666477.toInt()); setPadding(0, 0, 0, 24) })
    }

    private fun field(hint: String, password: Boolean = false): EditText {
        val edit = EditText(this).apply {
            this.hint = hint
            textSize = 16f
            setSingleLine(true)
            setPadding(18, 12, 18, 12)
            setBackgroundColor(0xFFFFFFFF.toInt())
            if (password) inputType = 129
        }
        root.addView(edit, LinearLayout.LayoutParams(-1, 58).apply { bottomMargin = 12 })
        return edit
    }

    private fun button(label: String, action: () -> Unit) {
        root.addView(Button(this).apply { text = label; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1, 54).apply { bottomMargin = 10 })
    }

    private fun message(value: String) {
        root.addView(TextView(this).apply { text = value; textSize = 14f; setTextColor(0xFFB3261E.toInt()); setPadding(0, 12, 0, 12) })
    }

    private fun showAuth() {
        runOnUiThread {
            baseLayout("Your authenticator. One account.", "Sign in with your OTP Hub username. No email or phone number is needed.")
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
            baseLayout("Welcome, $username", "This Android app is connected to your OTP Hub account.")
            val pass = field("Vault passphrase (12+ characters)", true)
            button("Unlock and sync encrypted vault") {
                if (pass.text.length < 12) { message("Use at least 12 characters for your vault passphrase."); return@button }
                vaultPass = pass.text.toString()
                async {
                    val remoteResponse = callRpc("otp_hub_vault_get", JSONObject().put("p_token", token))
                    val remote = if (remoteResponse.has("salt")) remoteResponse else null
                    val local = prefs.getString("vault", null)
                    val record = remote ?: if (local != null) JSONObject(local) else null
                    if (record != null) entries = decryptVault(record, vaultPass)
                    else entries = JSONArray()
                    runOnUiThread { showVault() }
                }
            }
            button("Sign out") {
                async { callRpc("otp_hub_logout", JSONObject().put("p_token", token)); prefs.edit().clear().apply(); token = ""; runOnUiThread { showAuth() } }
            }
            root.addView(TextView(this).apply { text = "Your TOTP secrets are encrypted before cloud backup. The account server stores only the encrypted vault."; textSize = 13f; setTextColor(0xFF686477.toInt()); setPadding(0, 20, 0, 0) })
        }
    }

    private fun showVault() {
        runOnUiThread {
            baseLayout("Authenticator vault", "Signed in as @$username · encrypted account backup")
            if (entries.length() == 0) root.addView(TextView(this).apply { text = "No authenticator accounts yet. Add a service setup key below."; textSize = 14f; setTextColor(0xFF656174.toInt()); setPadding(0, 12, 0, 18) })
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                root.addView(TextView(this).apply { text = item.optString("issuer") + "  •  " + item.optString("account") + "\n" + totp(item.optString("secret")); textSize = 20f; setTextColor(0xFF27213B.toInt()); setPadding(16, 18, 16, 18); setBackgroundColor(0xFFFFFFFF.toInt()) })
            }
            button("Scan QR code from another phone") {
                IntentIntegrator(this@MainActivity).apply {
                    setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                    setPrompt("Scan the authenticator setup QR on the other phone")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                }.initiateScan()
            }
            val issuer = field("Service name")
            val account = field("Account label")
            val secret = field("Base32 setup key")
            button("Add authenticator account") {
                try {
                    val normalized = secret.text.toString().replace(" ", "").uppercase()
                    decodeBase32(normalized)
                    entries.put(JSONObject().put("issuer", issuer.text.toString()).put("account", account.text.toString()).put("secret", normalized))
                    async {
                        val encrypted = encryptVault(entries, vaultPass)
                        prefs.edit().putString("vault", encrypted.toString()).apply()
                        callRpc("otp_hub_vault_save", JSONObject().put("p_token", token).put("p_encrypted_vault", encrypted))
                        runOnUiThread { showVault() }
                    }
                } catch (e: Exception) { message(e.message ?: "Invalid setup key") }
            }
            button("Sync encrypted backup now") {
                async {
                    val encrypted = encryptVault(entries, vaultPass)
                    prefs.edit().putString("vault", encrypted.toString()).apply()
                    callRpc("otp_hub_vault_save", JSONObject().put("p_token", token).put("p_encrypted_vault", encrypted))
                    runOnUiThread { message("Encrypted vault backup synced to your account.") }
                }
            }
            button("Lock vault") { vaultPass = ""; showHome() }
        }
    }


    @Deprecated("Uses ZXing's activity result flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data) ?: return
        val raw = scan.contents ?: return
        try {
            val uri = Uri.parse(raw)
            if (uri.scheme != "otpauth" || uri.host != "totp") throw Exception("This QR is not a TOTP authenticator setup code.")
            val secret = uri.getQueryParameter("secret")?.replace(" ", "")?.uppercase() ?: throw Exception("QR code has no setup secret.")
            decodeBase32(secret)
            val label = Uri.decode(uri.path.orEmpty().removePrefix("/"))
            val issuer = uri.getQueryParameter("issuer") ?: label.substringBefore(":").ifBlank { "Authenticator" }
            val account = label.substringAfter(":", label).ifBlank { username }
            entries.put(JSONObject().put("issuer", issuer).put("account", account).put("secret", secret))
            async {
                val encrypted = encryptVault(entries, vaultPass)
                prefs.edit().putString("vault", encrypted.toString()).apply()
                callRpc("otp_hub_vault_save", JSONObject().put("p_token", token).put("p_encrypted_vault", encrypted))
                runOnUiThread { showVault() }
            }
        } catch (e: Exception) {
            message(e.message ?: "Could not read authenticator QR code.")
        }
    }

    private fun decodeBase32(value: String): ByteArray {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = ""
        value.uppercase().filter { it in chars }.forEach { bits += chars.indexOf(it).toString(2).padStart(5, '0') }
        val out = ArrayList<Byte>()
        var i = 0
        while (i + 8 <= bits.length) { out.add(bits.substring(i, i + 8).toInt(2).toByte()); i += 8 }
        if (out.isEmpty() || value.uppercase().any { it != '=' && it !in chars }) throw Exception("Enter a valid Base32 setup key.")
        return out.toByteArray()
    }

    private fun totp(secret: String): String {
        val key = decodeBase32(secret)
        val counter = System.currentTimeMillis() / 1000 / 30
        val data = ByteArray(8)
        for (i in 7 downTo 0) data[i] = (counter ushr ((7 - i) * 8)).toByte()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash.last().toInt() and 15
        val binary = ((hash[offset].toInt() and 127) shl 24) or ((hash[offset+1].toInt() and 255) shl 16) or ((hash[offset+2].toInt() and 255) shl 8) or (hash[offset+3].toInt() and 255)
        return (binary % 1000000).toString().padStart(6, '0')
    }

    private fun encryptVault(data: JSONArray, passphrase: String): JSONObject {
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

    private fun decryptVault(record: JSONObject, passphrase: String): JSONArray {
        val salt = Base64.decode(record.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(record.getString("iv"), Base64.NO_WRAP)
        val encrypted = Base64.decode(record.getString("data"), Base64.NO_WRAP)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 310000, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(raw, "AES"), GCMParameterSpec(128, iv))
        return JSONArray(String(cipher.doFinal(encrypted)))
    }
}
