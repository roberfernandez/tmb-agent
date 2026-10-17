# T-mobilitat

Mòdul intern de TMB Agent, ruta `#/t-mobilitat`, en català. Substitueix la targeta d'enllaç directe a Consultes; manté l'accés corporatiu dins de la miniapp. No requereix noves taules, credencials ni canvis al control d'accés: la ruta continua sota `authorizedRender`.

## Contingut

Font principal: Procediments T-Mobilitat 3.0, 23/03/2026, 22 pàgines. `tmobilitat-cases.js` conté les 79 entrades de les taules de les pàgines 3, 4, 6, 8, 9, 12, 14, 15, 16, 19, 20 i 22. Es conserva la cel·la original, el tipus d'actuació, la solució i la pàgina. Les cel·les combinades de la pàgina 14 s'hereten només dins del seu grup. La pàgina 8 inclou una columna lateral buida i un peu que no és una entrada.

`tmobilitat-data.js` completa 98 fitxes amb vuit resums de circuits (p. 5, 7, 10, 11, 13, 17, 18, 21), cinc errors de DA (p. 21), cinc fitxes de suports (p. 2) i un còmput contextual del provisional Metropolitana/acompanyant (p. 20). La pàgina 1 és l'índex. Els resums es diferencien del text de les taules. Els títols molt llargs de compatibilitat s'abreugen al visor; l'original es manté i és cercable.

El visor PAR és la navegació visual per a totes les gestions. Les files del document es denominen casos del procediment: no s'afirma que totes siguin missatges literals reals de la porta. Cercador sense distinció d'accents, filtre d'àmbit, selector i navegació anterior/següent. En prémer el visor, fitxa amb actuació, context, circuit guiat quan n'hi ha, casos relacionats i enllaç corporatiu al document. El fragment `#page` pot ser ignorat per SharePoint: sempre es mostra la pàgina explícita.

## Límits documentals conservats

- Defectuós i malmès no són equivalents; cartró i PVC tenen circuits diferents.
- En frau es bloqueja només el títol segons la p. 16; el final genèric del diagrama p. 13 no amplia el bloqueig a tot el suport.
- El cas de mòbil individual exigeix comprovar un títol integrat vàlid. El circuit guiat p. 18 evita oferir bescanvi si no es compleix.
- Les devolucions depenen del canal, les 24 h i si s'ha validat. Es conserven les diferències entre p. 8, 9 i 10.
- El diagrama p. 11 no es converteix en una autorització genèrica de devolució manual d'efectiu.
- El diagrama p. 17 limita genèricament el bescanvi a PVC, però es manté el cas explícit de cartró CTL p. 20.
- Restitució massiva: pendent segons la p. 19; no es presenta disponible.
- T-16 i T-verda: p. 20 no especifica dies; no s'aplica la regla 45/60 dies de CIM/Power BI. No s'ofereix un càlcul manual no justificat en aquestes fitxes.
- Còmput de Metropolitana/acompanyant: 30 dies, data inicial inclosa, amb data de la DA com a referència. No és una regla universal del suport PVC ni substitueix l'emissió automàtica.
- El contingut s'atribueix a la font de març 2026; no s'assegura que sigui l'última instrucció existent. Imports i horaris es presenten amb aquesta atribució.
- La pàgina 2 conserva peu V1.8 i només etiqueta Android; la p. 3 també inclou iOS. No s'oculta aquesta diferència.

## Estat de consulta i funcionament

La cerca, categoria, cas, obertura, passos de les guies i data seleccionada a la calculadora es guarden a sessionStorage sense dades de clients. La comprovació horària d'accés existent conserva el DOM de la consulta. Les dades són locals; els enllaços corporatius obren una pestanya amb la identificació pròpia del treballador. No s'extreuen ni es comparteixen sessions corporatives. No es distribueix el PDF intern amb la web.

Els recursos nous s'inclouen a la precàrrega del service worker. La comprovació d'accés existent continua sent necessària. No es canvia l'arquitectura de sessió ni s'afegeixen redireccions automàtiques a serveis externs en entrar a la miniapp.

## Validació

Tests: cobertura d'entrades i pàgines, referències enllaçades, herència de cel·les combinades, cerca i filtres, còmput calendari (any de traspàs, canvi d'hora, canvi d'any i dates invàlides), branques de circuits i integració del mòdul/cache. Revisió en navegador de visor/ficha, filtre, circuit mòbil, còmput i recuperació de consulta.

Pendent de publicació explícitament autoritzada per l'usuari.


## Entrada de tres vies i caducitat (18/10/2026)

Entrada aprovada: Consultes (enllaç corporatiu), Actuacions i solucions (visor PAR) i Caducitat de títols (secció pròpia). Tornada a la portada de T-mobilitat des de les dues seccions internes. La secció, els filtres i els càlculs es conserven a la sessió del navegador. Les il·lustracions de visor/calendari són CSS; Consultes i els vuit títols utilitzen WebP locals (64.422 bytes en total).

`tmobilitat-expiry.js` mostra exactament vuit títols: T-usual, T-jove, T-familiar, T-grup, Targeta Rosa, T-16, T-verda i Passi d'acompanyant. Distingeix títol original i bescanvi provisional ferroviari.

- Originals mensuals: 30 dies; T-jove: 90 dies. Data inicial inclosa. Límit tarifari opcional explícit, sense inventar una caducitat anual futura. Font TMB: https://www.tmb.cat/ca/tarifes-metro-bus-barcelona/condicions-us-bitllets (consultada 18/10/2026), a més de les fitxes del Manual.
- Originals socials: llegir la data del suport/Consultes. No inferir una nova caducitat sumant 30/45/60 dies a avui.
- Provisional Metropolitana/acompanyant: 30 dies, procediment 3.0 p. 20.
- Provisional T-16/verda: 30 dies o la caducitat original si és anterior. Font complementària: DOGC 9582, 14/01/2026, CVE-DOGC-A-26009095-2026, reglament T-16 art. 9 p. 25 i T-verda art. 14 p. 29. Es requereix introduir la caducitat original.
- Provisional PVC ATM amb altres títols: màxim 30 dies, respectant la caducitat original, segons condicions ATM art. 10 p. 11–12 del mateix DOGC. No convertir el bescanvi d'una T-jove en una nova validesa de 90 dies.
- Provisional dels quatre títols socials: data base avui, sense selector d'emissió, tal com es va acordar; mensuals/trimestral permeten seleccionar data.
- No hi ha càlcul per als 45/60 dies de CIM/Power BI. No substitueix la data que emet la DA.

El document DOGC es manté a la recerca local i s'enllaça al web oficial des de la miniapp; no es distribueix una còpia del PDF. El text primari del procediment 3.0 no es reescriu per atribuir-li els terminis que provenen del reglament.

Representació acordada: T-usual, T-jove, T-familiar, T-grup i T-verda comparteixen el PVC verd genèric (imatge extreta de la pàgina 2 del procediment) amb el nom superposat a la interfície. No representa una impressió real del títol al suport. T-16, Rosa i acompanyant conserven les imatges pròpies.
