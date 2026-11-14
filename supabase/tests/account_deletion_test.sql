-- Local PostgreSQL checks for 20261115000000_pypath_account_deletion.sql.
-- Run AFTER local_supabase_stub.sql and all earlier migrations/tests. Plain PostgreSQL with stubbed
-- Supabase roles and a stand-in user_cloud_state table: NOT a real Supabase test.
\echo D0 setup: stand-in user_cloud_state (the real table is not created by these migrations), rows for A and B
create table if not exists public.user_cloud_state(user_id uuid primary key, revision bigint not null default 1);
insert into public.user_cloud_state(user_id) values ('11111111-1111-1111-1111-111111111111'), ('22222222-2222-2222-2222-222222222222') on conflict do nothing;
insert into pypath_private.signup_intents values
  (encode(sha256(convert_to('a@x.com','UTF8')),'hex'), 'h', now() + interval '10 minutes'),
  (encode(sha256(convert_to('b@x.com','UTF8')),'hex'), 'h', now() + interval '10 minutes')
on conflict do nothing;
insert into auth.users(id) values ('11111111-1111-1111-1111-111111111111'), ('22222222-2222-2222-2222-222222222222') on conflict do nothing;
insert into public.privacy_consents(user_id, policy_version, status) values
  ('11111111-1111-1111-1111-111111111111', '2026-11-13.2', 'agreed'),
  ('11111111-1111-1111-1111-111111111111', '2026-11-13.2', 'withdrawn'),
  ('22222222-2222-2222-2222-222222222222', '2026-11-13.2', 'agreed');
select (select count(*) from public.user_cloud_state) as cloud_rows,
       (select count(*) from public.privacy_consents where user_id = '11111111-1111-1111-1111-111111111111') as a_consents,
       (select count(*) from public.privacy_consents where user_id = '22222222-2222-2222-2222-222222222222') as b_consents,
       (select count(*) from pypath_private.signup_intents) as intents;
\echo D1 authenticated user A cannot call the deletion function -> must fail (permission denied)
begin; set local role authenticated; set local request.jwt.claim.sub='11111111-1111-1111-1111-111111111111';
select public.pypath_delete_account_data('22222222-2222-2222-2222-222222222222'); rollback;
\echo D2 anon cannot call it -> must fail (permission denied)
begin; set local role anon; select public.pypath_delete_account_data('11111111-1111-1111-1111-111111111111'); rollback;
\echo D3 service_role deletes ONLY user A rows (cloud, consents, sign-up intent)
begin; set local role service_role;
select public.pypath_delete_account_data('11111111-1111-1111-1111-111111111111') as result;
commit;
select (select count(*) from public.user_cloud_state where user_id = '11111111-1111-1111-1111-111111111111') as a_cloud,
       (select count(*) from public.privacy_consents where user_id = '11111111-1111-1111-1111-111111111111') as a_consents,
       (select count(*) from pypath_private.signup_intents where email_hash = encode(sha256(convert_to('a@x.com','UTF8')),'hex')) as a_intents,
       (select count(*) from public.user_cloud_state where user_id = '22222222-2222-2222-2222-222222222222') as b_cloud,
       (select count(*) from public.privacy_consents where user_id = '22222222-2222-2222-2222-222222222222') as b_consents,
       (select count(*) from pypath_private.signup_intents where email_hash = encode(sha256(convert_to('b@x.com','UTF8')),'hex')) as b_intents;
\echo D4 retry is harmless (already deleted -> zero rows, no error)
begin; set local role service_role; select public.pypath_delete_account_data('11111111-1111-1111-1111-111111111111') as result; commit;
\echo D5 null user id -> must fail
begin; set local role service_role; select public.pypath_delete_account_data(null); rollback;
\echo D6 Auth user A can now be deleted (no PyPath row blocks it)
delete from auth.identities where user_id = '11111111-1111-1111-1111-111111111111';
delete from auth.users where id = '11111111-1111-1111-1111-111111111111';
select count(*) as a_users_left from auth.users where id = '11111111-1111-1111-1111-111111111111';
\echo D7 account check for the deleted Google identity now answers exists=false
begin; set local role service_role;
select public.pypath_google_account_check('login', 'gsub-a', 'a@x.com', 'ip:d7', 'acct:d7') as check_after_delete; rollback;
