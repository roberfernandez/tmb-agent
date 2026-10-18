export const sourceUrl='https://tmbbcn.sharepoint.com/sites/SEEpublic/SitePages/Ascensors.aspx';
const base='https://tmbbcn.sharepoint.com/sites/SEEpublic/SEEDocuments/Ascensors/';
// Correspondència transcrita de la taula L4 de SEE, consultada el 18/10/2026.
// EE i EE2 no es fusionen: la font els associa a documents diferents.
export const procedures={
 EE:{name:'Ersce elèctric · EE',file:'X- Ersce Elèctric.pdf'},
 EE2:{name:'Ersce elèctric · EE2',file:'X- Ersce Electrics - Poble Sec L3.pdf'},
 TE1:{name:'Thyssen elèctric · TE1 (anterior a 2008)',file:'X- Thyssen Elèctric (ant2008).pdf'},
 TE2:{name:'Thyssen elèctric · TE2 (posterior a 2008)',file:'X- Thyssen Elèctric (post2008).pdf'},
 EHM:{name:'Ersce hidràulic · EHM',file:'X- Ersce Hidràulic - Maragall L4 ASC4.pdf'},
 EE7:{name:'Ersce elèctric · EE7 (Maragall)',file:'X- Ersce Elèctric - Maragall L4 ASC5.pdf'},
};
for(const p of Object.values(procedures))p.url=base+encodeURIComponent(p.file);
export const stations=[
 [413,'La Pau',['EE2']],[414,'Besòs',['EE']],[415,'Besòs Mar',['EE']],
 [416,'Maresme / Fòrum',['TE1']],[417,'Selva de Mar',['EE']],[418,'Poblenou',['EE']],
 [419,'Llacuna',['EE']],[420,'Bogatell',['EE']],[421,'Ciutadella / V. Olímpica',['EE2']],
 [422,'Barceloneta',['EE']],[423,'Jaume I',['EE2']],[424,'Urquinaona',[]],
 [425,'Passeig de Gràcia',['EE2']],[426,'Girona',['EE']],[427,'Verdaguer',[]],
 [428,'Joanic',['EE2','EE']],[429,'Alfons X',['EE2','EE']],
 [430,'Guinardó / Hosp. Sant Pau',['TE2']],[431,'Maragall',['EE2','EHM','EE7']],
 [432,'Llucmajor',['EE']],[433,'Via Júlia',['EE2','EE']],[434,'Trinitat Nova',['EE2']],
];
export function stationIndex(code){const i=stations.findIndex(s=>s[0]===Number(code));return i<0?0:i;}
// Ubicacions confirmades per l'usuari per a aquestes tres estacions.
export function procedureLocation(code,model){
 if(![428,429,433].includes(code))return '';
 return model==='EE2'?'Exterior':model==='EE'?'Andanes':'';
}
export function visibleFloors(index){const low=Math.max(0,Math.min(stations.length-5,index-2));return {low,high:low+4};}
