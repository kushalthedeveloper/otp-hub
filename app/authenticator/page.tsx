"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import "./vault.css";

type Entry = { id: string; issuer: string; account: string; secret: string };
type VaultRecord = { salt: string; iv: string; data: string };

const STORAGE_KEY = "otp-hub-encrypted-vault-v1";
const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

function decodeBase32(input: string): Uint8Array {
  const clean = input.toUpperCase().replace(/[^A-Z2-7]/g, "").replace(/=+$/g, "");
  let bits = "";
  for (const char of clean) {
    const value = alphabet.indexOf(char);
    if (value < 0) throw new Error("Secret must be a valid Base32 value.");
    bits += value.toString(2).padStart(5, "0");
  }
  const bytes = new Uint8Array(Math.floor(bits.length / 8));
  for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(bits.slice(i * 8, i * 8 + 8), 2);
  if (!bytes.length) throw new Error("Enter a valid authenticator secret.");
  return bytes;
}

function toBase64(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => binary += String.fromCharCode(byte));
  return btoa(binary);
}
function fromBase64(value: string): Uint8Array {
  return Uint8Array.from(atob(value), (char) => char.charCodeAt(0));
}
async function deriveKey(passphrase: string, salt: Uint8Array): Promise<CryptoKey> {
  const material = await crypto.subtle.importKey("raw", new TextEncoder().encode(passphrase), "PBKDF2", false, ["deriveKey"]);
  return crypto.subtle.deriveKey({ name: "PBKDF2", salt: new Uint8Array(salt).buffer as ArrayBuffer, iterations: 310000, hash: "SHA-256" }, material, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
}
async function encryptVault(entries: Entry[], passphrase: string): Promise<VaultRecord> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await deriveKey(passphrase, salt);
  const cipher = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, new TextEncoder().encode(JSON.stringify(entries)));
  return { salt: toBase64(salt), iv: toBase64(iv), data: toBase64(new Uint8Array(cipher)) };
}
async function decryptVault(record: VaultRecord, passphrase: string): Promise<Entry[]> {
  const key = await deriveKey(passphrase, fromBase64(record.salt));
  const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: new Uint8Array(fromBase64(record.iv)).buffer as ArrayBuffer }, key, new Uint8Array(fromBase64(record.data)).buffer as ArrayBuffer);
  return JSON.parse(new TextDecoder().decode(plain)) as Entry[];
}
async function generateCode(secret: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", new Uint8Array(decodeBase32(secret)).buffer as ArrayBuffer, { name: "HMAC", hash: "SHA-1" }, false, ["sign"]);
  const counter = Math.floor(Date.now() / 1000 / 30);
  const msg = new Uint8Array(8);
  let n = counter;
  for (let i = 7; i >= 0; i--) { msg[i] = n & 255; n = Math.floor(n / 256); }
  const digest = new Uint8Array(await crypto.subtle.sign("HMAC", key, msg));
  const offset = digest[digest.length - 1] & 15;
  const binary = ((digest[offset] & 127) << 24) | ((digest[offset + 1] & 255) << 16) | ((digest[offset + 2] & 255) << 8) | (digest[offset + 3] & 255);
  return String(binary % 1000000).padStart(6, "0");
}

export default function AuthenticatorPage() {
  const [hasVault, setHasVault] = useState(false);
  const [unlocked, setUnlocked] = useState(false);
  const [passphrase, setPassphrase] = useState("");
  const [entries, setEntries] = useState<Entry[]>([]);
  const [codes, setCodes] = useState<Record<string, string>>({});
  const [remaining, setRemaining] = useState(30 - Math.floor(Date.now() / 1000) % 30);
  const [issuer, setIssuer] = useState("");
  const [account, setAccount] = useState("");
  const [secret, setSecret] = useState("");
  const [error, setError] = useState("");
  const [copied, setCopied] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { setHasVault(Boolean(localStorage.getItem(STORAGE_KEY))); }, []);
  useEffect(() => {
    const tick = () => setRemaining(30 - Math.floor(Date.now() / 1000) % 30);
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, []);

  async function refreshCodes(list: Entry[]) {
    const next: Record<string, string> = {};
    for (const entry of list) {
      try { next[entry.id] = await generateCode(entry.secret); } catch { next[entry.id] = "------"; }
    }
    setCodes(next);
  }
  useEffect(() => {
    if (!unlocked) return;
    void refreshCodes(entries);
    const timer = window.setInterval(() => void refreshCodes(entries), 1000);
    return () => window.clearInterval(timer);
  }, [unlocked, entries, remaining]);

  async function createOrUnlock() {
    setError("");
    if (passphrase.length < 12) { setError("Use a passphrase with at least 12 characters."); return; }
    setBusy(true);
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        const decoded = await decryptVault(JSON.parse(stored) as VaultRecord, passphrase);
        setEntries(decoded);
      } else {
        const initial = await encryptVault([], passphrase);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(initial));
        setEntries([]);
        setHasVault(true);
      }
      setUnlocked(true);
    } catch {
      setError("Could not unlock the vault. Check your passphrase. If this is a new vault, try creating it again.");
    } finally { setBusy(false); }
  }

  async function saveEntries(next: Entry[]) {
    const encrypted = await encryptVault(next, passphrase);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(encrypted));
    setEntries(next);
  }

  async function addEntry(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    try {
      decodeBase32(secret);
      const next = [...entries, { id: crypto.randomUUID(), issuer: issuer.trim(), account: account.trim(), secret: secret.replace(/\s/g, "").toUpperCase() }];
      await saveEntries(next);
      setIssuer(""); setAccount(""); setSecret("");
    } catch (e) { setError(e instanceof Error ? e.message : "Could not add this account."); }
  }

  async function removeEntry(id: string) {
    const next = entries.filter((entry) => entry.id !== id);
    await saveEntries(next);
  }

  async function copyCode(id: string) {
    const code = codes[id];
    if (!code || code === "------") return;
    await navigator.clipboard.writeText(code);
    setCopied(id);
    window.setTimeout(() => setCopied(""), 1500);
  }

  function lockVault() {
    setUnlocked(false); setPassphrase(""); setEntries([]); setCodes({});
  }

  const progress = useMemo(() => (remaining / 30) * 100, [remaining]);

  return (
    <main className="vault-shell">
      <header className="vault-top"><Link href="/" className="vault-brand"><span>O</span> OTP Hub</Link><Link href="/" className="back-link">← Dashboard</Link></header>
      <section className="vault-content">
        <div className="vault-kicker">PERSONAL AUTHENTICATOR</div>
        <h1>Your codes, generated <em>locally.</em></h1>
        <p className="vault-intro">A private authenticator vault for accounts you intentionally add. Your secrets are encrypted in this browser and are never sent to an OTP Hub server.</p>
        <div className="vault-safety"><span>♢</span><div><strong>Local encryption is enabled</strong><small>AES-256-GCM encryption · PBKDF2 key derivation · This browser only</small></div></div>

        {!unlocked ? (
          <div className="vault-card unlock-card">
            <div className="vault-card-icon">◈</div>
            <h2>{hasVault ? "Unlock your vault" : "Create your vault"}</h2>
            <p>{hasVault ? "Enter the passphrase you used when creating this vault." : "Choose a strong passphrase. It encrypts your authenticator secrets on this device."}</p>
            <label htmlFor="vault-pass">Vault passphrase</label>
            <input id="vault-pass" type="password" autoComplete="current-password" value={passphrase} onChange={(e) => setPassphrase(e.target.value)} placeholder="At least 12 characters" />
            {error && <div className="vault-error">{error}</div>}
            <button className="vault-primary" onClick={createOrUnlock} disabled={busy}>{busy ? "Working…" : hasVault ? "Unlock vault" : "Create encrypted vault"}</button>
            <small className="vault-footnote">Important: if you forget this passphrase, your encrypted vault cannot be recovered. Keep a secure offline backup of your recovery information.</small>
          </div>
        ) : (
          <>
            <div className="vault-toolbar"><div><strong>{entries.length} authenticator {entries.length === 1 ? "account" : "accounts"}</strong><small>Codes refresh every 30 seconds on this device.</small></div><button className="vault-lock" onClick={lockVault}>Lock vault</button></div>
            <div className="code-list">
              {entries.length === 0 ? <div className="empty-vault"><span>◈</span><strong>Your vault is ready</strong><p>Add an authenticator account using its setup key. No incoming SMS, WhatsApp, or email messages are scanned.</p></div> : entries.map((entry) => (
                <article className="code-card" key={entry.id}>
                  <div className="code-meta"><span className="code-logo">{entry.issuer.slice(0, 1).toUpperCase()}</span><div><strong>{entry.issuer}</strong><small>{entry.account}</small></div><button className="remove-code" onClick={() => void removeEntry(entry.id)} aria-label={`Remove ${entry.issuer}`}>×</button></div>
                  <div className="code-number">{codes[entry.id] || "••••••"}</div>
                  <div className="code-bottom"><span>Refreshes in {remaining}s</span><div className="code-progress"><i style={{ width: `${progress}%` }} /></div><button onClick={() => void copyCode(entry.id)}>{copied === entry.id ? "Copied ✓" : "Copy code"}</button></div>
                </article>
              ))}
            </div>
            <form className="add-code-card" onSubmit={addEntry}>
              <h2>Add an authenticator account</h2>
              <p>Enter the setup key supplied by a service when you enable an authenticator app.</p>
              <div className="form-grid"><label>Service name<input required value={issuer} onChange={(e) => setIssuer(e.target.value)} placeholder="e.g. Example service" /></label><label>Account label<input required value={account} onChange={(e) => setAccount(e.target.value)} placeholder="e.g. you@example.com" /></label></div>
              <label>Authenticator setup key<input required value={secret} onChange={(e) => setSecret(e.target.value)} placeholder="BASE32 SECRET" spellCheck={false} /></label>
              {error && <div className="vault-error">{error}</div>}
              <button className="vault-primary" type="submit">＋ Add to encrypted vault</button>
            </form>
          </>
        )}
        <div className="vault-disclaimer"><strong>Security note</strong><p>This is an early local-only authenticator implementation. It generates TOTP codes from setup keys; it does not receive or relay one-time login codes sent by SMS, WhatsApp, or email. Do not add a setup key to a shared or public computer.</p></div>
      </section>
    </main>
  );
}
