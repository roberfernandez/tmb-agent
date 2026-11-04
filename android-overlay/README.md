# Vigila el servei · Android

Complement Android opcional per mostrar el tren per sobre d’altres aplicacions. La miniapp «El meu servei» continua sent el punt d’entrada.

## Requisits

- Android 8.0 o posterior.
- Permís d’avisos i permís especial «Mostrar sobre otras aplicaciones».
- Connexió a Internet amb accés a Miralin.cat o al servidor Render.

## Connexió

A partir de la versió 0.14.14, el servei Android obre una única connexió HTTPS Engine.IO amb Miralin.cat mentre el seguiment està actiu. Rep les posicions de la L4 i escull el tren segons el mode manual o l’horari rebut. Respon als missatges de manteniment, torna a connectar amb una espera de 2 a 30 segons després d’un tall i reinicia la sessió si passen 90 segons sense cap mapa nou. Aturar el seguiment tanca el client de xarxa.

Si no hi ha dades directes de menys de 45 segons, consulta `/api/trains` de Render cada 15 segons. Una resposta tardana de Render no substitueix dades directes recents. L’antiguitat mostrada és el temps des de la recepció del mapa, no una garantia de la data de mesura original.

La notificació persistent indica l’estat de la connexió directa. No s’envien imatges ni dades del servei personal a Miralin. La superposició es tanca amb ×.

Des de la versió 0.14.15, Android també calcula els avisos al dispositiu. Amb els avisos habilitats i el seguiment automàtic actiu, se subscriu a les estacions de desplaçament i presa del proper tram. Avisa de trasllats, preses i pas del tren assignat quan la pantalla està apagada. Els marges de les tres alternatives (via contrària) es comparen amb l’arribada real del tren assignat, no només amb l’hora del paper. Si el mateix tren permet arribar-hi, indica aquesta darrera opció, inclòs el canvi de sentit a terminal. No calcula alternatives amb quadres de més de 60 segons ni sense identificar l’estació i la via.

Els avisos emesos es desen per servei i jornada per evitar repeticions després d’un reinici. Android ofereix permetre l’activitat en segon pla per mantenir connexió i CPU amb el mòbil bloquejat; el bloqueig de pantalla, l’estalvi de bateria i el comportament del fabricant requereixen una prova real al telèfon. El manteniment de CPU només s’aplica amb avisos automàtics pendents i s’allibera en aturar-los. Els avisos web de la resta de plataformes continuen depenent de Render.

La miniapp mostra el tren previst. Quan s’interpreta una captura diferent amb el seguiment ja actiu, apareix «Actualitza el seguiment» perquè l’usuari transfereixi l’horari nou a Android. La sessió actual continua fins que es prem aquest botó o «Atura». Fins que l’usuari aplica el servei nou, Android conserva la sessió anterior i la miniapp indica que hi ha un servei pendent d’aplicar. Canviar l’antelació mostra «Aplica els canvis»; carregar la pàgina no obre automàticament l’app Android. Activar o actualitzar envia l’horari complet, el marge i la configuració d’avisos.

## Instal·lació de prova

Baixa l’APK amb nom de versió des de la miniapp o des de les [versions publicades](https://github.com/roberfernandez/tmb-agent/releases). La compilació executa les proves de sessió, heartbeat, lectura de mapes i reconnexió abans de publicar l’arxiu. No està publicat a Google Play.
