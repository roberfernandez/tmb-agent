import { createClient } from "npm:@supabase/supabase-js@2.99.1";
import { authorize, validPeriod, MODULE_IDS } from "./policy.mjs";
const cors = { "Access-Control-Allow-Origin": "https://roberfernandez.github.io", "Access-Control-Allow-Headers": "authorization, apikey, content-type", "Access-Control-Allow-Methods": "POST, OPTIONS", "Cache-Control": "no-store" };
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
Deno.serve(async req => {
  if (req.method === "OPTIONS") return new Response(null, { headers: cors });
  if (req.method !== "POST") return reply({ error: "Mètode no permès" }, 405);
  try {
    const token = req.headers.get("Authorization")?.match(/^Bearer (\S+)$/i)?.[1];
    if (!token) return reply({ error: "Sessió necessària" }, 401);
    let secret = Deno.env.get("SUPABASE_SECRET_KEYS") || Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    try { const keys = JSON.parse(secret!); secret = keys.default ?? keys.secret ?? Object.values(keys)[0]; } catch {}
    const db = createClient(Deno.env.get("SUPABASE_URL")!, secret!, { auth: { persistSession: false, autoRefreshToken: false } });
    const { data: { user }, error } = await db.auth.getUser(token);
    if (error || !user) return reply({ error: "Sessió no vàlida" }, 401);
    const approval = await db.from("solicitudes_acceso").select("id").eq("user_id", user.id).eq("estado", "aprobado").limit(1);
    if (approval.error) throw approval.error;
    let body;
    try { body = await req.json(); } catch { return reply({ error: "JSON no vàlid" }, 400); }
    const status = authorize(user, Boolean(approval.data?.length), body.action);
    if (status) return reply({ error: "Accés denegat" }, status);
    const run = async (name: string, args: Record<string, unknown> = {}) => {
      const result = await db.rpc(name, args);
      if (result.error) throw result.error;
      return result.data;
    };
    if (body.action === "capabilities") return reply({ ok: true, can_manage: user.app_metadata?.bustia_admin === true });
    if (body.action === "heartbeat" || body.action === "leave") {
      if (typeof body.tab !== "string" || !/^[0-9a-f-]{36}$/i.test(body.tab)) return reply({ error: "Pestanya no vàlida" }, 400);
      await run("tmb_agent_heartbeat", { p_user: user.id, p_tab: body.tab, p_visible: body.action === "heartbeat" });
      return reply({ ok: true });
    }
    if (body.action === "open") {
      if (!MODULE_IDS.includes(body.module)) return reply({ error: "MiniApp no vàlida" }, 400);
      await run("tmb_agent_record_open", { p_user: user.id, p_module: body.module });
      return reply({ ok: true });
    }
    if (body.action === "dashboard") {
      const period = validPeriod(body.days);
      if (!period) return reply({ error: "Període no vàlid" }, 400);
      return reply({ ok: true, ...await run("tmb_agent_dashboard", { p_days: period }) });
    }
    return reply({ error: "Acció no vàlida" }, 400);
  } catch { return reply({ error: "Servei temporalment no disponible" }, 503); }
});

