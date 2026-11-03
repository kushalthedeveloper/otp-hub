"use client";

import { useMemo, useState } from "react";

type Channel = "all" | "authenticator" | "email" | "device";

const sampleItems = [
  { id: 1, title: "Authenticator vault", detail: "Local demo workspace", channel: "authenticator", time: "Ready to set up", icon: "◈", tone: "violet" },
  { id: 2, title: "Email connection", detail: "Official OAuth integration · not connected", channel: "email", time: "Not connected", icon: "✉", tone: "blue" },
  { id: 3, title: "This device", detail: "Browser session · sample status", channel: "device", time: "Current session", icon: "▣", tone: "green" }
] as const;

const channelLabels: Record<Channel, string> = {
  all: "All connections",
  authenticator: "Authenticator",
  email: "Email",
  device: "Devices"
};

export default function Home() {
  const [active, setActive] = useState<Channel>("all");
  const [showNotice, setShowNotice] = useState(true);
  const [menuOpen, setMenuOpen] = useState(false);
  const items = useMemo(() => active === "all" ? sampleItems : sampleItems.filter((item) => item.channel === active), [active]);

  return (
    <main className="app-shell">
      <aside className={`sidebar ${menuOpen ? "sidebar-open" : ""}`}>
        <a className="brand" href="#" aria-label="OTP Hub home">
          <span className="brand-mark">O</span>
          <span><strong>otp<span>hub</span></strong><small>PERSONAL SECURITY</small></span>
        </a>
        <div className="workspace-label">WORKSPACE</div>
        <nav className="nav-list" aria-label="Main navigation">
          <button className="nav-item active"><span>⌂</span> Overview</button>
          <a className="nav-item" href="/authenticator"><span>◈</span> Authenticator</a>
          <a className="nav-item" href="/download"><span>↓</span> Download app</a>
          <a className="nav-item" href="/account"><span>◎</span> Account / Sign in</a>
          <button className="nav-item" onClick={() => setActive("email")}><span>✉</span> Email connections</button>
          <button className="nav-item" onClick={() => setActive("device")}><span>▣</span> Devices</button>
        </nav>
        <div className="sidebar-bottom">
          <div className="secure-card">
            <div className="secure-icon">✳</div>
            <strong>Your codes stay yours.</strong>
            <p>OTP Hub is designed around consent, encryption, and data minimization.</p>
            <a href="#privacy">Privacy principles ↗</a>
          </div>
          <div className="profile-row">
            <div className="avatar">K</div>
            <div><strong>OTP Hub account</strong><small>Username login</small></div>
            <span className="more">•••</span>
          </div>
        </div>
      </aside>

      <section className="main-area">
        <header className="topbar">
          <button className="mobile-menu" onClick={() => setMenuOpen(!menuOpen)} aria-label="Toggle menu">☰</button>
          <div className="breadcrumb">Workspace <span>/</span> <strong>Overview</strong></div>
          <div className="top-actions">
            <a className="status-pill" href="/account" style={{ textDecoration: "none" }}><i /> Account / Sign in</a>
            <button className="icon-button" aria-label="Notifications">♧</button>
            <div className="top-avatar">K</div>
          </div>
        </header>

        <div className="content">
          <div className="welcome-row">
            <div>
              <div className="eyebrow"><span className="eyebrow-line" /> YOUR SECURITY, IN ONE PLACE</div>
              <h1>Good afternoon<span className="title-dot">.</span></h1>
              <p className="subtitle">A clearer view of your personal authentication setup.</p>
            </div>
            <a className="primary-button" href="/authenticator"><span>＋</span> Set up authenticator</a>
          </div>

          {showNotice && (
            <div className="notice">
              <div className="notice-icon">ⓘ</div>
              <div><strong>Preview mode — sample information only</strong><p>No messages, email inboxes, or login codes are being accessed. Connectors will only be added through official, permission-based flows.</p></div>
              <button className="notice-close" onClick={() => setShowNotice(false)} aria-label="Dismiss notice">×</button>
            </div>
          )}

          <div className="stats-grid">
            <div className="stat-card"><div className="stat-top"><span>CONNECTED METHODS</span><span className="stat-icon purple">◈</span></div><div className="stat-value">0 <small>/ 3</small></div><div className="stat-foot"><span className="muted-dot" /> No live integrations yet</div></div>
            <div className="stat-card"><div className="stat-top"><span>PROTECTED DEVICES</span><span className="stat-icon blue">▣</span></div><div className="stat-value">1</div><div className="stat-foot"><span className="green-dot" /> This browser · demo</div></div>
            <div className="stat-card"><div className="stat-top"><span>SECURITY STATUS</span><span className="stat-icon green">✳</span></div><div className="stat-value status-text">Getting started</div><div className="stat-foot">Complete setup to review security</div></div>
          </div>

          <div className="section-heading">
            <div><h2>Your connections</h2><p>Manage the services and devices you authorize.</p></div>
            <button className="text-button" onClick={() => setActive("all")}>View all <span>→</span></button>
          </div>

          <div className="filter-row" role="tablist" aria-label="Filter connections">
            {(Object.keys(channelLabels) as Channel[]).map((key) => (
              <button key={key} role="tab" aria-selected={active === key} className={`filter-chip ${active === key ? "selected" : ""}`} onClick={() => setActive(key)}>{channelLabels[key]}</button>
            ))}
          </div>

          <div className="connection-list">
            {items.map((item) => (
              <div className="connection-card" key={item.id}>
                <div className={`connection-icon ${item.tone}`}>{item.icon}</div>
                <div className="connection-copy"><strong>{item.title}</strong><span>{item.detail}</span></div>
                <div className="connection-time">{item.time}</div>
                <button className="arrow-button" onClick={() => setActive(item.channel as Channel)} aria-label={`View ${item.title}`}>↗</button>
              </div>
            ))}
          </div>

          <div className="lower-grid">
            <div className="panel" id="privacy">
              <div className="panel-heading"><div><h3>Privacy by design</h3><p>Built to keep control in your hands.</p></div><span className="shield">♢</span></div>
              <div className="privacy-points">
                <div><span className="check">✓</span><div><strong>Explicit permission</strong><small>Every future integration starts with your consent.</small></div></div>
                <div><span className="check">✓</span><div><strong>Minimal data</strong><small>Only request the access a feature actually needs.</small></div></div>
                <div><span className="check">✓</span><div><strong>No code forwarding</strong><small>Login codes from messages are not intercepted or relayed.</small></div></div>
              </div>
            </div>
            <div className="panel setup-panel">
              <div className="setup-orbit"><span>◈</span></div>
              <div><span className="mini-label">NEXT STEP</span><h3>Set up your authenticator</h3><p>Plan a personal authenticator vault for codes you intentionally add and control.</p><a className="secondary-button" href="/authenticator">Explore setup <span>→</span></a></div>
            </div>
          </div>
          <footer><span>© 2026 OTP Hub</span><span>Privacy-first by design <b>·</b> Preview build 0.1.0</span></footer>
        </div>
      </section>
    </main>
  );
}
