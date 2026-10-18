# Rescat ascensors L4

Ubicacions confirmades per l'usuari: a Joanic, Alfons X i Via Júlia, EE2 correspon a l'ascensor exterior (1) i EE als d'andanes (2 i 3). La interfície mostra Exterior / Andanes sense exigir seleccionar el número.

Miniapp interna en català amb selector de cinc estacions visibles, fletxes, arrossegament i accés directe. Selecció i confirmació conservades a sessionStorage en tornar del PDF o de la ruta.

Els documents s'obren directament al visor del navegador en una pestanya nova. No es copien PDF ni pàgines del procediment i no es modifiquen instruccions de rescat. Identificació corporativa gestionada per SharePoint.

Font: https://tmbbcn.sharepoint.com/sites/SEEpublic/SitePages/Ascensors.aspx, apartat Línia 4, consultat el 18/10/2026.

La correspondència es desa a src/ascensors-data.js. Es dedupliquen enllaços repetits dins d'una estació; no s'agrupen documents diferents per ser tots elèctrics. EE és un document de 3 pàgines. EE2 és un manual de quadre unificat de 6 pàgines. EE7 identifica L4 Maragall SASC40020 i també usa un quadre unificat; no s'ha acreditat equivalència integral amb EE2 ni obsolescència d'un dels dos. Joanic, Alfons X i Via Júlia tenen EE2 i EE a la font. Maragall té EE2, EHM i EE7. Urquinaona i Verdaguer tenen les caselles buides: no es dedueix que no tinguin ascensors i no se'ls assigna cap manual.

La primera prova d'enllaç directe del PDF de Maragall va funcionar a Chrome i l'usuari va confirmar obertura i ampliació des del mòbil. La miniapp usa aquest mateix tipus d'enllaç directe. Les correspondències no s'actualitzen automàticament si TMB modifica la taula; cal revisar-les quan hi hagi canvis.

Validació local: 56 proves passen i compilació estàtica correcta. Verificats selector mòbil, variants de Maragall, restauració després de recàrrega i estat sense document d'Urquinaona.

Abans de publicar: revisar amb l'usuari les variants documentals. L'allowlist local de registre d'obertures inclou ascensors, però no s'ha desplegat la funció ni canviat la base de dades. La migració històrica limita els identificadors de miniapps: cal comprovar la restricció vigent i demanar confirmació abans de qualsevol migració per incorporar el registre d'obertures. Aquesta telemetria no condiciona la navegació ni l'obertura de PDF.
