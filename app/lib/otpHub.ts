const API_URL = "https://xllstbnkvtustctpkbap.supabase.co";
const PUBLIC_KEY = "sb_publishable_Ot6DZiGzEaebJMDSjiC23Q_1BwRqCYP";
const TOKEN_KEY = "otp-hub-session-token";
const USER_KEY = "otp-hub-username";

export type AccountResult = { username: string; session_token?: string; recovery_code?: string };

export function getSavedAccount() {
  if (typeof window === "undefined") return { token: "", username: "" };
  return { token: localStorage.getItem(TOKEN_KEY) || "", username: localStorage.getItem(USER_KEY) || "" };
}
export function saveAccount(result: AccountResult) {
  if (typeof window === "undefined") return;
  if (result.session_token) localStorage.setItem(TOKEN_KEY, result.session_token);
  localStorage.setItem(USER_KEY, result.username);
}
export function clearAccount() {
  if (typeof window === "undefined") return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}
export async function rpc<T = unknown>(name: string, args: Record<string, unknown>): Promise<T> {
  const response = await fetch(`${API_URL}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: { apikey: PUBLIC_KEY, Authorization: `Bearer ${PUBLIC_KEY}`, "Content-Type": "application/json" },
    body: JSON.stringify(args)
  });
  const text = await response.text();
  let data: any = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) throw new Error(data?.message || data?.hint || "Account request failed.");
  return data as T;
}
export async function validateAccount(token: string) {
  return rpc<{ username: string }>("otp_hub_session", { p_token: token });
}
