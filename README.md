# OTP Hub

OTP Hub is a privacy-first authenticator workspace with username/password accounts and an Android companion app.

**Live website:** https://otp-hub-eight.vercel.app

## Account system

- Username + password registration and login; no email or phone number is required.
- Usernames are 3–24 characters: lowercase letters, numbers, and underscores.
- Passwords must be 12–128 characters.
- Registration returns a one-time recovery code. Save it privately and offline. A successful recovery rotates the recovery code and displays the replacement.
- Account sessions are validated by Supabase-backed RPC functions.
- The dashboard redirects visitors to sign in unless a valid session exists.

## Web authenticator

- The browser authenticator generates TOTP codes from setup keys the user intentionally enters.
- Its encrypted vault is stored locally in that browser only; it does not currently sync with the Android vault.
- It uses AES-256-GCM encryption with PBKDF2-HMAC-SHA256 key derivation.
- QR scanning is not included; setup keys must be entered manually.

## Android authenticator

- Android project: `android/`
- Minimum Android version: Android 8.0 (API 26)
- Sign in or register using the same OTP Hub account as the website.
- Generate TOTP codes from setup keys that the user intentionally enters.
- Encrypt the vault with a separate passphrase using PBKDF2-HMAC-SHA256 and AES-256-GCM.
- Sync only the encrypted vault payload to the account database.
- Current build accepts manual Base32 setup keys; QR scanning is not included yet.

[Download the current Android testing APK](https://github.com/kushalthedeveloper/otp-hub/releases/download/android-build-6/app-debug.apk)

## Security notes

- The server stores password hashes, recovery-code hashes, session-token hashes, and encrypted Android vault backups.
- TOTP setup keys in the Android vault are encrypted on the client before backup. The vault passphrase is not sent to the server.
- Keep the account recovery code and vault passphrase separate and private.
- The database denies direct table access to client roles; narrowly scoped RPC functions provide the intended account operations.
- This is an early testing build, not a production security-audited authenticator. The APK is a debug-signed prerelease.
- Public account/recovery RPCs still need abuse-rate limiting and independent security review before production use.
- OTP Hub does not read SMS, WhatsApp, or email inboxes, and does not intercept or forward incoming login codes.

## Web stack

- Next.js App Router
- TypeScript
- Supabase Postgres RPC functions
- Vercel production deployment

## Android build

GitHub Actions builds the APK from `.github/workflows/android.yml`. Builds are published as prereleases in GitHub Releases.
