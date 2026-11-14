// PyPath — delete-account (Supabase Edge Function)
//
// Purpose
//   Lets a signed-in PyPath learner delete their own account from inside the app
//   (account > Privacy & account > Delete my account).
//
//   The app sends only its own Supabase access token (Authorization: Bearer <user JWT>) and the
//   body {"confirm":"DELETE"}. This function:
//     1. asks Supabase Auth which user that token belongs to (GET /auth/v1/user). The user id is
//        taken ONLY from that verified answer; any id in the request body is ignored, so a learner
//        can never delete another account,
//     2. deletes all of that user's PyPath rows in ONE database transaction
//        (public.pypath_delete_account_data: user_cloud_state, privacy_consents, sign-up markers;
//        falls back to deleting user_cloud_state and privacy_consents directly if that migration
//        is not applied yet),
//     3. deletes the Supabase Auth user (admin API).
//   It answers {"deleted": true} only when every step succeeded. On a failure it answers with the
//   stage that failed; the app then tells the learner the account was NOT (fully) deleted.
//   Retrying is safe: deleting an already deleted cloud row is not an error.
//
// Security notes
//   - The service-role / secret key comes from the Edge Function environment (provided by
//     Supabase) and is never returned or logged. It is never part of the APK.
//   - Deploy with JWT verification OFF (supabase/config.toml sets verify_jwt = false); the
//     function verifies the user's token itself through Supabase Auth. With "Verify JWT" ON the
//     Supabase gateway rejects user tokens signed with the new JWT signing keys (HTTP 401) before
//     this code runs, and nothing can be deleted:
//       supabase functions deploy delete-account --no-verify-jwt
//
// Provided automatically by Supabase: SUPABASE_URL and SUPABASE_SECRET_KEYS /
// SUPABASE_PUBLISHABLE_KEYS (new API keys) or SUPABASE_SERVICE_ROLE_KEY / SUPABASE_ANON_KEY
// (legacy keys, used only as a fallback). No other secret.

const MAX_BODY_BYTES = 4 * 1024;
const CONFIRM_WORD = "DELETE";

export interface Deps {
  /** Supabase Auth: the user id for a user access token, or null when the token is not valid. */
  verifyUser: (accessToken: string) => Promise<string | null>;
  /** Deletes all PyPath rows of the user (cloud backup, consent history, sign-up markers). Throws on failure. */
  deleteUserData: (userId: string) => Promise<void>;
  /** Deletes the Supabase Auth user. Throws on failure. */
  deleteAuthUser: (userId: string) => Promise<void>;
  configured: boolean;
}

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function handle(req: Request, deps: Deps): Promise<Response> {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  if (!deps.configured) return json(500, { error: "server_not_configured" });

  const auth = req.headers.get("authorization") ?? "";
  const m = auth.match(/^Bearer\s+(\S+)$/i);
  if (!m) return json(401, { error: "unauthorized" });

  const raw = await req.text();
  if (raw.length > MAX_BODY_BYTES) return json(413, { error: "too_large" });
  let body: { confirm?: unknown };
  try {
    body = JSON.parse(raw);
  } catch {
    return json(400, { error: "bad_request" });
  }
  if (body?.confirm !== CONFIRM_WORD) return json(400, { error: "confirmation_required" });

  let userId: string | null;
  try {
    userId = await deps.verifyUser(m[1]);
  } catch {
    return json(503, { error: "auth_unavailable", stage: "verify" });
  }
  if (!userId || !UUID.test(userId)) return json(401, { error: "unauthorized" });

  try {
    await deps.deleteUserData(userId);
  } catch {
    return json(502, { error: "delete_failed", stage: "cloud_data", deleted: false });
  }
  try {
    await deps.deleteAuthUser(userId);
  } catch {
    // Cloud backup is already gone; the account itself still exists. Retrying is safe.
    return json(502, { error: "delete_failed", stage: "auth_user", deleted: false, cloud_data_deleted: true });
  }
  return json(200, { deleted: true });
}

// ───────────── Production wiring ─────────────

/**
 * Picks a key from the Edge Function environment. Newer projects inject the new API keys as JSON
 * dictionaries (SUPABASE_SECRET_KEYS / SUPABASE_PUBLISHABLE_KEYS, e.g. {"default":"sb_secret_..."});
 * the legacy JWT keys (SUPABASE_SERVICE_ROLE_KEY / SUPABASE_ANON_KEY) stop working once legacy API
 * keys are disabled in the project. The new key is preferred; the legacy one is only a fallback.
 */
export function pickKey(dictJson: string | undefined, legacy: string | undefined): string {
  if (dictJson && dictJson.trim().length > 0) {
    try {
      const d = JSON.parse(dictJson);
      if (typeof d === "string" && d.length > 0) return d;
      if (d && typeof d === "object") {
        if (typeof d["default"] === "string" && d["default"].length > 0) return d["default"];
        for (const v of Object.values(d)) if (typeof v === "string" && v.length > 0) return v;
      }
    } catch {
      // not JSON: fall through to the legacy key
    }
  }
  return legacy ?? "";
}

/** Headers for a Supabase API key. Legacy keys are JWTs and also go in Authorization; new keys only in apikey. */
export function keyHeaders(key: string): Record<string, string> {
  const h: Record<string, string> = { apikey: key };
  if (key.startsWith("eyJ")) h["Authorization"] = `Bearer ${key}`;
  return h;
}

export function productionDeps(
  env: (name: string) => string | undefined = (n) => Deno.env.get(n),
  fetch: typeof globalThis.fetch = globalThis.fetch,
): Deps {
  const url = env("SUPABASE_URL") ?? "";
  // Server-only privileged key (never part of the APK, never returned or logged).
  const secret = pickKey(env("SUPABASE_SECRET_KEYS"), env("SUPABASE_SERVICE_ROLE_KEY"));
  // Low-privilege key, enough to ask Supabase Auth who a user token belongs to.
  const publishable = pickKey(env("SUPABASE_PUBLISHABLE_KEYS"), env("SUPABASE_ANON_KEY")) || secret;
  const service = keyHeaders(secret);
  return {
    configured: url.length > 0 && secret.length > 0,
    verifyUser: async (accessToken) => {
      const r = await fetch(`${url}/auth/v1/user`, { headers: { apikey: publishable, Authorization: `Bearer ${accessToken}` } });
      if (r.status === 401 || r.status === 403 || r.status === 404) return null;
      if (!r.ok) throw new Error(`auth_${r.status}`);
      const u = await r.json();
      return typeof u?.id === "string" ? u.id : null;
    },
    deleteUserData: async (userId) => {
      // One transaction: cloud backup, consent history and sign-up markers of this user only.
      const r = await fetch(`${url}/rest/v1/rpc/pypath_delete_account_data`, {
        method: "POST",
        headers: { ...service, "Content-Type": "application/json" },
        body: JSON.stringify({ p_user_id: userId }),
      });
      if (r.ok) return;
      if (r.status !== 404) throw new Error(`data_${r.status}`);
      // The 20261115000000 migration is not applied yet: delete the tables directly instead.
      for (const table of ["user_cloud_state", "privacy_consents"]) {
        const d = await fetch(`${url}/rest/v1/${table}?user_id=eq.${encodeURIComponent(userId)}`, {
          method: "DELETE",
          headers: { ...service, Prefer: "return=minimal" },
        });
        if (!d.ok) throw new Error(`${table}_${d.status}`);
      }
    },
    deleteAuthUser: async (userId) => {
      const r = await fetch(`${url}/auth/v1/admin/users/${encodeURIComponent(userId)}`, { method: "DELETE", headers: service });
      // 404: already deleted (e.g. a retry after a lost answer).
      if (!r.ok && r.status !== 404) throw new Error(`auth_delete_${r.status}`);
    },
  };
}

// Unit tests import this file with PYPATH_FUNCTION_TEST=1 so no server is started.
if (Deno.env.get("PYPATH_FUNCTION_TEST") !== "1") {
  const deps = productionDeps();
  Deno.serve((req) => handle(req, deps));
}
