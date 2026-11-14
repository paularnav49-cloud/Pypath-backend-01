-- ============================================================================================
-- PyPath — account deletion: remove all PyPath data of one user in ONE transaction
-- ============================================================================================
--
-- Used only by the `delete-account` Edge Function (service role), AFTER it verified the caller's
-- own Supabase access token. The function then deletes the Supabase Auth user.
--
-- Why: the earlier version deleted only `public.user_cloud_state` through PostgREST and relied
-- on ON DELETE CASCADE for `public.privacy_consents`. `privacy_consents` is created with
-- "create table if not exists", so if the table existed before (without that cascade) the Auth
-- user could not be deleted ("Database error deleting user") and consent rows could be left
-- behind. This function deletes every PyPath row of the user explicitly and atomically.
--
-- Deletes, for p_user_id only:
--   * public.user_cloud_state      (cloud backup; only if the table exists)
--   * public.privacy_consents      (consent history; see the retention note in the README)
--   * pypath_private.signup_intents for the user's e-mail (10-minute sign-up markers)
-- Not deleted here: pypath_private.auth_check_attempts holds only salted hashes used as rate-limit
-- counters (no user id, no e-mail) and is purged automatically after one day.
--
-- Safe to re-run (create or replace). Nothing is dropped or altered; no other user is touched.
-- EXECUTE is granted to service_role ONLY (not to anon / authenticated): a learner can never call
-- it, and the Edge Function passes only the user id it verified itself.
-- ============================================================================================

create or replace function public.pypath_delete_account_data(p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_email   text;
    v_cloud   integer := 0;
    v_consent integer := 0;
    v_intent  integer := 0;
begin
    if p_user_id is null then
        raise exception 'missing user id' using errcode = '22023';
    end if;

    select u.email into v_email from auth.users u where u.id = p_user_id;

    if to_regclass('public.user_cloud_state') is not null then
        execute 'delete from public.user_cloud_state where user_id = $1' using p_user_id;
        get diagnostics v_cloud = row_count;
    end if;

    if to_regclass('public.privacy_consents') is not null then
        delete from public.privacy_consents where user_id = p_user_id;
        get diagnostics v_consent = row_count;
    end if;

    if v_email is not null and to_regclass('pypath_private.signup_intents') is not null then
        delete from pypath_private.signup_intents
        where email_hash = encode(sha256(convert_to(lower(v_email), 'UTF8')), 'hex');
        get diagnostics v_intent = row_count;
    end if;

    return jsonb_build_object(
        'cloud_state_rows', v_cloud,
        'consent_rows', v_consent,
        'signup_intent_rows', v_intent
    );
end;
$$;

comment on function public.pypath_delete_account_data(uuid) is
    'PyPath: deletes all PyPath rows of one user in one transaction. Called only by the delete-account Edge Function (service role) after it verified the user''s token.';

revoke all on function public.pypath_delete_account_data(uuid) from public, anon, authenticated;
grant execute on function public.pypath_delete_account_data(uuid) to service_role;
