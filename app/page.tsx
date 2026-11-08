"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { clearAccount, getSavedAccount, rpc, validateAccount } from "./lib/otpHub";

type OtpItem = { code: string; receivedAt: number; source?: string };

function fromBase64(value: string) {
  const raw = atob(value);
  return Uint8Array.from(raw, (char) => char.charCodeAt(0));
}
function toBase64(value: ArrayBuffer) {
  const bytes = new Uint8Array(value);
  let raw = "";
  bytes.forEach((byte) => { raw += String.fromCharCode(byte); });
  return btoa(raw);
}
async function encryptInbox(items: OtpItem[], passphrase: string) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const material = await crypto.subtle.importKey("raw", new TextEncoder().encode(passphrase), "PBKDF2", false, ["deriveKey"]);
  const key = await crypto.subtle.deriveKey({ name: "PBKDF2", salt, iterations: 310000, hash: "SHA-256" }, material, { name: "AES-GCM", length: 256 }, false, ["encrypt"]);
  const encrypted = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, new TextEncoder().encode(JSON.stringify(items)));
  return { salt: toBase64(salt.buffer), iv: toBase64(iv.buffer), data: toBase64(encrypted) };
}
async function decryptInbox(payload: Record<string, string>, passphrase: string): Promise<OtpItem[]> {
  const salt = fromBase64(payload.salt);
  const iv = fromBase64(payload.iv);
  const encrypted = fromBase64(payload.data);
  const material = await crypto.subtle.importKey("raw", new TextEncoder().encode(passphrase), "PBKDF2", false, ["deriveKey"]);
  const key = await crypto.subtle.deriveKey({ name: "PBKDF2", salt, iterations: 310000, hash: "SHA-256" }, material, { name: "AES-GCM", length: 256 }, false, ["decrypt"]);
  const clear = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, key, encrypted);
  const parsed = JSON.parse(new TextDecoder().decode(clear));
  if (!Array.isArray(parsed)) throw new Error("Inbox data is not valid.");
  const cutoff = Date.now() - 24 * 60 * 60 * 1000;
  return parsed.filter((item) => typeof item?.code === "string" && /^\d{4,8}$/.test(item.code) && Number(item.receivedAt) >= cutoff).slice(0, 10);
}

export default function Home() {
  const router = useRouter();
  const [token, setToken] = useState("");
  const [username, setUsername] = useState("");
  const [checking, setChecking] = useState(true);
  const [passphrase, setPassphrase] = useState("");
  const [items, setItems] = useState<OtpItem[]>([]);
  const [unlocked, setUnlocked] = useState(false);
  const [revealed, setRevealed] = useState<Record<number, boolean>>({});
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const lastPayloadRef = useRef<string>("");

  useEffect(() => {
    let active = true;
    async function check() {
      const saved = getSavedAccount();
      if (!saved.token) { router.replace("/account"); return; }
      try {
        const account = await validateAccount(saved.token);
        if (!active) return;
        if (!account?.username) { clearAccount(); router.replace("/account"); return; }
        const inboxKey = sessionStorage.getItem("otp-hub-inbox-key") || "";
        if (inboxKey.length < 12) { router.replace("/account"); return; }
        setToken(saved.token);
        setUsername(account.username);
        setPassphrase(inboxKey);
      } catch {
        if (active) { clearAccount(); router.replace("/account"); }
      } finally { if (active) setChecking(false); }
    }
    void check();
    return () => { active = false; };
  }, [router]);

  const recentItems = useMemo(() => items.filter((item) => Date.now() - item.receivedAt <= 24 * 60 * 60 * 1000), [items]);
  useEffect(() => {
    if (!unlocked || !token || passphrase.length < 12) return;
    let stopped = false;
    let inFlight = false;
    const poll = async () => {
      if (stopped || inFlight || document.visibilityState !== "visible") return;
      inFlight = true;
      try {
        const payload = await rpc<Record<string, string> | null>("otp_hub_otp_inbox_get", { p_token: token });
        const signature = JSON.stringify(payload ?? null);
        if (!stopped && signature !== lastPayloadRef.current) {
          const nextItems = payload ? await decryptInbox(payload, passphrase) : [];
          if (!stopped) {
            lastPayloadRef.current = signature;
            setItems(nextItems);
            setRevealed({});
            setNotice("New phone sync received. Unified inbox updated automatically.");
            setError("");
          }
        }
      } catch {
        // Keep the last decrypted inbox visible; the next poll retries.
      } finally {
        inFlight = false;
      }
    };
    void poll();
    const timer = window.setInterval(() => void poll(), 2000);
    return () => { stopped = true; window.clearInterval(timer); };
  }, [unlocked, token, passphrase]);


  async function unlockInbox() {
    setBusy(true); setError(""); setNotice("");
    try {
      if (passphrase.length < 12) throw new Error("Enter your OTP Hub account password (at least 12 characters).");
      const payload = await rpc<Record<string, string> | null>("otp_hub_otp_inbox_get", { p_token: token });
      if (!payload) { lastPayloadRef.current = "null"; setItems([]); setUnlocked(true); setNotice("Inbox unlocked. No phone sync has been uploaded yet."); return; }
      const decrypted = await decryptInbox(payload, passphrase);
      setItems(decrypted);
      const normalizedPayload = await encryptInbox(decrypted, passphrase);
      await rpc("otp_hub_otp_inbox_save", { p_token: token, p_encrypted_payload: normalizedPayload });
      lastPayloadRef.current = JSON.stringify(normalizedPayload);
      setUnlocked(true);
      setRevealed({});
      setNotice("Encrypted inbox unlocked on this device. The server stores only encrypted data.");
    } catch (e) { setError(e instanceof Error ? e.message : "Could not unlock the inbox. Check your passphrase."); }
    finally { setBusy(false); }
  }

  useEffect(() => {
    if (!checking && token && passphrase.length >= 12 && !unlocked) void unlockInbox();
  }, [checking, token, passphrase, unlocked]);

  async function refreshInbox() {
    setBusy(true); setError(""); setNotice("");
    try {
      const payload = await rpc<Record<string, string> | null>("otp_hub_otp_inbox_get", { p_token: token });
      const decrypted = payload ? await decryptInbox(payload, passphrase) : [];
      setItems(decrypted);
      if (payload) {
        const normalizedPayload = await encryptInbox(decrypted, passphrase);
        await rpc("otp_hub_otp_inbox_save", { p_token: token, p_encrypted_payload: normalizedPayload });
        lastPayloadRef.current = JSON.stringify(normalizedPayload);
      } else lastPayloadRef.current = "null";
      setRevealed({});
      setNotice("Inbox refreshed from encrypted cloud storage.");
    } catch (e) { setError(e instanceof Error ? e.message : "Could not refresh the inbox."); }
    finally { setBusy(false); }
  }

  async function clearInbox() {
    if (!window.confirm("Clear all synced OTP entries from your account?")) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const encrypted = await encryptInbox([], passphrase);
      await rpc("otp_hub_otp_inbox_save", { p_token: token, p_encrypted_payload: encrypted });
      lastPayloadRef.current = JSON.stringify(encrypted);
      setItems([]); setRevealed({});
      setNotice("Synced OTP inbox cleared.");
    } catch (e) { setError(e instanceof Error ? e.message : "Could not clear the inbox."); }
    finally { setBusy(false); }
  }

  async function signOut() {
    setBusy(true);
    try { await rpc("otp_hub_logout", { p_token: token }); } catch {}
    clearAccount();
    setPassphrase(""); setItems([]); lastPayloadRef.current = "";
    router.replace("/account");
  }

  if (checking) return <main style={shell}><section style={panel}><p style={eyebrow}>OTP HUB / PRIVATE INBOX</p><h1 style={heading}>Checking your account…</h1></section></main>;

  return (
    <main style={shell}>
      <section style={panel}>
        <header style={header}>
          <div><p style={eyebrow}>OTP HUB / PRIVATE INBOX</p><h1 style={heading}>Your OTP dashboard</h1><p style={muted}>Signed in as <b style={{color:"#eee9ff"}}>@{username}</b></p></div>
          <button style={secondary} onClick={signOut} disabled={busy}>Sign out</button>
        </header>

        <section style={inboxCard}>
          <div style={sectionHead}><div><h2 style={subheading}>Unified OTP inbox</h2><p style={muted}>Up to 10 recent codes · service name and time · auto-filtered after 24 hours</p></div><span style={lock}>AES-256-GCM</span></div>
          {!unlocked ? <>
            <p style={body}>Your inbox is being decrypted using the account password from this sign-in session. No second vault password is needed.</p>
            <button style={primary} disabled={busy} onClick={unlockInbox}>{busy ? "Opening inbox…" : "Retry opening inbox"}</button>
            <button style={textButton} onClick={() => router.replace("/account")}>Sign in again</button>
          </> : <>
            <div style={toolbar}><span style={muted}>{recentItems.length} recent {recentItems.length === 1 ? "entry" : "entries"} · live refresh every 2 sec</span><div style={{display:"flex",gap:12}}><button style={textButton} disabled={busy} onClick={refreshInbox}>Refresh</button><button style={textButton} onClick={() => { setUnlocked(false); setItems([]); setPassphrase(""); }}>Lock inbox</button></div></div>
            {recentItems.length === 0 ? <div style={empty}><div style={{fontSize:27}}>⌁</div><strong>No synced OTPs yet</strong><p style={muted}>Enable notification access and Automatic encrypted sync in the Android app. New detections upload automatically when Android permits; this inbox checks for new uploads every 2 seconds while unlocked.</p></div> :
              <div style={list}>{recentItems.map((item, index) => <article key={item.receivedAt + "-" + index} style={otpRow}><div><strong style={{display:"block",color:"#d9d0ff",fontSize:13,marginBottom:5}}>{item.source || "Unknown service"}</strong><div style={code}>{revealed[index] ? item.code : "••••••"}</div><small style={muted}>{new Date(item.receivedAt).toLocaleString()}</small></div><div style={{display:"flex",gap:8}}><button style={smallButton} onClick={() => setRevealed((old) => ({...old,[index]:!old[index]}))}>{revealed[index] ? "Hide" : "Show"}</button><button style={smallButton} onClick={() => { void navigator.clipboard.writeText(item.code); setNotice("Code copied to clipboard. Clear the clipboard when finished."); }}>Copy</button></div></article>)}</div>}
            <button style={danger} disabled={busy} onClick={clearInbox}>Clear synced inbox</button>
          </>}
          {notice && <p style={success}>{notice}</p>}
          {error && <p style={failure}>{error}</p>}
        </section>

        <section style={instructions}>
          <h2 style={subheading}>Connect your Android phone</h2>
          <ol style={steps}>
            <li>Open OTP Hub on your phone and sign in to the same account.</li>
            <li>Choose <b>Enable notification access</b>. Android's Notification Access is a broad permission: the listener can receive notification metadata from apps on your phone, so enable it only if you are comfortable with that access.</li>
            <li>Return to the app, enter your OTP Hub account password in the sync field, and turn on <b>Automatic encrypted sync</b>. New detected OTP candidates are encrypted and uploaded in the background. Manual Sync remains available as a fallback.</li>
            <li>The inbox opens using the password from your account sign-in. While the inbox remains unlocked and this tab is visible, it checks for new uploads every 2 seconds and updates automatically. Android battery saver, connectivity, and background limits may delay phone uploads.</li>
          </ol>
          <p style={warning}>Privacy: only notification-derived numeric code candidates are supported. OTPs are sensitive account credentials. Keep this inbox private, do not share codes, and avoid syncing codes from accounts you do not control. Notification access can expose previews from selected apps, so enable it only if you understand that permission.</p>
        </section>

        <footer style={footer}><Link href="/download" style={link}>Android app</Link><Link href="/account" style={link}>Account settings</Link></footer>
      </section>
    </main>
  );
}

const shell: React.CSSProperties = { minHeight:"100vh", padding:"42px 18px", background:"radial-gradient(circle at 15% 0%,#252044 0,transparent 35%),#090b12", color:"#f4f2fb", fontFamily:"Arial,sans-serif" };
const panel: React.CSSProperties = { maxWidth:820, margin:"0 auto", background:"#111522", border:"1px solid #292d42", borderRadius:22, padding:28, boxShadow:"0 20px 70px #0007" };
const header: React.CSSProperties = { display:"flex", justifyContent:"space-between", alignItems:"flex-start", gap:16, flexWrap:"wrap", marginBottom:24 };
const eyebrow: React.CSSProperties = { color:"#aa91ff", fontSize:11, letterSpacing:2, fontWeight:700, margin:"0 0 10px" };
const heading: React.CSSProperties = { fontSize:30, margin:"0 0 8px", letterSpacing:-.7 };
const subheading: React.CSSProperties = { fontSize:18, margin:"0 0 5px", color:"#f2edff" };
const muted: React.CSSProperties = { color:"#969bb0", fontSize:13, lineHeight:1.6, margin:"5px 0" };
const body: React.CSSProperties = { color:"#b5b7c8", fontSize:13, lineHeight:1.7, margin:"16px 0" };
const inboxCard: React.CSSProperties = { border:"1px solid #34304e", borderRadius:16, padding:20, background:"linear-gradient(145deg,#19182b,#121725)" };
const sectionHead: React.CSSProperties = { display:"flex", justifyContent:"space-between", alignItems:"center", gap:12, flexWrap:"wrap" };
const lock: React.CSSProperties = { border:"1px solid #4a4168", borderRadius:20, padding:"6px 10px", color:"#c5b4ff", fontSize:10 };
const label: React.CSSProperties = { display:"block", color:"#d7d2e9", fontSize:12, fontWeight:700, margin:"16px 0 7px" };
const input: React.CSSProperties = { display:"block", width:"100%", maxWidth:420, padding:13, borderRadius:9, border:"1px solid #41445b", background:"#0c101b", color:"#fff", marginBottom:12 };
const primary: React.CSSProperties = { border:0, borderRadius:9, padding:"12px 16px", background:"linear-gradient(135deg,#9476f0,#7053d2)", color:"white", fontWeight:700, cursor:"pointer" };
const secondary: React.CSSProperties = { border:"1px solid #42445a", borderRadius:9, padding:"10px 14px", background:"#171c2b", color:"#ddd7ef", cursor:"pointer" };
const toolbar: React.CSSProperties = { display:"flex", justifyContent:"space-between", alignItems:"center", margin:"18px 0 12px" };
const textButton: React.CSSProperties = { border:0, background:"transparent", color:"#b9a7ff", cursor:"pointer" };
const empty: React.CSSProperties = { textAlign:"center", padding:"35px 18px", border:"1px dashed #42445a", borderRadius:12, color:"#c9c2e0" };
const list: React.CSSProperties = { display:"grid", gap:8, margin:"12px 0 16px" };
const otpRow: React.CSSProperties = { display:"flex", alignItems:"center", justifyContent:"space-between", gap:12, flexWrap:"wrap", padding:14, border:"1px solid #33384d", borderRadius:11, background:"#101522" };
const code: React.CSSProperties = { fontSize:25, letterSpacing:3, fontWeight:800, color:"#f3efff", marginBottom:4, fontVariantNumeric:"tabular-nums" };
const smallButton: React.CSSProperties = { border:"1px solid #44445d", borderRadius:8, padding:"8px 10px", background:"#1a1e30", color:"#d8d1ec", cursor:"pointer", fontSize:12 };
const danger: React.CSSProperties = { border:"1px solid #75414b", borderRadius:8, padding:"10px 13px", background:"#2b1820", color:"#ffb7c0", cursor:"pointer" };
const success: React.CSSProperties = { color:"#80e0b5", fontSize:12, lineHeight:1.6, marginTop:14 };
const failure: React.CSSProperties = { color:"#ff9ca9", fontSize:12, lineHeight:1.6, marginTop:14 };
const instructions: React.CSSProperties = { marginTop:22, padding:20, border:"1px solid #292f42", borderRadius:14, background:"#141a28" };
const steps: React.CSSProperties = { color:"#b7bbca", fontSize:13, lineHeight:1.9, paddingLeft:22 };
const warning: React.CSSProperties = { color:"#c9b78b", background:"#282315", border:"1px solid #574a2d", borderRadius:10, padding:13, fontSize:11, lineHeight:1.7 };
const footer: React.CSSProperties = { display:"flex", flexWrap:"wrap", gap:18, paddingTop:22 };
const link: React.CSSProperties = { color:"#b6a3ff", fontSize:12, textDecoration:"none", fontWeight:700 };
