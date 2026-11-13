-- ============================================================================================
-- PyPath — Privacy Policy consent records + server-side Google account check
-- Migration: 20261113000000_pypath_privacy_consent_and_account_check.sql
--
-- Apply ONCE in the existing PyPath Supabase project:
--   Supabase Dashboard > SQL Editor > New query > paste this whole file > Run
--   (or: supabase db push, if you use the Supabase CLI)
--
-- Safe to re-run: every object is created with IF NOT EXISTS / CREATE OR REPLACE and policies are
-- dropped and recreated by name. It does NOT touch public.user_cloud_state or any existing RLS
-- policy, and it does not change any Auth setting.
--
-- What it creates
--   1. public.privacy_consents            one row per (user, policy version) the user agreed to
--      + RLS: a signed-in user can read and insert only their own rows; nobody (except the
--        service role / database owner) can update or delete them; anon has no access.
--   2. schema pypath_private               not exposed through the Data API
--        pypath_private.auth_check_attempts   rate-limit counters (hashed keys only)
--        pypath_private.signup_intents        short-lived "this Google account asked to sign up"
--                                             markers (hashed e-mail / Google id only)
--   3. public.pypath_google_account_check(...)  SECURITY DEFINER; EXECUTE granted ONLY to
--        service_role. Called by the google-account-check Edge Function, never by the app.
-- ============================================================================================

begin;

-- --------------------------------------------------------------------------------------------
-- 1. Privacy Policy consent records
-- --------------------------------------------------------------------------------------------

create table if not exists public.privacy_consents (
    id              bigint generated always as identity primary key,
    -- Filled from the caller's JWT by the trigger below; a client cannot choose another user.
    user_id         uuid        not null default auth.uid() references auth.users (id) on delete cascade,
    -- Version string of the policy text the user agreed to, e.g. '2026-11-13'.
    policy_version  text        not null check (policy_version ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}(\.[0-9]{1,3})?$'),
    -- 'agreed' is the only value a user can insert. 'withdrawn' is reserved for a future,
    -- server-side withdrawal flow.
    status          text        not null default 'agreed' check (status in ('agreed', 'withdrawn')),
    -- Server time; set by the trigger, the client value (if any) is ignored.
    consented_at    timestamptz not null default now(),
    constraint privacy_consents_user_version_key unique (user_id, policy_version)
);

comment on table public.privacy_consents is
    'PyPath: explicit Privacy Policy consent per user and policy version. Never stores tokens.';

create index if not exists privacy_consents_user_id_idx on public.privacy_consents (user_id);

-- Trigger: user_id always comes from the JWT and consented_at always from the server clock.
create or replace function public.pypath_privacy_consents_before_insert()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    if auth.uid() is not null then
        new.user_id := auth.uid();
    end if;
    new.consented_at := now();
    return new;
end;
$$;

drop trigger if exists pypath_privacy_consents_before_insert on public.privacy_consents;
create trigger pypath_privacy_consents_before_insert
    before insert on public.privacy_consents
    for each row execute function public.pypath_privacy_consents_before_insert();

alter table public.privacy_consents enable row level security;

drop policy if exists "privacy_consents_select_own" on public.privacy_consents;
create policy "privacy_consents_select_own"
    on public.privacy_consents
    for select
    to authenticated
    using ((select auth.uid()) = user_id);

drop policy if exists "privacy_consents_insert_own" on public.privacy_consents;
create policy "privacy_consents_insert_own"
    on public.privacy_consents
    for insert
    to authenticated
    with check ((select auth.uid()) = user_id and status = 'agreed');

-- No UPDATE / DELETE policies: consent records are append-only for users.
-- Belt and braces on top of RLS: remove the privileges entirely.
revoke all on table public.privacy_consents from anon, public;
revoke update, delete, truncate, references, trigger on table public.privacy_consents from authenticated;
grant select, insert on table public.privacy_consents to authenticated;

-- --------------------------------------------------------------------------------------------
-- 2. Private schema for the account check (not exposed through the Data API)
-- --------------------------------------------------------------------------------------------

create schema if not exists pypath_private;
revoke all on schema pypath_private from public, anon, authenticated;

create table if not exists pypath_private.auth_check_attempts (
    bucket        text        primary key,   -- 'ip:<sha256>' or 'acct:<sha256>'
    window_start  timestamptz not null,
    attempts      integer     not null
);

create table if not exists pypath_private.signup_intents (
    email_hash       text        primary key,  -- sha256(lower(email)), hex
    google_sub_hash  text        not null,     -- sha256(google sub), hex
    expires_at       timestamptz not null
);

revoke all on all tables in schema pypath_private from public, anon, authenticated;

-- --------------------------------------------------------------------------------------------
-- 3. Account check RPC (service_role only)
--
--   p_mode            'signup' | 'login' | 'probe' ('probe' = only count an invalid-token attempt)
--   p_google_sub      Google account id ("sub") from a token the Edge Function already VERIFIED
--   p_email           e-mail from that verified token
--   p_ip_bucket       'ip:<hash>'   (hashed by the Edge Function)
--   p_account_bucket  'acct:<hash>' (hashed by the Edge Function)
--
-- Returns {"allowed": bool, "exists": bool}
-- Limits: 30 calls per IP and 10 per Google account in a 10-minute window.
-- --------------------------------------------------------------------------------------------

create or replace function public.pypath_google_account_check(
    p_mode            text,
    p_google_sub      text,
    p_email           text,
    p_ip_bucket       text,
    p_account_bucket  text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_window   constant interval := interval '10 minutes';
    v_ip_max   constant integer  := 30;
    v_acct_max constant integer  := 10;
    v_count    integer;
    v_exists   boolean := false;
begin
    if p_mode is null or p_mode not in ('signup', 'login', 'probe') then
        raise exception 'invalid mode' using errcode = '22023';
    end if;

    -- Occasional cleanup of old counters and expired intents.
    if random() < 0.02 then
        delete from pypath_private.auth_check_attempts where window_start < now() - interval '1 day';
        delete from pypath_private.signup_intents where expires_at < now();
    end if;

    -- Per-IP limit.
    if p_ip_bucket is not null then
        insert into pypath_private.auth_check_attempts as a (bucket, window_start, attempts)
        values (p_ip_bucket, now(), 1)
        on conflict (bucket) do update
            set attempts     = case when a.window_start < now() - v_window then 1 else a.attempts + 1 end,
                window_start = case when a.window_start < now() - v_window then now() else a.window_start end
        returning attempts into v_count;
        if v_count > v_ip_max then
            return jsonb_build_object('allowed', false, 'exists', false);
        end if;
    end if;

    if p_mode = 'probe' then
        return jsonb_build_object('allowed', true, 'exists', false);
    end if;

    if p_google_sub is null or length(p_google_sub) = 0 then
        raise exception 'missing google subject' using errcode = '22023';
    end if;

    -- Per-account limit.
    if p_account_bucket is not null then
        insert into pypath_private.auth_check_attempts as a (bucket, window_start, attempts)
        values (p_account_bucket, now(), 1)
        on conflict (bucket) do update
            set attempts     = case when a.window_start < now() - v_window then 1 else a.attempts + 1 end,
                window_start = case when a.window_start < now() - v_window then now() else a.window_start end
        returning attempts into v_count;
        if v_count > v_acct_max then
            return jsonb_build_object('allowed', false, 'exists', false);
        end if;
    end if;

    -- Does a Supabase Auth user with this Google identity exist?
    select exists (
        select 1
        from auth.identities i
        where i.provider = 'google'
          and (i.provider_id = p_google_sub or i.identity_data ->> 'sub' = p_google_sub)
    ) into v_exists;

    -- Explicit sign-up request for a new identity: remember it for 10 minutes (used only by the
    -- optional before-user-created hook; harmless without it).
    if p_mode = 'signup' and not v_exists and p_email is not null and length(p_email) > 0 then
        insert into pypath_private.signup_intents (email_hash, google_sub_hash, expires_at)
        values (
            encode(sha256(convert_to(lower(p_email), 'UTF8')), 'hex'),
            encode(sha256(convert_to(p_google_sub, 'UTF8')), 'hex'),
            now() + interval '10 minutes'
        )
        on conflict (email_hash) do update
            set google_sub_hash = excluded.google_sub_hash,
                expires_at      = excluded.expires_at;
    end if;

    return jsonb_build_object('allowed', true, 'exists', v_exists);
end;
$$;

comment on function public.pypath_google_account_check(text, text, text, text, text) is
    'PyPath: called only by the google-account-check Edge Function (service role) after it verified a Google ID token.';

revoke all on function public.pypath_google_account_check(text, text, text, text, text) from public, anon, authenticated;
grant execute on function public.pypath_google_account_check(text, text, text, text, text) to service_role;

commit;
