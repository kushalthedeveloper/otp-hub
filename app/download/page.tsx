export default function DownloadPage() {
  return (
    <main style={{ minHeight: "100vh", background: "#090b12", color: "#f2f0fa", padding: "48px 22px", fontFamily: "Arial, sans-serif" }}>
      <div style={{ maxWidth: 780, margin: "0 auto" }}>
        <a href="/" style={{ color: "#b6a3ff", textDecoration: "none", fontWeight: 700 }}>← OTP Hub</a>
        <p style={{ marginTop: 48, color: "#aa91ff", letterSpacing: 2, fontSize: 12, fontWeight: 700 }}>ANDROID COMPANION</p>
        <h1 style={{ fontSize: 42, lineHeight: 1.1, margin: "12px 0" }}>OTP Hub for Android</h1>
        <p style={{ color: "#a8a8bb", lineHeight: 1.7, fontSize: 16 }}>Use the same OTP Hub account on your phone and website. The Android companion can detect numeric OTP candidates exposed in notification previews and sync an encrypted inbox to your private dashboard.</p>
        <section style={{ background: "#151a29", border: "1px solid #34304e", borderRadius: 18, padding: 26, marginTop: 28 }}>
          <h2 style={{ marginTop: 0 }}>Latest Android testing build · v1.0.34</h2>
          <p style={{ color: "#a8a8bb", lineHeight: 1.7 }}>Build 58 · debug/testing APK, not a production-signed release. Android may display an installation warning.</p>
          <a href="https://github.com/kushalthedeveloper/otp-hub/releases/tag/android-build-58" style={{ display: "inline-block", padding: "14px 22px", borderRadius: 10, background: "linear-gradient(135deg,#9476f0,#7053d2)", color: "white", fontWeight: 700, textDecoration: "none" }}>Download APK · v1.0.34 ↓</a>
          <p style={{ fontSize: 12, color: "#9398ad", marginBottom: 0 }}>Direct APK download from the verified Android build release.</p>
          <p style={{ fontSize: 12, color: "#9398ad" }}>Requires Android 8.0 or newer. <a href="https://github.com/kushalthedeveloper/otp-hub/actions/runs/42471608674" style={{ color: "#b6a3ff" }}>Build 58 release details</a>.</p>
        </section>
        <section style={{ marginTop: 28, lineHeight: 1.8, color: "#b8bac9" }}>
          <h2 style={{ color: "#f2f0fa" }}>What this build supports</h2>
          <ul>
            <li>Username/password account registration and sign-in without email or phone number.</li>
            <li>OTP notification history with newest entries first; locally stored notification candidates are retained by the app.</li>
            <li>Opt-in notification listener that extracts numeric OTP candidates from notification title/preview text.</li>
            <li>Optional encrypted notification sync to the website; Android may delay background work depending on device and battery settings.</li>
            <li>Manual encrypted sync remains available as a fallback.</li>
          </ul>
          <h2 style={{ color: "#f2f0fa" }}>Connect the phone to the website</h2>
          <ol>
            <li>Install the APK and sign in with your OTP Hub account.</li>
            <li>If notification access is not yet enabled, tap <b>Enable notification access</b> and review Android's broad Notification Access permission. Once enabled, OTP Hub replaces the setup button with an enabled status.</li>
            <li>Set up Automatic encrypted sync using your OTP Hub account password. To use remote SMS scans, separately grant Android's SMS permission and enable <b>Allow remote SMS scans from website</b> in the app.</li>
            <li>Open the website dashboard, enter the same vault passphrase, and unlock the encrypted inbox.</li>
          </ol>
          <h2 style={{ color: "#f2f0fa" }}>Privacy and limitations</h2>
          <ul>
            <li>The server stores the inbox encrypted; the vault passphrase is not uploaded.</li>
            <li>Remote SMS scans inspect only the five newest SMS inbox entries; no older SMS messages are queried. The website displays each request as queued, scanning, completed, failed, or expired, including completed scans that find no OTP matches.</li>
            <li>Android can only detect codes actually exposed in notifications. Hidden previews, suppressed notifications, and apps that do not show codes in notifications may not work.</li>
            <li>SMS scanning is limited to the five newest SMS inbox entries after explicit SMS permission and remote-scan opt-in. The app does not read private WhatsApp chats, email inboxes, or photos.</li>
            <li>OTP codes can authorize access to accounts. Keep them private and enable notification access only if you understand the permission.</li>
            <li>The APK is debug-signed and real-device background behavior still depends on Android/OEM battery policies; it has not been verified on every device.</li>
          </ul>
          <p><a href="https://github.com/kushalthedeveloper/otp-hub/releases" style={{ color: "#b6a3ff" }}>View official builds and release notes</a></p>
        </section>
      </div>
    </main>
  );
}
