# Miralin Tren Ull · prova Android

Complement Android opcional per mostrar un indicador de tren per sobre d’altres aplicacions. La PWA continua sent el punt d’entrada i la versió per a iPhone.

## Requisits

- Android 8.0 o posterior.
- Permís d’avisos per mantenir visible l’estat del servei.
- Permís especial «Mostrar sobre otras aplicaciones».
- Connexió a Internet i servidor de proves Render disponible.

La app consulta `/api/trains` cada 15 segons, mostra el tren escollit, estació, via i antiguitat de la darrera recepció. No accedeix al contingut d’altres apps. La superposició es pot tancar amb ×; Android mostra un aviso persistente mientras esté activa.

## Install de la prova

El workflow **Android train eye** compila un APK debug y lo conserva como artefacto de Actions durante siete días. Descárgalo solo desde el repositorio del propietario. La app no está publicada en Google Play.
