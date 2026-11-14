-- ============================================================================================
-- PyPath — OPTIONAL hardening: "Login never creates an account" enforced by the Auth server
-- Migration: 20261113000100_pypath_optional_signup_guard_hook.sql
--
-- Requires 20261113000000_pypath_privacy_consent_and_account_check.sql to be applied first.
--
-- Without this hook the separation of "Sign in with Google" (create) and "Login with Google"
-- (existing accounts only) is enforced by the app + the google-account-check Edge Function: the
-- app never exchanges the Google token in Login mode unless the Edge Function confirmed the account
-- exists. A modified client could still call Supabase Auth directly and create an account (which
-- is the same thing "Sign in with Google" does, so no one gains access to anything they could not
-- get anyway).
--
-- With this hook enabled, Supabase Auth itself refuses to create a NEW Google user unless the
-- google-account-check Edge Function recorded an explicit sign-up request for that Google account
-- within the last 10 minutes. Existing users are never affected (the hook only runs when a user
-- is about to be created). Non-Google sign-ups are left alone.
--
-- HOW TO ENABLE (after running this file in the SQL Editor):
--   Supabase Dashboard > Authentication > Hooks > "Before User Created" > Enable
--   Hook type: Postgres   Schema: public   Function: pypath_before_user_created
--
-- HOW TO DISABLE: switch the hook off in the same screen (nothing else depends on it).
--
-- IMPORTANT: enable it only AFTER the google-account-check Edge Function is deployed and the
-- updated app is in use; older app versions (which do not call the Edge Function) would no longer
-- be able to create new accounts while the hook is on. Existing accounts keep working.
-- ============================================================================================

begin;

create or replace function public.pypath_before_user_created(event jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_provider text := event -> 'user' -> 'app_metadata' ->> 'provider';
    v_email    text := lower(coalesce(event -> 'user' ->> 'email', ''));
    v_hash     text;
    v_found    boolean := false;
begin
    if v_provider is distinct from 'google' then
        return '{}'::jsonb;
    end if;

    v_hash := encode(sha256(convert_to(v_email, 'UTF8')), 'hex');

    delete from pypath_private.signup_intents
    where email_hash = v_hash
      and expires_at > now()
    returning true into v_found;

    if coalesce(v_found, false) then
        return '{}'::jsonb;
    end if;

    return jsonb_build_object(
        'error', jsonb_build_object(
            'http_code', 403,
            'message', 'PYPATH_SIGNUP_REQUIRED: No PyPath account exists for this Google account yet. Use "Sign in with Google" to create one.'
        )
    );
end;
$$;

comment on function public.pypath_before_user_created(jsonb) is
    'PyPath: optional Supabase Auth "before user created" hook. New Google users need a recent explicit sign-up request.';

revoke all on function public.pypath_before_user_created(jsonb) from public, anon, authenticated;
grant execute on function public.pypath_before_user_created(jsonb) to supabase_auth_admin;

commit;
