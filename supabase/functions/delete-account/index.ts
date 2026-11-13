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
//     2. deletes that user's row in public.user_cloud_state (cloud backup),
//     3. deletes the Supabase Auth user (admin API). Rows referencing auth.users with
//        ON DELETE CASCADE go with it, including public.privacy_consents.
//   It answers {"deleted": true} only when every step succeeded. On a failure it answers with the
//   stage that failed; the app then tells the learner the account was NOT (fully) deleted.
//   Retrying is safe: deleting an already deleted cloud row is not an error.
//
// Security notes
//   - The service-role / secret key comes from the Edge Function environment (provided by
//     Supabase) and is never returned or logged. It is never part of the APK.
//   - Deploy with JWT verification OFF; the function verifies the user's token itself through
//     Supabase Auth (this also works with the new asymmetric JWT signing keys):
//       supabase functions deploy delete-account --no-verify-jwt
//
// Provided automatically by Supabase: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY. No other secret.

const MAX_BODY_BYTES = 4 * 1024;
const CONFIRM_WORD = "DELETE";

export interface Deps {
  /** Supabase Auth: the user id for a user access token, or null when the token is not valid. */
  verifyUser: (accessToken: string) => Promise<string | null>;
  /** Deletes the user's cloud backup row(s). Throws on failure. */
  deleteCloudState: (userId: string) => Promise<void>;
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
    await deps.deleteCloudState(userId);
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

function productionDeps(): Deps {
  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const service: Record<string, string> = { apikey: key };
  // Legacy service_role keys are JWTs and also go in Authorization; new sb_secret_ keys only in apikey.
  if (key.startsWith("eyJ")) service["Authorization"] = `Bearer ${key}`;
  return {
    configured: url.length > 0 && key.length > 0,
    verifyUser: async (accessToken) => {
      const r = await fetch(`${url}/auth/v1/user`, { headers: { apikey: key, Authorization: `Bearer ${accessToken}` } });
      if (r.status === 401 || r.status === 403) return null;
      if (!r.ok) throw new Error(`auth_${r.status}`);
      const u = await r.json();
      return typeof u?.id === "string" ? u.id : null;
    },
    deleteCloudState: async (userId) => {
      const r = await fetch(`${url}/rest/v1/user_cloud_state?user_id=eq.${encodeURIComponent(userId)}`, {
        method: "DELETE",
        headers: { ...service, Prefer: "return=minimal" },
      });
      if (!r.ok) throw new Error(`cloud_${r.status}`);
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
