export default function DownloadPage() {
  return (
    <main style={{ minHeight: "100vh", background: "#f7f6fb", color: "#201d2b", padding: "48px 22px", fontFamily: "Arial, sans-serif" }}>
      <div style={{ maxWidth: 760, margin: "0 auto" }}>
        <a href="/" style={{ color: "#6849c8", textDecoration: "none", fontWeight: 700 }}>← OTP Hub</a>
        <p style={{ marginTop: 48, color: "#7557d3", letterSpacing: 2, fontSize: 12, fontWeight: 700 }}>ANDROID AUTHENTICATOR</p>
        <h1 style={{ fontSize: 42, lineHeight: 1.1, margin: "12px 0" }}>OTP Hub for Android</h1>
        <p style={{ color: "#666276", lineHeight: 1.7, fontSize: 17 }}>Sign in with the same OTP Hub username and password used on the website. Your authenticator vault is encrypted on your device before its backup is synced to your account.</p>
        <section style={{ background: "white", borderRadius: 18, padding: 26, marginTop: 28, boxShadow: "0 12px 40px rgba(34,22,70,.07)" }}>
          <h2 style={{ marginTop: 0 }}>Download the latest Android build</h2>
          <p style={{ color: "#666276", lineHeight: 1.6 }}>This is currently a debug/testing APK. Android may show an installation warning because it is not yet signed with a production release certificate.</p>
          <a href="https://github.com/kushalthedeveloper/otp-hub/releases/download/android-build-6/app-debug.apk" style={{ display: "inline-block", padding: "14px 22px", borderRadius: 10, background: "#6849c8", color: "white", fontWeight: 700, textDecoration: "none" }}>Download Android APK ↓</a>
          <p style={{ fontSize: 12, color: "#777287", marginBottom: 0 }}>Requires Android 8.0 or newer. Install only builds published from the official OTP Hub repository.</p>
        </section>
        <section style={{ marginTop: 28, lineHeight: 1.7, color: "#555166" }}>
          <h3 style={{ color: "#201d2b" }}>What this build supports</h3>
          <ul>
            <li>Username and password registration/sign-in without email or phone number.</li>
            <li>Account session validation and sign-out.</li>
            <li>Generate time-based one-time passwords from authenticator setup keys.</li>
            <li>Encrypt the vault with your separate vault passphrase and sync the encrypted backup to your account.</li>
          </ul>
          <h3 style={{ color: "#201d2b" }}>Important security notes</h3>
          <ul>
            <li>Keep your account recovery code and vault passphrase safe. They serve different purposes.</li>
            <li>Use the same vault passphrase on every device that needs to decrypt your synced vault.</li>
            <li>This build accepts manually entered authenticator setup keys; QR scanning and a production-signed APK are not included yet.</li>
            <li>OTP Hub does not read SMS, WhatsApp, or email inboxes.</li>
          </ul>
          <p><a href="https://github.com/kushalthedeveloper/otp-hub/releases" style={{ color: "#6849c8" }}>View all official builds and release notes</a></p>
        </section>
      </div>
    </main>
  );
}
