import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: Record<string, unknown>, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function clean(value: unknown, max: number) {
  if (typeof value !== "string") return "";
  return value.trim().replace(/\s+/g, " ").slice(0, max);
}

function serviceKey(raw: string | undefined) {
  if (!raw) return undefined;
  try {
    const parsed = JSON.parse(raw);
    const value = parsed.default ?? parsed.secret ?? Object.values(parsed)[0];
    return typeof value === "string" ? value : undefined;
  } catch {
    return raw;
  }
}

async function sendTelegram(token: string, chatId: string, text: string) {
  const response = await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ chat_id: chatId, text }),
  });
  const data = await response.json().catch(() => ({}));
  return Boolean(response.ok && data?.ok);
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, error: "Método no permitido" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const publishableKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
  const secretKey = serviceKey(Deno.env.get("SUPABASE_SECRET_KEYS"));
  const telegramToken = Deno.env.get("TELEGRAM_BOT_TOKEN");
  const adminChatId = Deno.env.get("TELEGRAM_ADMIN_CHAT_ID");
  const authHeader = req.headers.get("Authorization");

  if (!supabaseUrl || !publishableKey || !secretKey || !authHeader) {
    return json({ ok: false, error: "Configuración o sesión no disponible" }, 500);
  }

  const userClient = createClient(supabaseUrl, publishableKey, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: { user }, error: userError } = await userClient.auth.getUser();
  if (userError || !user?.id || !user.email) {
    return json({ ok: false, error: "Usuario no autenticado" }, 401);
  }

  const admin = createClient(supabaseUrl, secretKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: approved, error: approvedError } = await admin
    .from("solicitudes_acceso")
    .select("id")
    .eq("user_id", user.id)
    .eq("estado", "aprobado")
    .limit(1)
    .maybeSingle();

  if (approvedError || !approved) {
    return json({ ok: false, error: "Usuario no autorizado" }, 403);
  }

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ ok: false, error: "JSON no válido" }, 400);
  }

  const action = clean(body.action, 32);
  const email = user.email.trim().toLowerCase();
  // getUser() reads server-managed metadata; never trust body/user_metadata.
  const canManage = user.app_metadata?.bustia_admin === true;

  if (action === "set_status") {
    if (!canManage) return json({ ok: false, error: "Només l’administrador pot gestionar peticions" }, 403);
    const propostaId = body.proposta_id;
    const estat = body.estat;
    if (!Number.isSafeInteger(propostaId) || Number(propostaId) <= 0 ||
        typeof estat !== "string" || !["oberta", "en_estudi", "acceptada", "en_desenvolupament", "feta", "descartada"].includes(estat)) {
      return json({ ok: false, error: "Petició o estat no vàlid" }, 400);
    }
    const { data: proposal, error } = await admin.from("tmb_agent_propostes")
      .update({ estat }).eq("id", propostaId).select("id,estat").maybeSingle();
    if (error) return json({ ok: false, error: "No s’ha pogut desar l’estat" }, 500);
    if (!proposal) return json({ ok: false, error: "La petició no existeix" }, 404);
    return json({ ok: true, proposta: proposal });
  }

  if (action === "list") {
    const [{ data: proposals, error: proposalError }, { data: votes, error: voteError }] = await Promise.all([
      admin
        .from("tmb_agent_propostes")
        .select("id,created_at,categoria,modul,titol,detall,estat")
        .order("created_at", { ascending: false }),
      admin
        .from("tmb_agent_vots")
        .select("proposta_id,user_id"),
    ]);
    if (proposalError || voteError) {
      console.error(proposalError ?? voteError);
      return json({ ok: false, error: "No se pudieron cargar las peticiones" }, 500);
    }

    const counts = new Map<number, number>();
    const mine = new Set<number>();
    for (const row of votes ?? []) {
      const id = Number(row.proposta_id);
      counts.set(id, (counts.get(id) ?? 0) + 1);
      if (row.user_id === user.id) mine.add(id);
    }

    const items = (proposals ?? []).map((p) => ({
      ...p,
      vots: counts.get(Number(p.id)) ?? 0,
      meu_vot: mine.has(Number(p.id)),
    })).sort((a, b) => {
      const activeA = ["oberta", "en_estudi", "acceptada", "en_desenvolupament"].includes(a.estat) ? 0 : 1;
      const activeB = ["oberta", "en_estudi", "acceptada", "en_desenvolupament"].includes(b.estat) ? 0 : 1;
      if (activeA !== activeB) return activeA - activeB;
      if (b.vots !== a.vots) return b.vots - a.vots;
      return String(b.created_at).localeCompare(String(a.created_at));
    });

    return json({ ok: true, propostes: items, can_manage: canManage });
  }

  if (action === "message") {
    const tipus = clean(body.tipus, 16);
    const modul = clean(body.modul, 80) || null;
    const missatge = typeof body.missatge === "string" ? body.missatge.trim().slice(0, 2000) : "";
    if (!["error", "consulta"].includes(tipus) || missatge.length < 3) {
      return json({ ok: false, error: "Missatge no vàlid" }, 400);
    }

    const label = tipus === "error" ? "🐞 ERROR DETECTAT" : "❓ NOVA CONSULTA";
    const telegramOk = telegramToken && adminChatId
      ? await sendTelegram(
          telegramToken,
          adminChatId,
          `🚇 TMB Agent · Bústia\n\n${label}\n\n📧 ${email}\n📱 Mòdul: ${modul ?? "General"}\n\n${missatge}`,
        )
      : false;

    if (!telegramOk) {
      return json({ ok: false, error: "No s'ha pogut enviar l'avís per Telegram" }, 502);
    }

    return json({ ok: true, telegram: true });
  }

  if (action === "proposal") {
    const categoria = clean(body.categoria, 24);
    const modul = clean(body.modul, 80) || null;
    const titol = clean(body.titol, 140);
    const detall = typeof body.detall === "string" ? body.detall.trim().slice(0, 1200) : "";
    if (!["millora", "nova_miniapp", "nova_funcio", "altres"].includes(categoria) || titol.length < 3) {
      return json({ ok: false, error: "Petició no vàlida" }, 400);
    }

    const { data: proposal, error } = await admin
      .from("tmb_agent_propostes")
      .insert({
        created_by: user.id,
        categoria,
        modul,
        titol,
        detall: detall || null,
      })
      .select("id,titol")
      .single();
    if (error || !proposal) {
      console.error(error);
      return json({ ok: false, error: "No s'ha pogut crear la petició" }, 500);
    }

    const { error: voteError } = await admin
      .from("tmb_agent_vots")
      .insert({ proposta_id: proposal.id, user_id: user.id });
    if (voteError) console.error(voteError);

    const telegramOk = telegramToken && adminChatId
      ? await sendTelegram(
          telegramToken,
          adminChatId,
          `🚇 TMB Agent · Bústia\n\n💡 NOVA PETICIÓ\n\n📧 ${email}\n🏷️ Categoria: ${categoria}\n📱 Mòdul: ${modul ?? "General"}\n🆔 PET-${proposal.id}\n👥 1 suport\n\n${titol}${detall ? `\n\n${detall}` : ""}`,
        )
      : false;

    return json({ ok: true, id: proposal.id, telegram: telegramOk });
  }

  if (action === "vote" || action === "unvote") {
    const propostaId = Number(body.proposta_id);
    if (!Number.isInteger(propostaId) || propostaId <= 0) {
      return json({ ok: false, error: "Petició no vàlida" }, 400);
    }

    const { data: proposal, error: proposalError } = await admin
      .from("tmb_agent_propostes")
      .select("id,titol,estat")
      .eq("id", propostaId)
      .maybeSingle();
    if (proposalError || !proposal) {
      return json({ ok: false, error: "La petició no existeix" }, 404);
    }
    if (proposal.estat === "feta") {
      return json({ ok: false, error: "La petició ja està feta i no admet canvis de suport" }, 409);
    }

    let changed = false;
    if (action === "vote") {
      const { error } = await admin
        .from("tmb_agent_vots")
        .insert({ proposta_id: propostaId, user_id: user.id });
      if (!error) changed = true;
      else if (error.code !== "23505") {
        console.error(error);
        return json({ ok: false, error: "No s'ha pogut registrar el suport" }, 500);
      }
    } else {
      const { data, error } = await admin
        .from("tmb_agent_vots")
        .delete()
        .eq("proposta_id", propostaId)
        .eq("user_id", user.id)
        .select("proposta_id");
      if (error) {
        console.error(error);
        return json({ ok: false, error: "No s'ha pogut retirar el suport" }, 500);
      }
      changed = Boolean(data?.length);
    }

    const { count } = await admin
      .from("tmb_agent_vots")
      .select("proposta_id", { count: "exact", head: true })
      .eq("proposta_id", propostaId);
    const total = count ?? 0;

    let telegramOk = true;
    if (changed && telegramToken && adminChatId) {
      const label = action === "vote" ? "👍 NOU SUPORT" : "↩️ SUPORT RETIRAT";
      telegramOk = await sendTelegram(
        telegramToken,
        adminChatId,
        `🚇 TMB Agent · Bústia\n\n${label}\n\n📧 ${email}\n🆔 PET-${propostaId}\n👥 ${total} suport${total === 1 ? "" : "s"}\n\n${proposal.titol}`,
      );
    }

    return json({ ok: true, changed, vots: total, meu_vot: action === "vote", telegram: telegramOk });
  }

  return json({ ok: false, error: "Acció no vàlida" }, 400);
});
