// Sustituye icon por la ruta relativa al archivo original cuando esté disponible.
// Para enlaces externos, usa únicamente URLs HTTPS; nunca credenciales.
export const modules = [
  { id: 'incidencias', name: 'Incidencias', initials: 'IN', description: 'Consulta y gestión de incidencias', icon: null },
  { id: 'dea', name: 'DEA', initials: 'DE', description: 'Información y recursos DEA', icon: null },
  { id: 'bobines', name: 'Bobines', initials: 'BO', description: 'Material y seguimiento de bobines', icon: null },
  { id: 'computo', name: 'Cómputo', initials: 'CO', description: 'Tu jornada y calendario laboral', icon: null },
  { id: 'miralin', name: 'Miralín', initials: 'MI', description: 'Acceso a Miralín', icon: null, externalUrl: '' },
  { id: 't-mobilitat', name: 'T-Mobilitat', initials: 'TM', description: 'Acceso a T-Mobilitat', icon: null, externalUrl: '' },
  { id: 'mapa-metro', name: 'Mapa Metro', initials: 'MM', description: 'La red de metro a mano', icon: null },
];
