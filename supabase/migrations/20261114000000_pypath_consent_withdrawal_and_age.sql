-- ============================================================================================
-- PyPath — correction migration: consent withdrawal + age confirmation
-- Migration: 20261114000000_pypath_consent_withdrawal_and_age.sql
--
-- REQUIRED for the corrected app (in-app "Withdraw consent"). Apply AFTER
-- 20261113000000_pypath_privacy_consent_and_account_check.sql:
--   Supabase Dashboard > SQL Editor > New query > paste this whole file > Run
--   (or: supabase db push, if you use the Supabase CLI)
--
-- Non-destructive and safe to re-run: no table is dropped, no row is changed or deleted, and
-- public.user_cloud_state and its RLS policies are NOT touched.
--
-- What it changes on public.privacy_consents
--   1. Drops the unique (user_id, policy_version) constraint, so the table can be an append-only
--      log of events: agreed -> withdrawn -> agreed again for the same policy version. The app
--      reads only the NEWEST row (highest id) of the signed-in user.
--   2. Adds an index (user_id, id desc) for that lookup.
--   3. Adds the nullable column age_confirmed (the user's self-declared age confirmation when agreeing:
--      18 or older, or 12 to 17 with a parent's or guardian's agreement; not verified).
--   4. Recreates the INSERT policy: a signed-in user may insert 'agreed' or 'withdrawn' rows for
--      THEMSELVES only (auth.uid() = user_id; the trigger also forces user_id from the JWT).
--      A 'withdrawn' row never carries age_confirmed. Users still cannot UPDATE or DELETE rows,
--      so nobody can change another user's (or their own past) consent records.
-- ============================================================================================

begin;

alter table public.privacy_consents
    drop constraint if exists privacy_consents_user_version_key;

create index if not exists privacy_consents_user_id_id_desc_idx
    on public.privacy_consents (user_id, id desc);

alter table public.privacy_consents
    add column if not exists age_confirmed boolean;

comment on column public.privacy_consents.age_confirmed is
    'PyPath: true when the user confirmed, together with this agreement, being 18 or older or being 12 to 17 with a parent''s or guardian''s agreement. Self-declared, not verified.';

comment on table public.privacy_consents is
    'PyPath: append-only log of Privacy Policy consent events (agreed / withdrawn) per user. The newest row per user is the current state. Never stores tokens.';

drop policy if exists "privacy_consents_insert_own" on public.privacy_consents;
create policy "privacy_consents_insert_own"
    on public.privacy_consents
    for insert
    to authenticated
    with check (
        (select auth.uid()) = user_id
        and (
            status = 'agreed'
            or (status = 'withdrawn' and age_confirmed is null)
        )
    );

-- Unchanged from the first migration, repeated so this file alone leaves a known state.
revoke all on table public.privacy_consents from anon, public;
revoke update, delete, truncate, references, trigger on table public.privacy_consents from authenticated;
grant select, insert on table public.privacy_consents to authenticated;

commit;
