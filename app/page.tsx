"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { clearAccount, getSavedAccount, rpc, validateAccount } from "./lib/otpHub";

type ViewState = "checking" | "signed-in" | "error";

export default function Home() {
  const router = useRouter();
  const [state, setState] = useState<ViewState>("checking");
  const [username, setUsername] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;
    async function checkSession() {
      const saved = getSavedAccount();
      if (!saved.token) {
        router.replace("/account");
        return;
      }
      try {
        const account = await validateAccount(saved.token);
        if (!active) return;
        if (!account?.username) {
          clearAccount();
          router.replace("/account");
          return;
        }
        setUsername(account.username);
        setState("signed-in");
      } catch {
        if (!active) return;
        clearAccount();
        router.replace("/account");
      }
    }
    void checkSession();
    return () => { active = false; };
  }, [router]);

  async function signOut() {
    setBusy(true);
    setMessage("");
    const { token } = getSavedAccount();
    try {
      if (token) await rpc("otp_hub_logout", { p_token: token });
    } catch {
      // Local credentials are still cleared if the network is unavailable.
    } finally {
      clearAccount();
      router.replace("/account");
    }
  }

  if (state === "checking") {
    return <main style={shell}><section style={panel}><p style={eyebrow}>OTP HUB · SECURE WORKSPACE</p><h1 style={heading}>Checking your account…</h1><p style={muted}>Your session is being verified before the dashboard opens.</p></section></main>;
  }

  if (state === "error") {
    return <main style={shell}><section style={panel}><h1 style={heading}>Sign-in required</h1><Link href="/account" style={link}>Go to account sign in</Link></section></main>;
  }

  return (
    <main style={shell}>
      <section style={{ ...panel, maxWidth: 760 }}>
        <header style={header}>
          <div><p style={eyebrow}>OTP HUB · SECURE WORKSPACE</p><h1 style={heading}>Your dashboard</h1><p style={muted}>You are signed in as <strong style={{ color: "#28223a" }}>{username}</strong>.</p></div>
          <button onClick={signOut} disabled={busy} style={secondaryButton}>{busy ? "Signing out…" : "Sign out"}</button>
        </header>
        {message && <p>{message}</p>}
        <div style={grid}>
          <article style={tile}><span style={tileIcon}>◈</span><h2 style={tileHeading}>Authenticator</h2><p style={muted}>Create and manage time-based codes from setup keys you intentionally add.</p><Link href="/authenticator" style={link}>Open authenticator →</Link></article>
          <article style={tile}><span style={tileIcon}>▣</span><h2 style={tileHeading}>Android app</h2><p style={muted}>Install the companion testing app and sign in with this same account.</p><Link href="/download" style={link}>Get the Android app →</Link></article>
          <article style={tile}><span style={tileIcon}>◎</span><h2 style={tileHeading}>Account security</h2><p style={muted}>Keep your password, recovery code, and separate vault passphrase private.</p><Link href="/account" style={link}>Account options →</Link></article>
        </div>
        <div style={notice}><strong>Privacy note</strong><p style={{ ...muted, margin: "7px 0 0" }}>OTP Hub does not read SMS, WhatsApp, or email inboxes. Only setup keys you intentionally add to your authenticator are used to generate codes.</p></div>
      </section>
    </main>
  );
}

const shell: React.CSSProperties = { minHeight: "100vh", background: "#f7f6fb", padding: "48px 20px", color: "#211d2d", fontFamily: "Arial, sans-serif" };
const panel: React.CSSProperties = { maxWidth: 480, margin: "0 auto", background: "white", borderRadius: 20, padding: 30, boxShadow: "0 12px 42px rgba(30,20,60,.08)" };
const header: React.CSSProperties = { display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 16, flexWrap: "wrap" };
const eyebrow: React.CSSProperties = { color: "#7557d3", fontWeight: 700, letterSpacing: 2, fontSize: 11, margin: "0 0 12px" };
const heading: React.CSSProperties = { fontSize: 32, margin: "0 0 10px" };
const muted: React.CSSProperties = { color: "#686477", lineHeight: 1.6, fontSize: 14 };
const grid: React.CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 14, marginTop: 28 };
const tile: React.CSSProperties = { border: "1px solid #ebe7f2", borderRadius: 14, padding: 18, background: "#fff" };
const tileIcon: React.CSSProperties = { color: "#7557d3", fontSize: 24 };
const tileHeading: React.CSSProperties = { fontSize: 18, margin: "12px 0 6px" };
const link: React.CSSProperties = { color: "#6849c8", fontWeight: 700, textDecoration: "none", fontSize: 14 };
const secondaryButton: React.CSSProperties = { border: "1px solid #ded9e9", borderRadius: 9, padding: "10px 14px", background: "white", color: "#342c49", fontWeight: 700, cursor: "pointer" };
const notice: React.CSSProperties = { marginTop: 24, padding: 16, borderRadius: 12, background: "#f4f0ff", color: "#342c49", fontSize: 14 };
