import { checkLauncherAccess, SESSION_KEY, loginUrl } from './launcher-access.js';
export { SESSION_KEY, loginUrl };
export async function checkSession(storage = localStorage, request = fetch) { return await checkLauncherAccess(storage, request) ? 'approved' : 'login'; }
export function accessState(rows) { for (const state of ['aprobado', 'pendiente', 'rechazado']) { if (rows.some(row => row.estado === state)) return state; } return null; }
