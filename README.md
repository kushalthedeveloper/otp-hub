# OTP Hub

A privacy-first personal authentication workspace.

## Current scope

This repository starts with a responsive web dashboard scaffold. The initial version uses clearly labeled sample data only. It does **not** read SMS, WhatsApp, or email messages, forward login codes, or collect third-party credentials.

## Security direction

- Never request or store email, WhatsApp, or other account passwords.
- Do not intercept or remotely relay one-time login codes from messaging apps.
- Future integrations must use official OAuth/API permissions and explicit user consent.
- Prefer a local authenticator design for user-controlled TOTP secrets, with encryption and recovery guidance.
- Keep secrets out of logs, analytics, URLs, and public repositories.
- Add server-side authorization and row-level security before connecting any database.

## Stack

- Next.js App Router
- TypeScript
- CSS modules / global CSS (no UI framework required)

## Run locally

```bash
npm install
npm run dev
```

Open http://localhost:3000.

## Status

Early UI scaffold. Authentication, encrypted vault storage, official provider integrations, and production deployment are not implemented yet.
