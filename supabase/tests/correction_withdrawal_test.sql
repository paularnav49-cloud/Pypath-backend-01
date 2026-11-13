-- Local PostgreSQL checks for 20261114000000_pypath_consent_withdrawal_and_age.sql.
-- Run AFTER local_supabase_stub.sql, the 20261113000000 migration, rls_and_account_check_test.sql
-- and the correction migration. Plain PostgreSQL with stubbed Supabase roles: NOT a real Supabase test.
\echo C1 migration re-run is harmless (idempotent) -- see runner
\echo C2 user A: agreed (with age_confirmed) then withdrawn then agreed again for the same version -> all stored
begin; set local role authenticated; set local request.jwt.claim.sub='11111111-1111-1111-1111-111111111111';
insert into public.privacy_consents(policy_version, age_confirmed) values ('2026-11-13.1', true);
insert into public.privacy_consents(policy_version, status) values ('2026-11-13.1', 'withdrawn');
insert into public.privacy_consents(policy_version, age_confirmed) values ('2026-11-13.1', true);
select policy_version, status, age_confirmed from public.privacy_consents where policy_version = '2026-11-13.1' order by id;
commit;
\echo C3 newest row decides (what the app reads: order=id.desc&limit=1)
begin; set local role authenticated; set local request.jwt.claim.sub='11111111-1111-1111-1111-111111111111';
insert into public.privacy_consents(policy_version, status) values ('2026-11-13.1', 'withdrawn');
select policy_version, status from public.privacy_consents where user_id = '11111111-1111-1111-1111-111111111111' order by id desc limit 1;
commit;
\echo C4 user B cannot write a withdrawal for A (user_id forced from JWT) and sees no rows of A
begin; set local role authenticated; set local request.jwt.claim.sub='22222222-2222-2222-2222-222222222222';
insert into public.privacy_consents(user_id, policy_version, status) values ('11111111-1111-1111-1111-111111111111', '2026-11-13.1', 'withdrawn');
select count(*) filter (where user_id = '11111111-1111-1111-1111-111111111111') as a_rows_visible_to_b,
       count(*) filter (where user_id = '22222222-2222-2222-2222-222222222222') as b_rows from public.privacy_consents;
commit;
reset role;
select user_id, status from public.privacy_consents where user_id = '22222222-2222-2222-2222-222222222222';
\echo C5 withdrawn row with age_confirmed -> must fail (RLS)
begin; set local role authenticated; set local request.jwt.claim.sub='11111111-1111-1111-1111-111111111111';
insert into public.privacy_consents(policy_version, status, age_confirmed) values ('2026-11-13.1', 'withdrawn', true); rollback;
\echo C6 unknown status -> must fail (rejected by RLS policy and check constraint)
begin; set local role authenticated; set local request.jwt.claim.sub='11111111-1111-1111-1111-111111111111';
insert into public.privacy_consents(policy_version, status) values ('2026-11-13.1', 'deleted'); rollback;
\echo C7 users still cannot update or delete (own or others) rows
begin; set local role authenticated; set local request.jwt.claim.sub='11111111-1111-1111-1111-111111111111'; update public.privacy_consents set status = 'agreed'; rollback;
begin; set local role authenticated; set local request.jwt.claim.sub='22222222-2222-2222-2222-222222222222'; delete from public.privacy_consents; rollback;
\echo C8 anon still has no access
begin; set local role anon; select count(*) from public.privacy_consents; rollback;
\echo C9 deleting the auth user removes only the consent rows of that user (what delete-account relies on)
select count(*) as before_total from public.privacy_consents;
delete from auth.users where id = '22222222-2222-2222-2222-222222222222';
select count(*) filter (where user_id = '22222222-2222-2222-2222-222222222222') as b_left,
       count(*) filter (where user_id = '11111111-1111-1111-1111-111111111111') as a_left from public.privacy_consents;
\echo C10 unique constraint gone, index present
select count(*) as unique_constraint from pg_constraint where conname = 'privacy_consents_user_version_key';
select count(*) as index_present from pg_indexes where indexname = 'privacy_consents_user_id_id_desc_idx';
