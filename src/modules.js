// Sustituye icon por la ruta relativa al archivo original cuando esté disponible.
// Para enlaces externos, usa únicamente URLs HTTPS; nunca credenciales.
export const modules = [
  { id: 'incidencias', appUrl: 'https://roberfernandez.github.io/incidencias-l4/', name: 'Incidencias', initials: 'IN', description: 'Consulta y gestión de incidencias', icon: './assets/icons/incidencias-192.png' },
  { id: 'dea', appUrl: 'https://roberfernandez.github.io/dea-codi-l4/', name: 'DEA', initials: 'DE', description: 'Información y recursos DEA', icon: './assets/icons/dea-192.png' },
  { id: 'bobines', appUrl: 'https://roberfernandez.github.io/bobines/', name: 'Bobines', initials: 'BO', description: 'Material y seguimiento de bobines', icon: './assets/icons/bobines-192.png' },
  { id: 'computo', appUrl: 'https://roberfernandez.github.io/computo-aac/', name: 'Cómputo', initials: 'CO', description: 'Tu jornada y calendario laboral', icon: './assets/icons/computo-192.png' },
  { id: 'miralin', name: 'Miralín', initials: 'MI', description: 'Acceso a Miralín', icon: './assets/icons/miralin-192.png', externalUrl: '' },
  { id: 't-mobilitat', name: 'T-Mobilitat', initials: 'TM', description: 'Acceso a T-Mobilitat', icon: null, externalUrl: '' },
  { id: 'mapa-metro', name: 'Mapa Metro', initials: 'MM', description: 'La red de metro a mano', icon: null },
];
