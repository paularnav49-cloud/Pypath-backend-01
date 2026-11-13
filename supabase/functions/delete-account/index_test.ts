// Tests for the delete-account Edge Function (mocked Supabase; no network, no database).
// Run:  PYPATH_FUNCTION_TEST=1 deno test --allow-env supabase/functions/delete-account/index_test.ts
import { type Deps, handle } from "./index.ts";

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
    deleteCloudState: (u) => opts.cloudFails ? Promise.reject(new Error("x")) : (cloud.delete(u), Promise.resolve()),
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
