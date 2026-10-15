begin;
do $$
declare uid uuid; data jsonb; n bigint;
begin
 if exists(select 1 from pg_class where oid in ('public.tmb_agent_opens'::regclass,'public.tmb_agent_launcher_presence'::regclass) and not relrowsecurity) then raise exception 'RLS missing'; end if;
 if has_table_privilege('anon','public.tmb_agent_opens','select') or has_table_privilege('authenticated','public.tmb_agent_opens','insert') or has_table_privilege('authenticated','public.tmb_agent_launcher_presence','select') then raise exception 'Client access'; end if;
 if has_function_privilege('anon','public.tmb_agent_dashboard(integer)','execute') or has_function_privilege('authenticated','public.tmb_agent_dashboard(integer)','execute') or has_function_privilege('authenticated','public.tmb_agent_heartbeat(uuid,uuid,boolean)','execute') or has_function_privilege('authenticated','public.tmb_agent_record_open(uuid,text)','execute') then raise exception 'RPC exposed'; end if;
 select id into uid from auth.users where raw_app_meta_data->>'bustia_admin'='true' limit 1;
 perform public.tmb_agent_heartbeat(uid,'00000000-0000-4000-8000-000000000001',true);
 perform public.tmb_agent_heartbeat(uid,'00000000-0000-4000-8000-000000000002',true);
 perform public.tmb_agent_heartbeat(uid,'00000000-0000-4000-8000-000000000001',false);
 select count(*) into n from public.tmb_agent_launcher_presence where user_id=uid;
 if n < 1 then raise exception 'Other tab lost'; end if;
 perform public.tmb_agent_record_open(uid,'nomina');
 data:=public.tmb_agent_dashboard(7);
 if not exists(select 1 from jsonb_array_elements(data->'stats') s where s->>'module'='nomina' and (s->>'opens')::int>=1 and (s->>'unique_users')::int>=1) then raise exception 'Aggregation failed'; end if;
 if jsonb_array_length(data->'users')<>(select count(*) from auth.users) then raise exception 'User list incomplete'; end if;
 update public.tmb_agent_launcher_presence set last_seen=now()-interval '76 seconds' where user_id=uid;
 data:=public.tmb_agent_dashboard(7);
 if exists(select 1 from jsonb_array_elements(data->'users') u where (u->>'online')::boolean) then raise exception 'Expired presence'; end if;
 if not exists(select 1 from cron.job where jobname='tmb-agent-minimal-retention' and active) then raise exception 'Retention disabled'; end if;
end $$;
set local role service_role;
select jsonb_build_object('users',jsonb_array_length(public.tmb_agent_dashboard(30)->'users'),'role',current_user,'checks','passed; transaction rolled back') as verification;
rollback;
