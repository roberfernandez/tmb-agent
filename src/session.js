// Same project and web storage key used by supabase_flutter in Incidencias.
export const SESSION_KEY = 'sb-hhenkvendzengggrgook-auth-token';
const URL = 'https://hhenkvendzengggrgook.supabase.co';
const PUBLIC_KEY = 'sb_publishable_QSPDTmh3fd0FH-VvAjH5KQ_Rfvzr2x_';

// Preserve the original precedence in AccesoScreen and solicitar-acceso.
export function accessState(rows) {
  for (const state of ['aprobado', 'pendiente', 'rechazado']) {
    if (rows.some(row => row.estado === state)) return state;
  }
  return null;
}

export async function checkSession(storage = localStorage, request = fetch) {
  let session;
  try { session = JSON.parse(storage.getItem(SESSION_KEY)); } catch { return 'login'; }
  if (!session?.access_token || !Number.isFinite(session.expires_at) ||
      session.expires_at * 1000 <= Date.now()) return 'login';
  const headers = { apikey: PUBLIC_KEY, Authorization: `Bearer ${session.access_token}` };
  const options = { headers, cache: 'no-store', signal: AbortSignal.timeout(15000) };
  const userResponse = await request(`${URL}/auth/v1/user`, options);
  if (userResponse.status === 401 || userResponse.status === 403) return 'login';
  if (!userResponse.ok) throw new Error('Access check unavailable');
  const user = await userResponse.json();
  if (!user?.id) return 'login';
  const params = new URLSearchParams({select:'estado', user_id:`eq.${user.id}`, order:'created_at.desc'});
  const result = await request(`${URL}/rest/v1/solicitudes_acceso?${params}`, options);
  if (result.status === 401 || result.status === 403) return 'login';
  if (!result.ok) throw new Error('Authorization check unavailable');
  const rows = await result.json();
  if (!Array.isArray(rows)) throw new Error('Invalid authorization response');
  // Never accept a user ID, approval flag or state from query strings/referrer.
  if (storage.getItem(SESSION_KEY) !== JSON.stringify(session)) {
    let latest;
    try { latest = JSON.parse(storage.getItem(SESSION_KEY)); } catch { return 'login'; }
    if (latest?.access_token !== session.access_token) return 'login';
  }
  return accessState(rows) === 'aprobado' ? 'approved' : 'login';
}

export function loginUrl(location) {
  return `/tmb-agent/auth/?returnTo=${encodeURIComponent(location.pathname + location.hash)}`;
}
