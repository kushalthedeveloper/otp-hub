# OTP Hub

OTP Hub is a personal OTP notification inbox with username/password accounts and an Android companion app.

**Live website:** https://otp-hub-eight.vercel.app

## Account system

- Username + password registration and login; no email or phone number is required.
- Usernames are 3–24 characters: lowercase letters, numbers, and underscores.
- Passwords must be 12–128 characters.
- Registration returns a one-time recovery code. Save it privately and offline. A successful recovery rotates the recovery code and displays the replacement.
- Account sessions are validated by Supabase-backed RPC functions.

## Incoming OTP notification inbox

- Android notification scanning is opt-in and requires the user to enable Android's Notification Access permission.
- The listener checks notification title and preview text for OTP-related keywords and a 4–8 digit numeric candidate.
- It stores only the candidate code and receive timestamp locally; it does not keep the original notification text or sender.
- The Android app keeps at most 10 recent candidates and removes entries older than 24 hours when new notifications arrive.
- The user manually starts a sync from the Android app. Sync encrypts the candidate list on-device using the OTP Hub account password, PBKDF2-HMAC-SHA256 (310,000 iterations), and AES-256-GCM before upload. While the website inbox is unlocked and the browser tab is visible, it checks for a new encrypted snapshot every 2 seconds and updates the displayed inbox automatically after a phone sync.
- The website decrypts the payload in the browser only after the user enters the same account password. The passphrase and plaintext OTP list are not sent to the server.
- The server stores one encrypted inbox payload per account. The website can refresh, lock, and clear the synced inbox.
- The website filters out entries older than 24 hours when decrypting. The server-side encrypted snapshot is replaced on the next sync or when the user clears it.

**Limitations:** Android can only detect content actually exposed in notifications delivered to the listener. Hidden notification previews, suppressed notifications, or apps that do not show the code in their notification may not be detected. This does not scan SMS history, WhatsApp chats, email inboxes, or photos, and does not bypass app privacy controls.

## Android app

- Android project: `android/`
- Minimum Android version: Android 8.0 (API 26)
- Sign in or register using the same OTP Hub account as the website.
- Detect OTP candidates from notification previews after the user enables Android Notification Access.
- Upload an encrypted snapshot only after the user chooses Sync in the app.

[Download the latest Android testing APK](https://github.com/kushalthedeveloper/otp-hub/releases/latest)

## Security notes

- The server stores password hashes, recovery-code hashes, session-token hashes, and encrypted vault/inbox payloads.
- Direct client table access is revoked and RLS is enabled. Narrowly scoped RPC functions validate the account session token before reading or writing encrypted records.
- Keep your account password and recovery code private. Do not share OTPs; they can authorize access to accounts.
- Enable notification access only when you understand the permission and choose which apps may show notification previews.
- This is an early testing build, not a production security-audited authenticator. APK releases are debug-signed prereleases.
- Public account and recovery RPCs still need abuse-rate limiting and independent security review before production use.

## Web stack

- Next.js App Router and TypeScript
- Supabase Postgres RPC functions
- Vercel production deployment

## Android build

GitHub Actions builds the APK from `.github/workflows/android.yml`. Builds are published as prereleases in GitHub Releases.
