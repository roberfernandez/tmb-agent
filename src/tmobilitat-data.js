import { source, tableCases } from './tmobilitat-cases.js';
export { source };
export const categories = {
  peatge:'Línia de peatge', bescanvis:'Bescanvis', compres:'Compres i recàrregues',
  gestions:'Gestions administratives', alta:'Alta i suports', activacio:'Activació i PIN',
  contingencies:'Mòbil i contingències', contactes:'Contactes', caducitat:'Caducitat',
};
const circuit=(id,title,category,page,text,extra={})=>({id,title,category,page,text,kind:'circuit',type:'Segons el cas',solution:'Segons el circuit',notes:[],related:[],...extra});
const circuits=[
  circuit('circuit-alta','ALTA I ADQUISICIÓ DEL SUPORT · CIRCUIT','alta',5,
    'Cartró: compra presencial a les DA de les estacions, sense alta al sistema.\nPVC TMB: presencial a Punt TMB amb cita prèvia, o online amb enviament per correu de 5 a 10 dies segons el diagrama.\nVirtual: TMBApp, alta online.\nPVC T-16 / T-verda: ATM; presencial a Ciutat de la Justícia (L10S) amb cita prèvia, o online amb enviament de 5 a 10 dies.\nPVC AMB: online o correu ordinari; el diagrama identifica Via Laietana 33.',
    {notes:['Terminis i canals segons la font del 23/03/2026. La fitxa d’obtenció de suport mòbil de la pàgina 3 concreta l’alta en 24–48 h.'],related:['p3-2','p3-3','p3-4','p3-5','p3-6','p3-7']}),
  circuit('circuit-pin','SOL·LICITUD I ACTIVACIÓ DEL PIN · CIRCUIT','activacio',7,
    'TMBApp: llegir el suport PVC i obtenir el PIN; el diagrama inclou l’enviament per correu electrònic. Si el suport s’ha activat amb el mòbil, el procés finalitza. Si no, introduir el PIN a la DA.\nTambé es pot sol·licitar assistència pels canals d’atenció TMB / T-mobilitat o presencialment a Punt TMB o Ciutat de la Justícia, preferiblement amb cita prèvia.',{related:['p6-1','p22-1','p22-3']}),
  circuit('circuit-compra','CÀRREGA ERRÒNIA · CIRCUIT','compres',10,
    'Si el títol ja s’ha validat: no donar bescanvi; derivar a QRS pels canals App TMB, App ATM o web TMB.\nSi no s’ha validat i s’ha carregat a Punt TMB: donar bescanvi PUNT TMB i sol·licitar devolució de compra abans de 24 h.\nSi no s’ha validat i s’ha carregat a DA, TMBApp o online: sol·licitar devolució abans de 24 h per TMBApp.',{related:['p9-1'],guide:'compra'}),
  circuit('circuit-cobrat','TÍTOL COBRAT I NO CARREGAT · CIRCUIT','compres',11,
    'Compra per TMBApp: comprovar el banc de l’usuari i l’eina de consulta. Si no hi ha cancel·lació automàtica i han passat menys de 24 h, passar el suport per una antena activa del mòbil; si continua sense carregar-se, QRS. Si no hi ha cancel·lació després de 24 h, QRS.\nCompra per DA: comprovar si hi ha cancel·lació de compra. En una cancel·lació, el diagrama diferencia targeta de crèdit (rebut de compra i de devolució), pagament mixt, bitllets i monedes (retorn de l’import introduït per la DA).\nSense cancel·lació: comprovar PVC a la DA, eina de consulta, banc de l’usuari i interior/operacions de la DA i INDRA. La decisió «Compra TC o +20 €?» deriva a QRS quan és afirmativa; si és negativa passa per «Entrega efectiu?» i, si no s’entrega, QRS.',
    {notes:['El diagrama no explicita aquí un protocol complet d’entrega manual d’efectiu. No s’ha d’interpretar «+20 €» com una autorització general per fer devolucions a l’estació. Consulteu la pàgina 11 per seguir les fletxes originals.'],related:['p8-8','p9-1']}),
  circuit('circuit-bloqueig','BLOQUEIG I DESBLOQUEIG · CIRCUIT','gestions',13,
    'A l’eina de consulta, buscar el suport pel número o document.\nPer pèrdua: seleccionar el motiu i submotiu corresponents (sol·licitud d’usuari / títol trobat). Per robatori: sol·licitud d’usuari. Seleccionar línia i estació per registrar el bloqueig.\nEl diagrama també inclou desbloquejar el suport PVC.\nEn ús fraudulent, distingir el títol del suport: la pàgina 16 especifica bloquejar només el títol afectat, informar el client i el responsable i no retirar el suport.',
    {notes:['La pàgina 13 acaba amb una casella genèrica «Bloquejar suport», també després de la branca de frau. S’aplica la precisió explícita de la pàgina 16: en frau, només el títol.'],related:['p12-4','p12-6','p12-7','p16-11','p16-12']}),
  circuit('circuit-peatge','SUPORT QUE NO VALIDA · CIRCUIT','peatge',17,
    'Provar un altre pas. Si valida, finalitzar.\nSi no valida: distingir caiguda del sistema, lectura a la DA i estat físic/garantia. El circuit de bescanvi exigeix comprovar que el títol és vàlid per viatjar; si no ho és, procés de compra.\nSuport bloquejat: desbloqueig amb acreditació.\nTítol bloquejat: abans de 48 h Punt TMB/CAI; després, CAI. T-16 només CAI.\nTítol caducat, esgotat, sense crèdit o sense càrrega activa: procés de recàrrega.\nZona no vàlida o títol no vàlid: compra de suport/títol.\nTítol ja cancel·lat: donar pas.\nSuport no activat: activació PTMOB-02.\nSuport físicament malmès o fora de garantia: no donar bescanvi; QRS.',
    {notes:['El diagrama general resumeix el bescanvi a la DA com a «només suport PVC», però la pàgina 20 també regula el cartró CTL. Consulteu la fitxa específica abans de decidir. La caiguda de sistema té la resposta detallada a la pàgina 16.'],related:['p16-5','p20-5','p6-1','p16-12']}),
  circuit('circuit-mobil','MÒBIL QUE NO VALIDA · CIRCUIT','contingencies',18,
    'Provar un altre pas i reiniciar el dispositiu si escau. Si valida, finalitzar.\nCaiguda global de l’app T-mobilitat: bescanvi T-usual magnètica codi 404, amb validesa de dos dies.\nSi no hi ha caiguda global: comprovar amb Consultes T-mobilitat que la persona té un títol integrat vàlid. Si no en té, no donar bescanvi. Si en té, bescanvi T-usual magnètica codi 404 de dos dies i indicar els canals d’atenció o Punt TMB per resoldre definitivament la incidència.\nLa pàgina 16 afegeix que cal indicar al rebut de la T-usual magnètica el número de suport de la persona usuària.',
    {related:['p16-6','p16-7'],guide:'mobil'}),
  circuit('circuit-bescanvi','SUPORT DEFECTUÓS · CIRCUIT DE BESCANVI','bescanvis',21,
    'Comprovar l’estat físic: si està malmès, no té dret a bescanvi per aquesta via.\nPVC AMB: no retirar l’original; duplicat online/presencial.\nPVC ATM: no retirar l’original; duplicat Punt TMB/online (la taula de la pàgina 20 també cita CAI).\nT-16 / T-verda: no retirar l’original; duplicat CAI-ATM.\nCartró: retirar el suport defectuós i tramitar sobre de bescanvis.\nIntroduir el número de suport a la DA i comprovar que emet el cartró. Si no l’emet, consultar el missatge d’error. El bloqueig automàtic de la DA consta com a diferit al diagrama.',
    {related:['p20-1','p20-2','p20-3','p20-5','p20-6','error-da-1','error-da-2','error-da-3','error-da-4','error-da-5']}),
];
const errors=[
  ['SUPORT NO TROBAT O NO VÀLID','Error del número introduït.'],
  ['ERROR DE COMUNICACIÓ AMB EL SERVIDOR','Provar una altra DA.'],
  ['SUPORT NO DISPONIBLE PER EMISSIÓ','No té bobina carregada o són suports d’altres operadors.'],
  ['SUPORT FORA DE GARANTIA','No es fa bescanvi.'],
  ['SUPORT ANUL·LAT','Suport ja bescanviat o anul·lat per altres raons.'],
].map(([title,text],i)=>circuit(`error-da-${i+1}`,title,'bescanvis',21,text,{kind:'error-da',type:'Error de bescanvi a la DA',solution:'Segons el missatge',related:['circuit-bescanvi']}));
const support=(id,title,text)=>circuit(id,title,'alta',2,text,{notes:['Informació de la taula de suports del document 3.0; el peu d’aquesta pàgina conserva V1.8. La pàgina 3 també descriu iOS. Imports segons la font del març de 2026.']});
const supports=[
  support('suport-pvc','CARACTERÍSTIQUES · PVC ATM','Preu: 4,50 €. Garantia: 5 anys. Nominatiu, bloquejable i amb recuperació del títol. Traspàs entre PVC i cartró. Capacitat: dos títols, una càrrega latent per cadascun i un títol actiu. Compatibilitat detallada a les fitxes de càrrega de la pàgina 8.'),
  support('suport-monotitol','CARACTERÍSTIQUES · PVC MONOTÍTOL','Preu: 4,50 €. Garantia: 5 anys. Nominatiu, bloquejable i amb recuperació del títol. Un únic títol associat. Inclou acompanyant, T-16, Metropolitana gratuïta/reduïda i T-verda. Duplicat provisional de PVC monotítol a cartró monotítol.'),
  support('suport-virtual','CARACTERÍSTIQUES · VIRTUAL','La taula de la pàgina 2 identifica Android: 1 €, sense garantia ni bloqueig segons aquesta taula, nominatiu i recuperable. Cinc títols, cinc càrregues latents i un títol actiu. Traspàs únicament entre mòbils. La pàgina 3 desenvolupa també el funcionament iOS; consulteu la fitxa d’obtenció de suport mòbil.'),
  support('suport-cartro','CARACTERÍSTIQUES · CARTRÓ ANÒNIM','Preu: 1 €. Garantia: 6 mesos. No nominatiu, no bloquejable i sense recuperació del títol segons la taula. Un títol i una càrrega latent. Traspàs de cartró a cartró. Compatibilitat detallada a la pàgina 8.'),
  support('suport-usual','CARACTERÍSTIQUES · CARTRÓ T-USUAL','Preu: 1 €. Garantia: 6 mesos. Nominatiu, no bloquejable i sense recuperació del títol segons la taula. T-usual, amb una càrrega en ús i una latent. Traspàs de cartró a cartró.'),
];
const shortTitles={'p8-1':'CÀRREGA AL SUPORT PVC','p8-2':'CÀRREGA AL SUPORT MÒBIL','p8-3':'CÀRREGA AL CARTRÓ NO NOMINATIU','p8-4':'CÀRREGA AL CARTRÓ T-USUAL','p8-5':'CÀRREGA AL PVC MONOTÍTOL','p20-2':'BESCANVI · PVC T-16 A CARTRÓ CTL','p20-3':'BESCANVI · METROPOLITANA I ACOMPANYANT','p20-6':'BESCANVI · PVC T-VERDA A CARTRÓ CTL'};
const expiry=circuit('caducitat-provisional','CADUCITAT · METROPOLITANA I ACOMPANYANT','caducitat',20,
  'En el bescanvi de PVC Metropolitana gratuïta/reduïda o acompanyant a cartró CTL, la pàgina 20 estableix 30 dies de caducitat. La Metropolitana reduïda és recarregable. El bescanvi s’emet automàticament a la DA; comprovar la data del suport emès.',
  {calculator:true,notes:['El càlcul mostra 30 dies comptant la data d’emissió com a primer dia. No s’aplica a qualsevol suport ni amplia la validesa del títol original. La data emesa per la DA és la referència operativa.'],related:['p20-3']});
export const cases=[...tableCases.map(c=>({...c,title:shortTitles[c.id]||c.title,solution:c.solution.replace(/tmb/g,'TMB'),category:['p16-6','p16-7'].includes(c.id)?'contingencies':c.category,related:c.id==='p20-3'?['caducitat-provisional','circuit-bescanvi']:c.related})),...circuits,...errors,...supports,expiry];
export function normalize(text){return String(text).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('ca').replace(/[^a-z0-9]+/g,' ').trim();}
export function filterCases({query='',category='',type=''}={}){
  const words=normalize(query).split(' ').filter(Boolean);
  return cases.filter(c=>(!category||c.category===category)&&(!type||normalize(c.type).includes(type))&&words.every(w=>normalize([c.title,c.originalTitle,c.text,c.type,c.solution,...c.notes].join(' ')).includes(w)));
}
export function expiryDate(start,days=30){
  if(!/^\d{4}-\d{2}-\d{2}$/.test(start)||!Number.isInteger(days)||days<1) return null;
  const date=new Date(`${start}T12:00:00Z`);
  if(!Number.isFinite(date.getTime())||date.toISOString().slice(0,10)!==start)return null;
  date.setUTCDate(date.getUTCDate()+days-1);return date.toISOString().slice(0,10);
}
// Branches retain the preconditions of the two source diagrams.
export const guides={
  mobil:{page:18,start:'pas',nodes:{
    pas:{question:'Valida en un altre pas, reiniciant el dispositiu si escau?',options:[['Sí','fi'],['No','global']]},
    fi:{result:'Si ja valida, finalitzar el circuit.'},
    global:{question:'Hi ha una caiguda global de l’app T-mobilitat?',options:[['Sí','bescanvi-global'],['No','valid']]},
    'bescanvi-global':{result:'Donar bescanvi T-usual magnètica codi 404, amb validesa de dos dies.'},
    valid:{question:'Consultes confirma que disposa d’un títol integrat vàlid?',options:[['Sí','bescanvi'],['No','cap']]},
    bescanvi:{result:'Donar bescanvi T-usual magnètica codi 404 de dos dies. Segons la pàgina 16, anotar el número de suport al rebut. Indicar els canals d’atenció o Punt TMB per solucionar definitivament la incidència.'},
    cap:{result:'No donar bescanvi per aquest circuit.'},
  }},
  compra:{page:10,start:'validat',nodes:{
    validat:{question:'El títol carregat erròniament ja s’ha validat?',options:[['Sí','qrs'],['No','canal']]},
    qrs:{result:'No donar bescanvi. Derivar a QRS per App TMB, App ATM o web TMB.'},
    canal:{question:'On s’ha carregat el títol?',options:[['Punt TMB','punt'],['DA, TMBApp o online','online']]},
    punt:{result:'Donar bescanvi PUNT TMB i sol·licitar devolució de compra abans de 24 h, segons el diagrama. Consulteu les condicions de devolució de la pàgina 9.'},
    online:{result:'Sol·licitar devolució de compra abans de 24 h per TMBApp, segons el diagrama. Consulteu les condicions de devolució de la pàgina 9.'},
  }},
};
