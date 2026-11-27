const BASE = 'https://hhenkvendzengggrgook.supabase.co';
const PUBLIC_KEY = 'sb_publishable_QSPDTmh3fd0FH-VvAjH5KQ_Rfvzr2x_';
export const SESSION_KEY = 'sb-hhenkvendzengggrgook-auth-token';
function readSession(storage) { try { return JSON.parse(storage.getItem(SESSION_KEY)); } catch { return null; } }
export function loginUrl(location) { return `/tmb-agent/auth/?returnTo=${encodeURIComponent(location.pathname + location.hash)}`; }
export async function checkLauncherAccess(storage = localStorage, request = fetch) {
  const session = readSession(storage);
  if (!session?.access_token || !Number.isFinite(session.expires_at) || session.expires_at * 1000 <= Date.now()) return null;
  const response = await request(`${BASE}/functions/v1/tmb-agent-admin`, {
    method: 'POST', cache: 'no-store', signal: AbortSignal.timeout(12000),
    headers: { apikey: PUBLIC_KEY, Authorization: `Bearer ${session.access_token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ action: 'capabilities' }),
  });
  if (response.status === 401 || response.status === 403) return null;
  if (!response.ok) throw new Error('Servei d’accés no disponible');
  const data = await response.json();
  if (data?.ok !== true || typeof data.can_manage !== 'boolean') throw new Error('Resposta d’accés invàlida');
  try { const latest = JSON.parse(storage.getItem(SESSION_KEY)); if (latest?.access_token !== session.access_token) return null; } catch { return null; }
  return data;
}
