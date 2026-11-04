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

Els avisos web del servei continuen calculant-se a Render. Aquesta connexió Android no restaura per si sola els càlculs d’arribades i alternatives del servidor si aquest no pot accedir a Miralin.

## Instal·lació de prova

Baixa l’APK amb nom de versió des de la miniapp o des de les [versions publicades](https://github.com/roberfernandez/tmb-agent/releases). La compilació executa les proves de sessió, heartbeat, lectura de mapes i reconnexió abans de publicar l’arxiu. No està publicat a Google Play.
