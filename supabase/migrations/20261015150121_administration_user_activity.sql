create or replace function public.tmb_agent_dashboard(p_days integer) returns jsonb
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
), user_modules as (
 select user_id,module,count(*) opens,max(opened_at) last_open from period group by user_id,module
), activity as (
 select u.email,sum(m.opens)::bigint opens,max(m.last_open) last_open,
 jsonb_agg(jsonb_build_object('module',m.module,'opens',m.opens,'last_open',m.last_open) order by m.last_open desc) modules
 from user_modules m join auth.users u on u.id=m.user_id group by u.id,u.email
), stats as (
 select module,count(*) opens,count(distinct user_id) unique_users from period group by module
), recent as (
 select u.email,e.module,e.opened_at from period e join auth.users u on u.id=e.user_id order by e.opened_at desc,e.id desc limit 100
)
select jsonb_build_object(
 'server_time',now(),
 'activity',coalesce((select jsonb_agg(to_jsonb(a) order by a.last_open desc) from activity a),'[]'::jsonb),
 'users',coalesce((select jsonb_agg(jsonb_build_object('email',u.email,'created_at',u.created_at,'last_sign_in_at',u.last_sign_in_at,
 'authorization',u.authorization,'last_seen',p.last_seen,
 'online',u.authorization='aprobado' and coalesce(p.last_seen>now()-interval '75 seconds',false)) order by u.created_at desc)
 from authorized_users u left join presence p on p.user_id=u.id),'[]'::jsonb),
 'stats',coalesce((select jsonb_agg(to_jsonb(s) order by s.module) from stats s),'[]'::jsonb),
 'events',coalesce((select jsonb_agg(to_jsonb(r) order by r.opened_at desc) from recent r),'[]'::jsonb)
)
$$;
