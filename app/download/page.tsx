export default function DownloadPage() {
  return (
    <main style={{ minHeight: "100vh", background: "#090b12", color: "#f2f0fa", padding: "48px 22px", fontFamily: "Arial, sans-serif" }}>
      <div style={{ maxWidth: 780, margin: "0 auto" }}>
        <a href="/" style={{ color: "#b6a3ff", textDecoration: "none", fontWeight: 700 }}>← OTP Hub</a>
        <p style={{ marginTop: 48, color: "#aa91ff", letterSpacing: 2, fontSize: 12, fontWeight: 700 }}>ANDROID COMPANION</p>
        <h1 style={{ fontSize: 42, lineHeight: 1.1, margin: "12px 0" }}>OTP Hub for Android</h1>
        <p style={{ color: "#a8a8bb", lineHeight: 1.7, fontSize: 16 }}>Use the same OTP Hub account on your phone and website. The Android companion detects genuine verification codes (filtering out DLT sender shortcodes like 57273200 and 5253200) and syncs an encrypted inbox to your private dashboard.</p>
        <section style={{ background: "#151a29", border: "1px solid #34304e", borderRadius: 18, padding: 26, marginTop: 28 }}>
          <h2 style={{ marginTop: 0 }}>Latest Android APK Build</h2>
          <p style={{ color: "#a8a8bb", lineHeight: 1.7 }}>Includes accurate OTP extraction for Indian DLT SMS headers, WhatsApp/notification parsing, and encrypted background sync.</p>
          <a href="https://github.com/kushalthedeveloper/otp-hub/releases" style={{ display: "inline-block", padding: "14px 22px", borderRadius: 10, background: "linear-gradient(135deg,#9476f0,#7053d2)", color: "white", fontWeight: 700, textDecoration: "none" }}>Download Latest APK from GitHub Releases ↓</a>
          <p style={{ fontSize: 12, color: "#9398ad", marginBottom: 0 }}>Select the newest <b>OTP Hub Android build</b> pre-release at the top of the Releases page and download <code>app-debug.apk</code>.</p>
          <p style={{ fontSize: 12, color: "#9398ad" }}>Requires Android 8.0 or newer. <a href="https://github.com/kushalthedeveloper/otp-hub/actions/workflows/android.yml" style={{ color: "#b6a3ff" }}>View Android CI build status</a>.</p>
        </section>
        <section style={{ marginTop: 28, lineHeight: 1.8, color: "#b8bac9" }}>
          <h2 style={{ color: "#f2f0fa" }}>What this build supports</h2>
          <ul>
            <li>Username/password account registration and sign-in without email or phone number.</li>
            <li>Accurate OTP extraction that separates notification titles from message bodies and strips DLT sender shortcodes (<code>AD-57273200</code>, <code>5253200</code>, etc.).</li>
            <li>Automatic purge of legacy shortcode false positives from local phone history when new OTPs arrive.</li>
            <li>Encrypted notification and remote SMS sync to the website using AES-256-GCM.</li>
          </ul>
          <h2 style={{ color: "#f2f0fa" }}>Connect the phone to the website</h2>
          <ol>
            <li>Download and install the latest <code>app-debug.apk</code> from GitHub Releases, then sign in with your OTP Hub account.</li>
            <li>Tap <b>Enable notification access</b> and enable OTP Hub in Android Notification Access settings.</li>
            <li>Enter your OTP Hub account password in the sync field and turn on <b>Automatic encrypted sync</b> (and optionally <b>Allow remote SMS scans from website</b>).</li>
            <li>Open the website dashboard to view your real-time OTPs automatically.</li>
          </ol>
          <p><a href="https://github.com/kushalthedeveloper/otp-hub/releases" style={{ color: "#b6a3ff" }}>View all builds and release notes</a></p>
        </section>
      </div>
    </main>
  );
}
