export const MODULE_IDS = ['computo','incidencias','nomina','dea','bobines','miralin','t-mobilitat','mapa-metro','cquadre','cinta-metrica','bustia','permisos','ascensors','pae'];
export function authorize(user, approved, action) {
  if (!user?.id) return 401;
  if (!approved) return 403;
  if (action === 'dashboard' && user.app_metadata?.bustia_admin !== true) return 403;
  return 0;
}
export function validPeriod(days) { return [7,30,90].includes(days) ? days : null; }
