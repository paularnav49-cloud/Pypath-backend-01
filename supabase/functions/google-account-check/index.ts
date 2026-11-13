// PyPath — google-account-check (Supabase Edge Function)
//
// Purpose
//   Lets the PyPath Android app keep "Sign in with Google" (create an account) and
//   "Login with Google" (existing accounts only) as two separate operations.
//
//   The app sends the Google ID token it just received from the Google account picker, the raw
//   nonce of that sign-in attempt and the mode ("signup" or "login"). This function:
//     1. verifies the Google ID token itself (RS256 signature against Google's published keys,
//        issuer, audience = PyPath's Web OAuth client ID, expiry, and the nonce),
//     2. applies rate limits (per hashed IP address and per hashed Google account),
//     3. asks the database (service role, server side only) whether a Supabase Auth user with this
//        Google identity already exists,
//     4. in "signup" mode records a short-lived sign-up intent (used by the optional
//        before-user-created hook, see supabase/migrations/*_signup_guard_hook.sql),
//     5. answers { exists: boolean }.
//
// Security notes
//   - No account enumeration: an answer is only given for the Google identity whose valid, fresh,
//     Google-signed ID token (issued to PyPath's own client ID) is presented. Nobody can ask about
//     an arbitrary e-mail address or Google account.
//   - The service-role / secret key is read from the Edge Function environment (provided by
//     Supabase) and is never returned or logged. It is never part of the APK.
//   - The ID token is not stored or logged. Rate-limit buckets and sign-up intents store only
//     SHA-256 hashes.
//   - Deploy with JWT verification OFF (the app calls it before it has a Supabase session; the
//     function authenticates the caller through the Google ID token instead):
//       supabase functions deploy google-account-check --no-verify-jwt
//
// Required secret (Dashboard > Edge Functions > Secrets):
//   GOOGLE_WEB_CLIENT_ID = the Web OAuth client ID from PyPath's res/values/google_auth.xml
//                          (several IDs may be given, separated by commas)
// Optional secret:
//   RATE_LIMIT_SALT      = any random string; mixed into the IP/account hashes
// Provided automatically by Supabase: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY

const GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const GOOGLE_ISSUERS = new Set(["https://accounts.google.com", "accounts.google.com"]);
const MAX_BODY_BYTES = 16 * 1024;
const CLOCK_SKEW_SECONDS = 60;

type Jwk = { kid: string; kty: string; n: string; e: string; alg?: string; use?: string };

export interface Deps {
  now: () => number; // epoch seconds
  fetchJwks: () => Promise<Jwk[]>;
  rpc: (args: Record<string, unknown>) => Promise<{ allowed: boolean; exists: boolean }>;
  clientIds: string[];
  salt: string;
}

// ───────────── Helpers ─────────────

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

/** Decodes base64url into a plain ArrayBuffer (accepted by WebCrypto and TextDecoder in every TS/Deno version). */
function b64urlToBytes(s: string): ArrayBuffer {
  const pad = s.length % 4 === 0 ? "" : "=".repeat(4 - (s.length % 4));
  const bin = atob(s.replace(/-/g, "+").replace(/_/g, "/") + pad);
  const buf = new ArrayBuffer(bin.length);
  const out = new Uint8Array(buf);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return buf;
}

async function sha256Hex(text: string): Promise<string> {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(d)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Constant-time comparison of two equal-length strings. */
function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

// JWKS cache (per warm function instance).
let jwksCache: { keys: Jwk[]; until: number } | null = null;

async function defaultFetchJwks(): Promise<Jwk[]> {
  const nowMs = Date.now();
  if (jwksCache && jwksCache.until > nowMs) return jwksCache.keys;
  const r = await fetch(GOOGLE_JWKS_URL);
  if (!r.ok) throw new Error("jwks_unavailable");
  const body = await r.json();
  const maxAge = Number(/max-age=(\d+)/.exec(r.headers.get("cache-control") ?? "")?.[1] ?? "3600");
  jwksCache = { keys: body.keys ?? [], until: nowMs + Math.min(maxAge, 6 * 3600) * 1000 };
  return jwksCache.keys;
}

export interface GoogleClaims {
  sub: string;
  email: string;
  email_verified?: boolean;
  nonce?: string;
}

/**
 * Verifies a Google ID token. Returns its claims, or throws an Error whose message is a short
 * machine-readable reason (never the token itself).
 */
export async function verifyGoogleIdToken(idToken: string, rawNonce: string, deps: Pick<Deps, "now" | "fetchJwks" | "clientIds">): Promise<GoogleClaims> {
  const parts = idToken.split(".");
  if (parts.length !== 3) throw new Error("malformed_token");
  let header: { alg?: string; kid?: string };
  let payload: Record<string, unknown>;
  try {
    header = JSON.parse(new TextDecoder().decode(b64urlToBytes(parts[0])));
    payload = JSON.parse(new TextDecoder().decode(b64urlToBytes(parts[1])));
  } catch {
    throw new Error("malformed_token");
  }
  if (header.alg !== "RS256" || !header.kid) throw new Error("unsupported_token");
  const jwk = (await deps.fetchJwks()).find((k) => k.kid === header.kid && k.kty === "RSA");
  if (!jwk) throw new Error("unknown_key");
  const key = await crypto.subtle.importKey(
    "jwk",
    { kty: "RSA", n: jwk.n, e: jwk.e, alg: "RS256", ext: true },
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"],
  );
  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    b64urlToBytes(parts[2]),
    new TextEncoder().encode(`${parts[0]}.${parts[1]}`),
  );
  if (!ok) throw new Error("bad_signature");

  const now = deps.now();
  if (typeof payload.iss !== "string" || !GOOGLE_ISSUERS.has(payload.iss)) throw new Error("bad_issuer");
  const aud = payload.aud;
  const auds = Array.isArray(aud) ? aud : [aud];
  if (!auds.some((a) => typeof a === "string" && deps.clientIds.includes(a))) throw new Error("bad_audience");
  if (typeof payload.exp !== "number" || payload.exp + CLOCK_SKEW_SECONDS < now) throw new Error("expired");
  if (typeof payload.iat === "number" && payload.iat - CLOCK_SKEW_SECONDS > now) throw new Error("not_yet_valid");
  if (typeof payload.sub !== "string" || payload.sub.length === 0) throw new Error("no_subject");
  if (typeof payload.email !== "string" || payload.email.length === 0) throw new Error("no_email");
  // PyPath gives Google the SHA-256 (hex) of the raw nonce and keeps the raw value (see Nonce.kt).
  if (typeof payload.nonce !== "string" || !rawNonce || !safeEqual(payload.nonce, await sha256Hex(rawNonce))) {
    throw new Error("bad_nonce");
  }
  return {
    sub: payload.sub,
    email: payload.email,
    email_verified: payload.email_verified === true || payload.email_verified === "true",
    nonce: payload.nonce,
  };
}

function clientIp(req: Request): string {
  const fwd = req.headers.get("x-forwarded-for") ?? "";
  return (fwd.split(",")[0] || req.headers.get("x-real-ip") || "unknown").trim();
}

// ───────────── Web client ID (token audience) ─────────────

/** Shape of a Google OAuth client ID: "<project number>-<id>.apps.googleusercontent.com". */
export const CLIENT_ID_PATTERN = /^[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com$/;

/**
 * Parses the GOOGLE_WEB_CLIENT_ID secret: comma-separated, surrounding spaces and quotes removed.
 * Entries that do not look like a Google OAuth client ID are returned in `invalid`.
 */
export function parseClientIds(raw: string): { ids: string[]; invalid: string[] } {
  const ids: string[] = [];
  const invalid: string[] = [];
  for (const part of raw.split(",")) {
    const v = part.trim().replace(/^["']+|["']+$/g, "").trim();
    if (!v) continue;
    (CLIENT_ID_PATTERN.test(v) ? ids : invalid).push(v);
  }
  return { ids, invalid };
}

// ───────────── Handler ─────────────

export async function handle(req: Request, deps: Deps): Promise<Response> {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  if (deps.clientIds.length === 0) return json(500, { error: "server_not_configured" });

  const raw = await req.text();
  if (raw.length > MAX_BODY_BYTES) return json(413, { error: "too_large" });
  let body: { mode?: unknown; id_token?: unknown; nonce?: unknown };
  try {
    body = JSON.parse(raw);
  } catch {
    return json(400, { error: "bad_request" });
  }
  const mode = body.mode;
  if (mode !== "signup" && mode !== "login") return json(400, { error: "bad_mode" });
  if (typeof body.id_token !== "string" || typeof body.nonce !== "string") return json(400, { error: "bad_request" });

  // Cheap per-IP limit first, so invalid tokens cannot be used to hammer the key check.
  const ipBucket = "ip:" + (await sha256Hex(deps.salt + "|ip|" + clientIp(req)));

  let claims: GoogleClaims;
  try {
    claims = await verifyGoogleIdToken(body.id_token, body.nonce, deps);
  } catch (e) {
    // Count failed attempts against the IP as well.
    try {
      const r = await deps.rpc({ p_mode: "probe", p_google_sub: null, p_email: null, p_ip_bucket: ipBucket, p_account_bucket: null });
      if (!r.allowed) return json(429, { error: "rate_limited" });
    } catch { /* ignore: the token is rejected anyway */ }
    const reason = e instanceof Error ? e.message : "invalid_token";
    return json(reason === "jwks_unavailable" ? 503 : 401, { error: "invalid_token", reason });
  }

  const accountBucket = "acct:" + (await sha256Hex(deps.salt + "|sub|" + claims.sub));
  let result: { allowed: boolean; exists: boolean };
  try {
    result = await deps.rpc({
      p_mode: mode,
      p_google_sub: claims.sub,
      p_email: claims.email,
      p_ip_bucket: ipBucket,
      p_account_bucket: accountBucket,
    });
  } catch {
    return json(503, { error: "check_unavailable" });
  }
  if (!result.allowed) return json(429, { error: "rate_limited" });
  return json(200, { exists: result.exists === true, mode });
}

// ───────────── Production wiring ─────────────

function productionDeps(): Deps {
  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const parsed = parseClientIds(Deno.env.get("GOOGLE_WEB_CLIENT_ID") ?? "");
  if (parsed.invalid.length > 0) {
    // Client IDs are not secrets; logging the rejected value helps fix a copy/paste mistake.
    console.error("GOOGLE_WEB_CLIENT_ID contains an invalid Web client ID:", parsed.invalid.join(", "));
  }
  // A malformed value disables the function (500 server_not_configured) instead of silently
  // verifying tokens against the wrong audience.
  const clientIds = parsed.invalid.length > 0 ? [] : parsed.ids;
  return {
    now: () => Math.floor(Date.now() / 1000),
    fetchJwks: defaultFetchJwks,
    clientIds,
    salt: Deno.env.get("RATE_LIMIT_SALT") ?? "pypath",
    rpc: async (args) => {
      if (!url || !key) throw new Error("not_configured");
      const headers: Record<string, string> = { "Content-Type": "application/json", apikey: key };
      // Legacy service_role keys are JWTs and also go in Authorization; new sb_secret_ keys only in apikey.
      if (key.startsWith("eyJ")) headers["Authorization"] = `Bearer ${key}`;
      const r = await fetch(`${url}/rest/v1/rpc/pypath_google_account_check`, {
        method: "POST",
        headers,
        body: JSON.stringify(args),
      });
      if (!r.ok) throw new Error(`rpc_${r.status}`);
      const out = await r.json();
      return { allowed: out?.allowed === true, exists: out?.exists === true };
    },
  };
}

// Unit tests import this file with PYPATH_FUNCTION_TEST=1 so no server is started.
if (Deno.env.get("PYPATH_FUNCTION_TEST") !== "1") {
  const deps = productionDeps();
  Deno.serve((req) => handle(req, deps));
}
