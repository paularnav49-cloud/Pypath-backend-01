// Tests for the delete-account Edge Function (mocked Supabase; no network, no database).
// Run:  PYPATH_FUNCTION_TEST=1 deno test --allow-env supabase/functions/delete-account/index_test.ts
import { type Deps, handle, keyHeaders, pickKey, productionDeps } from "./index.ts";

function assert(cond: unknown, msg = "assertion failed"): asserts cond {
  if (!cond) throw new Error(msg);
}

const A = "11111111-1111-1111-1111-111111111111";
const B = "22222222-2222-2222-2222-222222222222";

/** Fake Supabase: tokens map to users; records which users' data were deleted. */
function fake(opts: { cloudFails?: boolean; authFails?: boolean; authDown?: boolean } = {}) {
  const tokens: Record<string, string> = { "token-a": A, "token-b": B };
  const cloud = new Set([A, B]);
  const users = new Set([A, B]);
  const deps: Deps = {
    configured: true,
    verifyUser: (t) => opts.authDown ? Promise.reject(new Error("down")) : Promise.resolve(tokens[t] ?? null),
    deleteUserData: (u) => opts.cloudFails ? Promise.reject(new Error("x")) : (cloud.delete(u), Promise.resolve()),
    deleteAuthUser: (u) => opts.authFails ? Promise.reject(new Error("x")) : (users.delete(u), Promise.resolve()),
  };
  return { deps, cloud, users };
}

function req(token: string | null, body: unknown, method = "POST") {
  const headers: Record<string, string> = {};
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return new Request("http://localhost/", { method, headers, body: method === "POST" ? JSON.stringify(body) : undefined });
}

Deno.test("deletes only the caller's cloud data and auth user", async () => {
  const f = fake();
  const r = await handle(req("token-a", { confirm: "DELETE" }), f.deps);
  assert(r.status === 200 && (await r.json()).deleted === true);
  assert(!f.cloud.has(A) && !f.users.has(A));
  assert(f.cloud.has(B) && f.users.has(B), "another user is untouched");
});

Deno.test("a user id in the body is ignored (cannot delete another account)", async () => {
  const f = fake();
  const r = await handle(req("token-a", { confirm: "DELETE", user_id: B, id: B }), f.deps);
  assert(r.status === 200);
  assert(f.users.has(B) && f.cloud.has(B), "B must stay");
  assert(!f.users.has(A));
});

Deno.test("invalid or missing token deletes nothing", async () => {
  for (const t of [null, "forged-token"]) {
    const f = fake();
    const r = await handle(req(t, { confirm: "DELETE" }), f.deps);
    assert(r.status === 401, `status ${r.status}`);
    assert(f.users.size === 2 && f.cloud.size === 2);
  }
});

Deno.test("explicit confirmation is required", async () => {
  const f = fake();
  assert((await handle(req("token-a", {}), f.deps)).status === 400);
  assert((await handle(req("token-a", { confirm: "yes" }), f.deps)).status === 400);
  assert((await handle(req("token-a", null, "GET"), f.deps)).status === 405);
  assert(f.users.size === 2 && f.cloud.size === 2);
});

Deno.test("failures are reported, never a success", async () => {
  let f = fake({ cloudFails: true });
  let r = await handle(req("token-a", { confirm: "DELETE" }), f.deps);
  let b = await r.json();
  assert(r.status === 502 && b.deleted === false && b.stage === "cloud_data");
  assert(f.users.has(A), "auth user not deleted when cloud delete failed");

  f = fake({ authFails: true });
  r = await handle(req("token-a", { confirm: "DELETE" }), f.deps);
  b = await r.json();
  assert(r.status === 502 && b.deleted === false && b.stage === "auth_user");

  f = fake({ authDown: true });
  r = await handle(req("token-a", { confirm: "DELETE" }), f.deps);
  assert(r.status === 503);
  assert(f.users.size === 2 && f.cloud.size === 2);
});

Deno.test("server without configuration refuses", async () => {
  const f = fake();
  f.deps.configured = false;
  assert((await handle(req("token-a", { confirm: "DELETE" }), f.deps)).status === 500);
  assert(f.users.size === 2);
});

Deno.test("server key: new SUPABASE_SECRET_KEYS preferred, legacy key only as fallback", () => {
  assert(pickKey('{"default":"sb_secret_new"}', "eyJlegacy") === "sb_secret_new");
  assert(pickKey('{"other":"sb_secret_x"}', "eyJlegacy") === "sb_secret_x");
  assert(pickKey(undefined, "eyJlegacy") === "eyJlegacy");
  assert(pickKey("", "eyJlegacy") === "eyJlegacy");
  assert(pickKey("not json", "eyJlegacy") === "eyJlegacy");
  assert(pickKey("{}", undefined) === "");
  const n = keyHeaders("sb_secret_new");
  assert(n.apikey === "sb_secret_new" && !("Authorization" in n), "new keys never go in Authorization");
  const l = keyHeaders("eyJlegacy");
  assert(l.apikey === "eyJlegacy" && l.Authorization === "Bearer eyJlegacy");
});

Deno.test("an unconfigured server deletes nothing and says so", async () => {
  const f = fake();
  const r = await handle(req("token-a", { confirm: "DELETE" }), { ...f.deps, configured: false });
  assert(r.status === 500 && (await r.json()).error === "server_not_configured");
  assert(f.users.size === 2 && f.cloud.size === 2);
});

/** Records the HTTP calls the production wiring makes (no network). */
function recorder(answers: Record<string, number>) {
  const calls: { method: string; url: string; headers: Record<string, string>; body: string }[] = [];
  const f = (async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? "GET";
    const headers = Object.fromEntries(new Headers(init?.headers).entries());
    calls.push({ method, url, headers, body: String(init?.body ?? "") });
    const key = Object.keys(answers).find((k) => `${method} ${url}`.includes(k));
    const status = key ? answers[key] : 200;
    const body = url.endsWith("/auth/v1/user") ? JSON.stringify({ id: A }) : "{}";
    return new Response(status === 204 ? null : body, { status });
  }) as typeof globalThis.fetch;
  return { f, calls };
}

const ENV: Record<string, string> = {
  SUPABASE_URL: "https://p.supabase.co",
  SUPABASE_SECRET_KEYS: '{"default":"sb_secret_test"}',
  SUPABASE_PUBLISHABLE_KEYS: '{"default":"sb_publishable_test"}',
  SUPABASE_SERVICE_ROLE_KEY: "eyJlegacy",
};

Deno.test("production wiring: verify with publishable key, delete via RPC then Auth admin with the secret key", async () => {
  const rec = recorder({});
  const deps = productionDeps((n) => ENV[n], rec.f);
  const r = await handle(req("user-token", { confirm: "DELETE" }), deps);
  assert(r.status === 200 && (await r.json()).deleted === true);
  const [verify, rpc, admin] = rec.calls;
  assert(rec.calls.length === 3, `calls ${rec.calls.length}`);
  assert(verify.url.endsWith("/auth/v1/user") && verify.headers["apikey"] === "sb_publishable_test");
  assert(verify.headers["authorization"] === "Bearer user-token");
  assert(rpc.method === "POST" && rpc.url.endsWith("/rest/v1/rpc/pypath_delete_account_data"));
  assert(JSON.parse(rpc.body).p_user_id === A, "only the verified user id is sent");
  assert(rpc.headers["apikey"] === "sb_secret_test" && !rpc.headers["authorization"]);
  assert(admin.method === "DELETE" && admin.url.endsWith(`/auth/v1/admin/users/${A}`));
  assert(admin.headers["apikey"] === "sb_secret_test");
});

Deno.test("production wiring: migration not applied (RPC 404) falls back to direct table deletes", async () => {
  const rec = recorder({ "rpc/pypath_delete_account_data": 404 });
  const r = await handle(req("user-token", { confirm: "DELETE" }), productionDeps((n) => ENV[n], rec.f));
  assert(r.status === 200);
  const urls = rec.calls.map((c) => `${c.method} ${c.url}`);
  assert(urls.some((u) => u.startsWith("DELETE") && u.includes(`/rest/v1/user_cloud_state?user_id=eq.${A}`)));
  assert(urls.some((u) => u.startsWith("DELETE") && u.includes(`/rest/v1/privacy_consents?user_id=eq.${A}`)));
  assert(urls[urls.length - 1].includes("/auth/v1/admin/users/"));
});

Deno.test("production wiring: data delete failure stops before the Auth user is deleted", async () => {
  const rec = recorder({ "rpc/pypath_delete_account_data": 500 });
  const r = await handle(req("user-token", { confirm: "DELETE" }), productionDeps((n) => ENV[n], rec.f));
  const b = await r.json();
  assert(r.status === 502 && b.deleted === false && b.stage === "cloud_data");
  assert(!rec.calls.some((c) => c.url.includes("/auth/v1/admin/users/")), "Auth user must not be deleted");
});

Deno.test("production wiring: Auth admin failure is reported, never a success", async () => {
  const rec = recorder({ "DELETE https://p.supabase.co/auth/v1/admin": 500 });
  const r = await handle(req("user-token", { confirm: "DELETE" }), productionDeps((n) => ENV[n], rec.f));
  const b = await r.json();
  assert(r.status === 502 && b.deleted === false && b.stage === "auth_user");
});

Deno.test("production wiring: legacy-only environment still works (JWT key also in Authorization)", async () => {
  const rec = recorder({});
  const env: Record<string, string> = { SUPABASE_URL: "https://p.supabase.co", SUPABASE_SERVICE_ROLE_KEY: "eyJlegacy", SUPABASE_ANON_KEY: "eyJanon" };
  const r = await handle(req("user-token", { confirm: "DELETE" }), productionDeps((n) => env[n], rec.f));
  assert(r.status === 200);
  assert(rec.calls[0].headers["apikey"] === "eyJanon");
  assert(rec.calls[1].headers["authorization"] === "Bearer eyJlegacy");
});

Deno.test("production wiring: an invalid user token deletes nothing", async () => {
  const rec = recorder({ "/auth/v1/user": 401 });
  const r = await handle(req("bad", { confirm: "DELETE" }), productionDeps((n) => ENV[n], rec.f));
  assert(r.status === 401 && rec.calls.length === 1);
});
