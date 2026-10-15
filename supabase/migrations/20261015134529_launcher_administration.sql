create table public.tmb_agent_opens (
 id bigint generated always as identity primary key,
 user_id uuid not null references auth.users(id) on delete cascade,
 module text not null check (module in ('computo','incidencias','nomina','dea','bobines','miralin','t-mobilitat','mapa-metro','cquadre','cinta-metrica','bustia')),
 opened_at timestamptz not null default now()
);
create index tmb_agent_opens_date on public.tmb_agent_opens(opened_at);
create index tmb_agent_opens_user_date on public.tmb_agent_opens(user_id,opened_at);
create table public.tmb_agent_launcher_presence (
 user_id uuid not null references auth.users(id) on delete cascade,
 tab_id uuid not null,
 last_seen timestamptz not null default now(),
 primary key(user_id,tab_id)
);
alter table public.tmb_agent_opens enable row level security;
alter table public.tmb_agent_launcher_presence enable row level security;
revoke all on public.tmb_agent_opens, public.tmb_agent_launcher_presence from public,anon,authenticated;
grant all on public.tmb_agent_opens, public.tmb_agent_launcher_presence to service_role;
grant usage,select on sequence public.tmb_agent_opens_id_seq to service_role;

create function public.tmb_agent_heartbeat(p_user uuid,p_tab uuid,p_visible boolean) returns void
language plpgsql security invoker set search_path='' as $$
begin
 if p_visible then
 insert into public.tmb_agent_launcher_presence(user_id,tab_id) values(p_user,p_tab)
 on conflict(user_id,tab_id) do update set last_seen=now();
 else delete from public.tmb_agent_launcher_presence where user_id=p_user and tab_id=p_tab;
 end if;
end $$;
create function public.tmb_agent_record_open(p_user uuid,p_module text) returns void
language plpgsql security invoker set search_path='' as $$
begin
 perform pg_advisory_xact_lock(hashtextextended(p_user::text,0));
 if (select count(*) from public.tmb_agent_opens where user_id=p_user and opened_at>now()-interval '1 minute') >= 30 then
  raise exception 'Too many events';
 end if;
 insert into public.tmb_agent_opens(user_id,module) values(p_user,p_module);
end $$;

-- A restricted definer is required to project selected Auth fields; never returns metadata or private content.
create function public.tmb_agent_dashboard(p_days integer) returns jsonb
language sql stable security definer set search_path='' as $$
with authorized_users as (
 select u.id,u.email,u.created_at,u.last_sign_in_at,
 case when exists(select 1 from public.solicitudes_acceso a where a.user_id=u.id and a.estado='aprobado') then 'aprobado'
 when exists(select 1 from public.solicitudes_acceso a where a.user_id=u.id and a.estado='pendiente') then 'pendiente'
 when exists(select 1 from public.solicitudes_acceso a where a.user_id=u.id and a.estado='rechazado') then 'rechazado'
 else 'sin solicitud' end as authorization
 from auth.users u
), presence as (
 select user_id,max(last_seen) last_seen from public.tmb_agent_launcher_presence group by user_id
), period as (
 select * from public.tmb_agent_opens
 where p_days in (7,30,90) and opened_at>=now()-make_interval(days=>p_days)
), stats as (
 select module,count(*) opens,count(distinct user_id) unique_users from period group by module
), recent as (
 select u.email,e.module,e.opened_at from period e join auth.users u on u.id=e.user_id order by e.opened_at desc,e.id desc limit 100
)
select jsonb_build_object(
 'server_time',now(),
 'users',coalesce((select jsonb_agg(jsonb_build_object('email',u.email,'created_at',u.created_at,'last_sign_in_at',u.last_sign_in_at,
 'authorization',u.authorization,'last_seen',p.last_seen,
 'online',u.authorization='aprobado' and coalesce(p.last_seen>now()-interval '75 seconds',false)) order by u.created_at desc)
 from authorized_users u left join presence p on p.user_id=u.id),'[]'::jsonb),
 'stats',coalesce((select jsonb_agg(to_jsonb(s) order by s.module) from stats s),'[]'::jsonb),
 'events',coalesce((select jsonb_agg(to_jsonb(r) order by r.opened_at desc) from recent r),'[]'::jsonb)
)
$$;
revoke all on function public.tmb_agent_heartbeat(uuid,uuid,boolean),public.tmb_agent_record_open(uuid,text),public.tmb_agent_dashboard(integer) from public,anon,authenticated;
grant execute on function public.tmb_agent_heartbeat(uuid,uuid,boolean),public.tmb_agent_record_open(uuid,text),public.tmb_agent_dashboard(integer) to service_role;

create extension if not exists pg_cron;
select cron.schedule('tmb-agent-minimal-retention','17 * * * *',
$job$delete from public.tmb_agent_opens where opened_at < now()-interval '90 days';
delete from public.tmb_agent_launcher_presence where last_seen < now()-interval '1 day';$job$);
