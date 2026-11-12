# Vigila el servei · Android

Complement Android opcional per mostrar el tren per sobre d’altres aplicacions. La miniapp «El meu servei» continua sent el punt d’entrada.

## Requisits

- Android 8.0 o posterior.
- Permís d’avisos i permís especial «Mostrar sobre otras aplicaciones».
- Connexió a Internet amb accés a Miralin.cat.

## Connexió

Des de la versió 0.14.42, l'entrada nativa utilitza el client estàndard `io.socket:socket.io-client:2.1.2` directament amb Miralin.cat. Prioritza WebSocket; si fallen tres intents, passa al polling de la mateixa llibreria amb possibilitat d'actualització a WebSocket. La llibreria gestiona el protocol, els batecs i les reconnexions. El mapa i els quadres d'estació comparteixen connexió i es tornen a demanar les estacions seleccionades quan es reconnecta. Aturar el seguiment tanca el client i el control de frescor. El transport HTTP propi anterior es conserva només per a les proves de regressió.

Des de la versió 0.14.40, la recepció de mapes i la del quadre de cada estació es vigilen per separat: si una estació sol·licitada passa 60 segons sense resposta, es renova la sessió i es torna a subscriure a les estacions escollides. Un quadre sense etiqueta només s'atribueix quan s'ha demanat una única estació en aquella sessió, i aquesta identificació es conserva per a l'arribada i l'interval. El seguiment manual serveix per a qualsevol de les 22 estacions de L4; no fixa La Pau. La pantalla provisional de previsualització d'avisos de la 0.14.39 s'ha retirat.

Des de la versió 0.14.37, totes les dades de trens de l'APK provenen directament de Miralin.cat, sense consultar Render ni iniciar sessió al Miralín oficial de TMB. L’antiguitat mostrada és el temps des de la recepció del mapa, no una garantia de la data de mesura original.

El temps amb el tren anterior es llegeix de `secPreviousTrain` a les files `via1`/`via2` del quadre de l'estació seleccionada. Es manté separat dels temporitzadors de posició de `lineTrains` i de les dades del mapa; no és un compte enrere. Es respecten la via del servei, els àlies M/P, la propera aparició en mode manual i les terminals amb andana única. Els quadres caduquen als 60 segons i un valor nul es mostra com `--`. Quan es detecta l'arribada manual, es continua rebent aquest quadre mentre es manté el seguiment.

La notificació persistent indica l’estat de la connexió directa. No s’envien imatges ni dades del servei personal a Miralin. La superposició es tanca amb ×.

Des de la versió 0.14.38, el frontal fa 176 × 68 dp: estació, antiguitat de les dades i via a la fila superior; tren, interval amb el tren anterior (gorret blanc) i arribada a la fila inferior. Tocar-lo obre directament el visor del servei, amb la captura o la llista d'activitats, el tram actual ressaltat i desplaçament automàtic quan canvia. La creu del visor o el botó enrere el tanquen i recuperen el frontal. El panell de diagnòstic ETA s'ha retirat.

Des de la versió 0.14.15, Android també calcula els avisos al dispositiu. Amb els avisos habilitats i el seguiment automàtic actiu, se subscriu a les estacions de desplaçament i presa del proper tram. Avisa de trasllats, preses i pas del tren assignat quan la pantalla està apagada. Els marges de les tres alternatives (via contrària) es comparen amb l’arribada real del tren assignat, no només amb l’hora del paper. Si el mateix tren permet arribar-hi, indica aquesta darrera opció, inclòs el canvi de sentit a terminal. No calcula alternatives amb quadres de més de 60 segons ni sense identificar l’estació i la via.

Els avisos emesos es desen per servei i jornada per evitar repeticions després d’un reinici. Android ofereix permetre l’activitat en segon pla per mantenir connexió i CPU amb el mòbil bloquejat; el bloqueig de pantalla, l’estalvi de bateria i el comportament del fabricant requereixen una prova real al telèfon. El manteniment de CPU només s’aplica amb avisos automàtics pendents i s’allibera en aturar-los. Els avisos web de la resta de plataformes continuen depenent de Render.

La miniapp mostra el tren previst. Quan s’interpreta una captura diferent amb el seguiment ja actiu, apareix «Actualitza el seguiment» perquè l’usuari transfereixi l’horari nou a Android. La sessió actual continua fins que es prem aquest botó o «Atura». Fins que l’usuari aplica el servei nou, Android conserva la sessió anterior i la miniapp indica que hi ha un servei pendent d’aplicar. Canviar l’antelació mostra «Aplica els canvis»; carregar la pàgina no obre automàticament l’app Android. Activar o actualitzar envia l’horari complet, el marge i la configuració d’avisos.

Els errors de connexió conserven la causa i el missatge de xarxa, en lloc de reduir-los a `IOException`. El diagnòstic indica la llibreria i el transport seleccionat; continua disponible des del visor.

## Diagnòstic sota demanda

A la versió 0.14.41, toca el frontal i prem «Copia el diagnòstic» al visor. Copia l'estació realment configurada, l'estat d'arribada manual, el quadre acceptat i una traça limitada de la connexió: peticions, errors, esdeveniments i el darrer quadre rebut encara que s'hagi descartat. La traça es manté només en memòria i no s'envia automàticament. No inclou la captura ni l'horari personal del servei. No hi ha un panell ETA permanent i el botó no confirma l'arribada ni modifica la selecció.

## Instal·lació de prova

Baixa l’APK amb nom de versió des de la miniapp o des de les [versions publicades](https://github.com/roberfernandez/tmb-agent/releases). La compilació executa les proves de sessió, heartbeat, lectura de mapes i reconnexió abans de publicar l’arxiu. No està publicat a Google Play.

## Prova de servei

Amb el servei de prova que conté Estació a Jaume I de 21:04 a 23:09, trasllat a Trinitat Nova de 23:09 a 23:29 i conducció del tren 405 a les 23:29, el visor i el client Android han d’indicar 405 com a tren previst abans de la presa. Després de substituir una captura, prem «Actualitza el seguiment» per transferir-la al dispositiu. Activa els avisos, tria l’antelació i prem «Aplica els canvis» si l’has modificada. Prova la recepció amb la pantalla apagada i comprova que aturar el seguiment impedeix nous avisos locals.
