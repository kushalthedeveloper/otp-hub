"use client";

import { useState } from "react";
import Link from "next/link";
import { rpc, saveAccount, type AccountResult } from "../lib/otpHub";

type Mode = "login" | "register" | "recover";

export default function AccountPage() {
  const [mode, setMode] = useState<Mode>("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [recovery, setRecovery] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [recoveryCode, setRecoveryCode] = useState("");

  async function submit(event: React.FormEvent) {
    event.preventDefault(); setError(""); setNotice(""); setRecoveryCode(""); setBusy(true);
    try {
      if (mode === "register") {
        const result = await rpc<AccountResult>("otp_hub_register", { p_username: username, p_password: password });
        saveAccount(result); setRecoveryCode(result.recovery_code || "");
        setNotice("Account created. Save this one-time recovery code before leaving this page.");
      } else if (mode === "login") {
        const result = await rpc<AccountResult>("otp_hub_login", { p_username: username, p_password: password });
        saveAccount(result); setNotice("Signed in successfully. Your account is ready.");
      } else {
        const result = await rpc<Array<{ username: string; recovery_code: string }>>("otp_hub_recover", { p_username: username, p_recovery_code: recovery, p_new_password: newPassword });
        const item = Array.isArray(result) ? result[0] : undefined;
        setRecoveryCode(item?.recovery_code || "");
        setNotice("Password updated. Save your new recovery code, then sign in again.");
      }
    } catch (e) { setError(e instanceof Error ? e.message : "Something went wrong."); }
    finally { setBusy(false); }
  }

  return (
    <main style={{ minHeight: "100vh", background: "#f7f6fb", padding: "42px 20px", color: "#211d2d", fontFamily: "Arial, sans-serif" }}>
      <div style={{ maxWidth: 480, margin: "0 auto" }}>
        <Link href="/" style={{ color: "#6849c8", textDecoration: "none", fontWeight: 700 }}>← OTP Hub</Link>
        <section style={{ background: "white", borderRadius: 20, padding: 30, marginTop: 34, boxShadow: "0 12px 42px rgba(30,20,60,.08)" }}>
          <div style={{ color: "#7557d3", fontWeight: 700, letterSpacing: 2, fontSize: 11 }}>YOUR OTP HUB ACCOUNT</div>
          <h1 style={{ fontSize: 32, margin: "12px 0" }}>{mode === "login" ? "Welcome back" : mode === "register" ? "Create your account" : "Recover your account"}</h1>
          <p style={{ color: "#686477", lineHeight: 1.6 }}>Use one username and password on the OTP Hub website and Android app. No email address or phone number is required.</p>
          <div style={{ display: "flex", gap: 8, margin: "22px 0" }}>
            {(["login", "register", "recover"] as Mode[]).map((item) => <button key={item} onClick={() => { setMode(item); setError(""); setNotice(""); setRecoveryCode(""); }} style={{ flex: 1, padding: "10px 4px", borderRadius: 9, border: mode === item ? "1px solid #6849c8" : "1px solid #e5e1ee", background: mode === item ? "#f1edff" : "white", color: "#342c49", fontWeight: 600, textTransform: "capitalize" }}>{item}</button>)}
          </div>
          <form onSubmit={submit}>
            <label style={{ display: "block", fontSize: 13, fontWeight: 700, marginBottom: 6 }}>Username</label>
            <input required minLength={3} maxLength={24} pattern="[A-Za-z0-9_]+" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="your_username" autoComplete="username" style={inputStyle} />
            {mode !== "recover" ? <>
              <label style={labelStyle}>Password</label>
              <input required minLength={12} maxLength={128} type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="At least 12 characters" autoComplete={mode === "login" ? "current-password" : "new-password"} style={inputStyle} />
            </> : <>
              <label style={labelStyle}>Recovery code</label>
              <input required value={recovery} onChange={(e) => setRecovery(e.target.value)} placeholder="Enter your saved recovery code" style={inputStyle} />
              <label style={labelStyle}>New password</label>
              <input required minLength={12} maxLength={128} type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="At least 12 characters" autoComplete="new-password" style={inputStyle} />
            </>}
            {error && <p style={{ color: "#b3261e", fontSize: 13 }}>{error}</p>}
            {notice && <p style={{ color: "#26734d", fontSize: 13, lineHeight: 1.5 }}>{notice}</p>}
            {recoveryCode && <div style={{ background: "#f4f0ff", padding: 16, borderRadius: 10, margin: "14px 0" }}><strong>Recovery code — save it now</strong><p style={{ fontSize: 20, fontWeight: 700, letterSpacing: 1 }}>{recoveryCode}</p><small>This code is shown once. Keep it private and offline.</small></div>}
            <button disabled={busy} type="submit" style={{ width: "100%", border: 0, borderRadius: 10, padding: 15, background: "#6849c8", color: "white", fontWeight: 700, marginTop: 16, opacity: busy ? .6 : 1 }}>{busy ? "Please wait…" : mode === "login" ? "Sign in" : mode === "register" ? "Create account" : "Reset password"}</button>
          </form>
          <p style={{ color: "#777287", fontSize: 12, lineHeight: 1.5, marginTop: 18 }}>Your account password and vault passphrase are separate. Never share either one or your recovery code.</p>
        </section>
      </div>
    </main>
  );
}

const inputStyle: React.CSSProperties = { width: "100%", boxSizing: "border-box", padding: 14, borderRadius: 9, border: "1px solid #ded9e9", marginBottom: 16, fontSize: 15, background: "white", color: "#211d2d" };
const labelStyle: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 700, marginBottom: 6 };
