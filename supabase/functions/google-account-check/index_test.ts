// Tests for the google-account-check Edge Function (no network, no database).
// Run:  PYPATH_FUNCTION_TEST=1 deno test --allow-env --allow-read supabase/functions/google-account-check/index_test.ts
import { type Deps, handle, parseClientIds } from "./index.ts";

function assert(cond: unknown, msg = "assertion failed"): asserts cond {
  if (!cond) throw new Error(msg);
}

const enc = (o: unknown) =>
  btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(o)))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const b64u = (b: ArrayBuffer) =>
  btoa(String.fromCharCode(...new Uint8Array(b))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
async function sha256Hex(t: string) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(t));
  return Array.from(new Uint8Array(d)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

const CLIENT = "123-abc.apps.googleusercontent.com";
const NOW = 1_800_000_000;
const pair = await crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
  true,
  ["sign", "verify"],
) as CryptoKeyPair;
const other = await crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
  true,
  ["sign", "verify"],
) as CryptoKeyPair;
const pub = await crypto.subtle.exportKey("jwk", pair.publicKey);

async function token(claims: Record<string, unknown>, key = pair.privateKey, kid = "k1") {
  const h = enc({ alg: "RS256", kid, typ: "JWT" });
  const p = enc(claims);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${h}.${p}`));
  return `${h}.${p}.${b64u(sig)}`;
}

const RAW_NONCE = "raw-nonce-123";
async function goodClaims(extra: Record<string, unknown> = {}) {
  return {
    iss: "https://accounts.google.com", aud: CLIENT, sub: "g-1", email: "a@example.com", email_verified: true,
    iat: NOW - 10, exp: NOW + 3600, nonce: await sha256Hex(RAW_NONCE), ...extra,
  };
}

function deps(existing = new Set<string>(), log: Record<string, unknown>[] = [], allowed = true): Deps {
  return {
    now: () => NOW,
    fetchJwks: () => Promise.resolve([{ kid: "k1", kty: "RSA", n: pub.n!, e: pub.e! }]),
    clientIds: [CLIENT],
    salt: "test",
    rpc: (args) => {
      log.push(args);
      return Promise.resolve({ allowed, exists: typeof args.p_google_sub === "string" && existing.has(args.p_google_sub) });
    },
  };
}

function req(body: unknown, method = "POST") {
  return new Request("http://localhost/", {
    method,
    headers: { "x-forwarded-for": "203.0.113.5" },
    body: method === "POST" ? JSON.stringify(body) : undefined,
  });
}

Deno.test("login with a known Google identity reports exists=true", async () => {
  const log: Record<string, unknown>[] = [];
  const r = await handle(req({ mode: "login", id_token: await token(await goodClaims()), nonce: RAW_NONCE }), deps(new Set(["g-1"]), log));
  assert(r.status === 200, `status ${r.status}`);
  const b = await r.json();
  assert(b.exists === true && b.mode === "login");
  // Only hashes of the IP and account are sent as rate-limit buckets.
  assert(String(log[0].p_ip_bucket).startsWith("ip:") && !String(log[0].p_ip_bucket).includes("203.0.113.5"));
  assert(String(log[0].p_account_bucket).startsWith("acct:") && !String(log[0].p_account_bucket).includes("g-1"));
});

Deno.test("login with an unknown identity reports exists=false", async () => {
  const r = await handle(req({ mode: "login", id_token: await token(await goodClaims()), nonce: RAW_NONCE }), deps());
  assert(r.status === 200);
  assert((await r.json()).exists === false);
});

Deno.test("signup mode is passed through", async () => {
  const log: Record<string, unknown>[] = [];
  const r = await handle(req({ mode: "signup", id_token: await token(await goodClaims()), nonce: RAW_NONCE }), deps(new Set(), log));
  assert(r.status === 200);
  assert(log[0].p_mode === "signup" && log[0].p_google_sub === "g-1" && log[0].p_email === "a@example.com");
});

for (const [name, claims, nonce, key] of [
  ["wrong audience (token for another app)", { aud: "999-other.apps.googleusercontent.com" }, RAW_NONCE, "good"],
  ["wrong issuer", { iss: "https://evil.example" }, RAW_NONCE, "good"],
  ["expired", { exp: NOW - 3600, iat: NOW - 7200 }, RAW_NONCE, "good"],
  ["nonce mismatch (replayed token)", {}, "another-nonce", "good"],
  ["bad signature", {}, RAW_NONCE, "other"],
] as const) {
  Deno.test(`rejects: ${name}`, async () => {
    const log: Record<string, unknown>[] = [];
    const t = await token(await goodClaims(claims as Record<string, unknown>), key === "good" ? pair.privateKey : other.privateKey);
    const r = await handle(req({ mode: "login", id_token: t, nonce }), deps(new Set(["g-1"]), log));
    assert(r.status === 401, `status ${r.status}`);
    const b = await r.json();
    assert(b.exists === undefined, "no account information for an invalid token");
    // Only the IP probe is counted; no account lookup happened.
    assert(log.every((a) => a.p_mode === "probe" && a.p_google_sub === null));
  });
}

Deno.test("rejects malformed requests", async () => {
  assert((await handle(req({ mode: "delete", id_token: "x", nonce: "y" }), deps())).status === 400);
  assert((await handle(req({ mode: "login" }), deps())).status === 400);
  assert((await handle(req(null, "GET"), deps())).status === 405);
  assert((await handle(req({ mode: "login", id_token: "not.a.jwt", nonce: "n" }), deps())).status === 401);
});

Deno.test("rate limited", async () => {
  const r = await handle(req({ mode: "login", id_token: await token(await goodClaims()), nonce: RAW_NONCE }), deps(new Set(["g-1"]), [], false));
  assert(r.status === 429);
});

Deno.test("database unavailable gives 503, never a guess", async () => {
  const d = deps();
  d.rpc = () => Promise.reject(new Error("down"));
  const r = await handle(req({ mode: "login", id_token: await token(await goodClaims()), nonce: RAW_NONCE }), d);
  assert(r.status === 503);
});

Deno.test("server without client id refuses", async () => {
  const d = deps();
  d.clientIds = [];
  assert((await handle(req({ mode: "login", id_token: "a.b.c", nonce: "n" }), d)).status === 500);
});

// ───────────── Web client ID (audience) ─────────────

/** The Web client ID the Android app requests tokens for (source of truth: google_auth.xml). */
function repoWebClientId(): string {
  const xml = Deno.readTextFileSync(new URL("../../../PyPath/app/src/main/res/values/google_auth.xml", import.meta.url));
  const m = xml.match(/<string name="google_web_client_id"[^>]*>\s*([^<\s]+)\s*<\/string>/);
  assert(m, "google_web_client_id not found in google_auth.xml");
  return m[1];
}

Deno.test("client id secret is parsed (spaces, quotes, several ids) and malformed values are flagged", () => {
  const p = parseClientIds(` "123-abc.apps.googleusercontent.com" , 456-def.apps.googleusercontent.com,`);
  assert(p.ids.length === 2 && p.ids[0] === "123-abc.apps.googleusercontent.com" && p.invalid.length === 0);
  const bad = parseClientIds("123-abc.apps.googleusercontent.com, not-a-client-id, 123-abc");
  assert(bad.invalid.length === 2, JSON.stringify(bad));
  assert(parseClientIds("").ids.length === 0);
});

Deno.test("the app's Web client ID from google_auth.xml is a valid audience value", async () => {
  const id = repoWebClientId();
  const p = parseClientIds(id);
  assert(p.ids.length === 1 && p.invalid.length === 0, id);
  // A token issued to exactly that client ID is accepted when the secret holds the same value.
  const d = deps(new Set(["g-1"]));
  d.clientIds = p.ids;
  const r = await handle(req({ mode: "login", id_token: await token(await goodClaims({ aud: id })), nonce: RAW_NONCE }), d);
  assert(r.status === 200, `status ${r.status}`);
});

Deno.test("a client ID that differs by one character (l vs 1) is a different audience and is rejected", async () => {
  const id = repoWebClientId();
  const variant = id.replace(/l/g, "1");
  assert(variant !== id, "test needs an id containing 'l'");
  const d = deps(new Set(["g-1"]));
  d.clientIds = [variant];
  const r = await handle(req({ mode: "login", id_token: await token(await goodClaims({ aud: id })), nonce: RAW_NONCE }), d);
  assert(r.status === 401, `status ${r.status}`);
  assert((await r.json()).reason === "bad_audience");
});
