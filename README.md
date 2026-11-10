# OTP Hub

OTP Hub is a personal OTP notification inbox with username/password accounts and an Android companion app.

**Live website:** https://otp-hub-eight.vercel.app

## Account system

- Username + password registration and login; no email or phone number is required.
- Usernames are 3–24 characters: lowercase letters, numbers, and underscores.
- Passwords must be 12–128 characters.
- Registration returns a one-time recovery code. Save it privately and offline. A successful recovery rotates the recovery code and displays the replacement.
- Account sessions are validated by Supabase-backed RPC functions.

## Incoming OTP inbox and remote SMS scan

- Android notification scanning is opt-in and requires the user to enable Android's Notification Access permission.
- The notification listener extracts likely numeric OTP candidates from notification title, expanded text, and Android MessagingStyle chat-message payloads (including WhatsApp) that Android actually delivers to it. Hidden previews and notifications that are not posted by the source app cannot be read.
- Optional remote SMS scanning is separately opt-in and requires the Android `READ_SMS` runtime permission. The phone examines up to the 500 newest SMS inbox records in memory and returns no more than the five newest OTP matches, even when the matches are older than the five newest messages.
- SMS message bodies are not stored or uploaded. Only the candidate code, sender label, receive time, and channel are included in the result.
- The website's **Scan SMS on my phone** button queues a command for the paired Android device. The Android app must be signed in, online, and have **Allow remote SMS scans from website** enabled.
- Remote scan results are encrypted on-device using the existing account passphrase (PBKDF2-HMAC-SHA256 and AES-256-GCM) before upload. The website decrypts them in the browser after the inbox is unlocked.
- Android WorkManager checks remote commands periodically, and the app also polls every 20 seconds while it is open to make a website-requested scan start sooner. Android may defer background work because of battery saver, network state, force-stop, or vendor background restrictions; this is not guaranteed to be instant.
- The app's existing Automatic encrypted sync remains separate and optional. The website refreshes encrypted inbox data while the tab is open and visible.
- WhatsApp private chats are not scanned. OTPs may be detected only from WhatsApp notifications that Android exposes to the enabled notification listener; there is no direct WhatsApp database access.

**Privacy and security:** SMS and notification access are sensitive. Enable each permission only if you understand it, keep OTP Hub account credentials private, and disable Remote SMS Scan when you no longer need it. The server stores remote scan results encrypted, not as plaintext SMS. This is an early testing build and has not had an independent security audit.

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
