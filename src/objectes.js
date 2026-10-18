const PUBLIC_URL='https://www.tmb.cat/ca/atencio-al-client/gestions/objectes-perduts';
const PROCEDURE_URL='https://tmbbcn.sharepoint.com/:p:/r/sites/LC/_layouts/15/Doc.aspx?sourcedoc=%7B925F1C87-3495-44E8-9FC3-205A6C40813F%7D&file=2_PECA_Objetos_perdidos_publicacion.ppt&action=edit&mobileredirect=true';
export function renderObjectes(){
 const root=document.createElement('section');root.className='op-app';
 root.innerHTML=`<h1>Objectes perduts</h1>
 <img class="op-hero" src="./assets/objectes/objectes-400.webp" alt="Cartera amb una targeta T-mobilitat i un mòbil">
 <section aria-labelledby="op-agent"><h2 id="op-agent">Procediment de l’agent</h2>
 <a class="op-action" href="${PROCEDURE_URL}" target="_blank" rel="noopener noreferrer"><strong>Obre el procediment oficial ↗</strong><small>Instrucció AAC I i II · lectura i esquema d’actuació</small></a>
 <p class="op-note">Dues pàgines d’instruccions després de la portada. Canvia de diapositiva i amplia-la al visor de TMB. Requereix accés corporatiu.</p>
 <div class="op-contacts"><span>CCM · <strong>19493</strong></span><span>CSPC · <strong>88038</strong></span></div>
 </section>
 <section aria-labelledby="op-tramit"><h2 id="op-tramit">Tràmit Objecte perdut</h2>
 <a class="op-action op-primary" href="${PUBLIC_URL}" target="_blank" rel="noopener noreferrer"><strong>Tràmit Objecte perdut ↗</strong><small>Formulari públic de TMB per al viatger</small></a>
 <button class="op-action" id="op-show-qr" type="button" aria-expanded="false" aria-controls="op-qr">Mostra el QR al viatger</button>
 <div id="op-qr" class="op-qr" hidden><strong>Escaneja amb el teu mòbil</strong><img src="./assets/objectes/tramit-qr.png" alt="QR del tràmit públic d’objectes perduts de TMB"><small>www.tmb.cat · Accés públic</small></div>
 <a class="op-action" href="tel:900701149"><strong>900 70 11 49</strong><small>De dilluns a diumenge, de 7.00 a 21.00 h</small></a>
 <p class="op-note">Consulta per telèfon a partir de l’endemà de la pèrdua. Si es localitza l’objecte, TMB t’indicarà com recuperar-lo.</p>
 </section>
 <section aria-labelledby="op-recollida"><h2 id="op-recollida">Recollida a Sagrada Família</h2>
 <p>Punt TMB d’Informació i Atenció, estació de Sagrada Família (L2/L5). Al vestíbul, després de les validadores, entrant per Provença–Marina.</p>
 <a class="op-source" href="https://www.tmb.cat/ca/atencio-al-client/punts-atencio/punts-tmb" target="_blank" rel="noopener noreferrer">Consulta la ubicació i els horaris oficials ↗</a>
 <p>Per recollir l’objecte, presenta el DNI i signa el justificant de lliurament. Per als mòbils, TMB descriu la comprovació de titularitat introduint la contrasenya de la SIM.</p>
 <details><summary>Custòdia i objectes no reclamats</summary><p>Segons TMB Notícies (17/08/2023), els objectes es custodien un màxim de 21 dies. Passat aquest termini, els que contenen documentació oficial o números de sèrie es lliuren a l’Ajuntament de Barcelona; els títols socials s’envien a Cetramsa; les targetes T-mobilitat es destinen a destrucció i els objectes reutilitzables no documentats es donen a Conductors Solidaris de Catalunya.</p><p class="op-note">Informació publicada el 2023. No s’ha confirmat una actualització posterior d’aquestes condicions.</p></details>
 <a class="op-source" href="https://noticies.tmb.cat/transport/com-funciona-recollida-dobjectes-perduts-metro-autobusos-0" target="_blank" rel="noopener noreferrer">Font sobre recollida i custòdia · TMB, 17/08/2023 ↗</a>
 </section>`;
 const button=root.querySelector('#op-show-qr'),qr=root.querySelector('#op-qr');
 button.onclick=()=>{qr.hidden=!qr.hidden;button.setAttribute('aria-expanded',String(!qr.hidden));button.textContent=qr.hidden?'Mostra el QR al viatger':'Amaga el QR';};
 return root;
}
