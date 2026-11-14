\set ON_ERROR_STOP 0
\echo T1 user A inserts own consent (spoofing user_id of B) -> stored as A
begin; set local role authenticated; set local request.jwt.claim.sub='11111111-1111-1111-1111-111111111111';
insert into public.privacy_consents(user_id,policy_version,consented_at) values ('22222222-2222-2222-2222-222222222222','2026-11-13','2000-01-01');
select user_id, policy_version, status, consented_at > now()-interval '1 min' as server_time from public.privacy_consents; commit;
\echo T2 user B sees nothing of A
begin; set local role authenticated; set local request.jwt.claim.sub='22222222-2222-2222-2222-222222222222'; select count(*) as b_sees from public.privacy_consents; commit;
\echo T3 user A update -> must fail
begin; set local role authenticated; set local request.jwt.claim.sub='11111111-1111-1111-1111-111111111111'; update public.privacy_consents set status='withdrawn'; rollback;
\echo T4 user A delete -> must fail
begin; set local role authenticated; set local request.jwt.claim.sub='11111111-1111-1111-1111-111111111111'; delete from public.privacy_consents; rollback;
\echo T5 insert status withdrawn -> must fail
begin; set local role authenticated; set local request.jwt.claim.sub='11111111-1111-1111-1111-111111111111'; insert into public.privacy_consents(policy_version,status) values ('2026-12-01','withdrawn'); rollback;
\echo T6 duplicate version -> unique violation
begin; set local role authenticated; set local request.jwt.claim.sub='11111111-1111-1111-1111-111111111111'; insert into public.privacy_consents(policy_version) values ('2026-11-13'); rollback;
\echo T7 anon select/insert -> must fail
begin; set local role anon; select * from public.privacy_consents; rollback;
begin; set local role anon; insert into public.privacy_consents(user_id,policy_version) values ('11111111-1111-1111-1111-111111111111','2026-11-13'); rollback;
\echo T8 authenticated without jwt sub -> insert fails
begin; set local role authenticated; insert into public.privacy_consents(user_id,policy_version) values ('11111111-1111-1111-1111-111111111111','2026-11-14'); rollback;
\echo T9 anon/authenticated cannot call RPC
begin; set local role anon; select public.pypath_google_account_check('login','gsub-a',null,'ip:x','acct:x'); rollback;
begin; set local role authenticated; select public.pypath_google_account_check('login','gsub-a',null,'ip:x','acct:x'); rollback;
\echo T10 authenticated/anon cannot read private schema
begin; set local role authenticated; select * from pypath_private.signup_intents; rollback;
begin; set local role anon; select * from pypath_private.auth_check_attempts; rollback;
\echo T11 service_role RPC: login existing / login unknown / signup unknown / probe / bad mode
set role service_role;
select public.pypath_google_account_check('login','gsub-a','a@x.com','ip:1','acct:a') as login_existing;
select public.pypath_google_account_check('login','gsub-new','n@x.com','ip:1','acct:n') as login_unknown;
select public.pypath_google_account_check('signup','gsub-new','N@x.com','ip:1','acct:n') as signup_unknown;
select public.pypath_google_account_check('probe',null,null,'ip:1',null) as probe;
select public.pypath_google_account_check('hack','gsub-a',null,'ip:1',null) as badmode;
select public.pypath_google_account_check('login',null,null,'ip:1',null) as nosub;
reset role;
select count(*) as intents from pypath_private.signup_intents;
\echo T12 account rate limit: 10 allowed then blocked
set role service_role;
select (public.pypath_google_account_check('login','gsub-r','r@x.com','ip:'||g,'acct:r')->>'allowed') a, count(*) from generate_series(1,12) g group by 1 order by 1;
\echo T13 ip rate limit 30
select (public.pypath_google_account_check('login','gsub-'||g,null,'ip:same','acct:'||g)->>'allowed') a, count(*) from generate_series(1,33) g group by 1 order by 1;
reset role;
\echo T14 hook: google w/o intent -> error, with intent -> ok once, non-google -> ok
set role supabase_auth_admin;
select public.pypath_before_user_created('{"user":{"email":"z@x.com","app_metadata":{"provider":"google"}}}') as no_intent;
select public.pypath_before_user_created('{"user":{"email":"n@x.com","app_metadata":{"provider":"google"}}}') as with_intent;
select public.pypath_before_user_created('{"user":{"email":"n@x.com","app_metadata":{"provider":"google"}}}') as intent_consumed;
select public.pypath_before_user_created('{"user":{"email":"e@x.com","app_metadata":{"provider":"email"}}}') as non_google;
reset role;
\echo T15 anon cannot call hook
begin; set local role anon; select public.pypath_before_user_created('{}'); rollback;
