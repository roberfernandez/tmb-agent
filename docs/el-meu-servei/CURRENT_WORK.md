# Trabajo en curso

## Corrección pendiente de publicación — aviso prematuro de tren

Se ha identificado y corregido en `ServiceAlertPolicy` un caso en el que un tren
del servicio se veía en una estación anterior a la de toma y, aun así, generaba
un aviso inmediato en la persiana/reloj. El motor seguirá usando esa lectura para
el seguimiento interno, pero el aviso dinámico solo se emitirá cuando Miralín
muestre el tren en la estación real de embarque. Esto evita casos como un aviso
del 417 en Jaume I cuando debe tomarse en La Pau y el texto erróneo «La Pau → La
Pau».

Se añadió una prueba de regresión para tren visto en Jaume I antes de llegar a La
Pau. La compilación local no ha podido ejecutarse en este entorno porque falta
el plugin Android Gradle 8.7.3 en la caché disponible; debe validarse en GitHub
Actions antes de entregar una APK.

La selección manual se ha reforzado para que también prevalezca sobre una
reanudación automática basada únicamente en el reloj. Esa reanudación no se
considera prueba de que el tren haya sido tomado; solo una observación corporativa
válida o una confirmación manual puede liberar la pieza anterior.

Se ha eliminado además el aviso secundario de «llegada confirmada» durante la
transición de toma. Esa transición puede ejecutarse justo al dejar el tren y
provocar vibraciones repetidas en el reloj. El aviso dinámico previo se mantiene;
al confirmar la toma se limpia y no se genera otro.

Actualizado el 27 de noviembre de 2026.

## Android 0.14.29

Estado actual. APK candidata compilada y verificada; pendiente de prueba real por
Róber. La 0.14.28 continúa siendo la última versión validada.

Base comprobada.

- Fuente exacta de 0.14.28 recuperada del commit local histórico `83a45fd`.
- APK y compilación local idénticos con SHA-256 `f1dbaaec…fbcd`.
- Paquete `cat.tmbagent.elmeuservei.motor`.
- Certificado compatible con SHA-256 `8892fa0f…7f56`.
- La corrección de reanudar un servicio ya iniciado está incluida y tiene prueba.

Implementado en 0.14.29.

- Limpieza de avisos al terminar pieza, cambiar de servicio, confirmar una toma,
  desactivar avisos o detener seguimiento.
- La notificación permanente ID 9404 no entra en esa limpieza.
- Avisos de tren desde `departureAt`, `lastTrain` y observaciones corporativas
  frescas; se actualizan si TMB modifica el paso previsto.
- Traslados sin tren desde el horario del servicio; traslados con tren excluidos
  de la programación teórica.
- Etiqueta estable por identidad de servicio y fila para impedir fugas entre días.
- Migración única de los antiguos IDs aleatorios 9600–9999.

APK candidata publicada:
`https://github.com/roberfernandez/tmb-agent/releases/download/el-meu-servei-motor-v0.14.29/El-meu-servei-motor-0.14.29.apk`.
SHA-256 `969735724a76b692e0c9b8f672a6bfab24d7776033b04729155bf087fc0d7c8b`.
Paquete `cat.tmbagent.elmeuservei.motor`, `versionCode 46`, certificado
`8892fa0f…7f56`. Actualiza la 0.14.28 sin desinstalar. La compilación local de
control se conserva con SHA-256 `c8f296fd…49fc`; ambas contienen el mismo DEX,
recursos y manifiesto.
Commit de implementación GitHub: `5e4a6f96497d53003637d2eb6b5f8b7167040989`.

Pruebas ejecutadas: compilación limpia, tests Java del motor, verificación de
paquete/firma y 143 pruebas Node de TMBAgent.

## Compilación firmada independiente de Work

Preparado un workflow exclusivo que compila la fuente canónica, usa la firma
original desde GitHub Actions Secrets y publica tanto un artefacto descargable como
una prerelease con APK directo. La solicitud está separada en
`.github/el-meu-servei-build.json`, por lo que una conversación normal puede iniciar
otra compilación cambiando sólo `requestId` mediante el conector de GitHub.

Sistema operativo y probado de extremo a extremo sin cambiar código funcional ni
incrementar la versión. Los cuatro secretos cifrados están configurados; sus
valores no aparecen en Git, documentación, artefactos ni registros. La ejecución
GitHub Actions
[`45934184355`](https://github.com/roberfernandez/tmb-agent/actions/runs/45934184355)
fue iniciada por el commit textual
`147110f7f7d3cc05930775f83b6a88def9a2730b`, demostrando la vía disponible para
ChatGPT normal.

Resultados comprobados: `build-signed-apk` y `publish-release` en verde; artefacto
`El-meu-servei-motor-0.14.29-signed` (ID `15169553387`); prerelease directa
publicada; 38 tareas Gradle y tests Java correctos; paquete
`cat.tmbagent.elmeuservei.motor`; `versionName 0.14.29`; `versionCode 46`;
certificado `8892fa0f…7f56`; SHA-256 del APK de GitHub
`969735724a76b692e0c9b8f672a6bfab24d7776033b04729155bf087fc0d7c8b`.
La descarga se verificó fuera del runner y coincide con el SHA registrado por la
propia ejecución.

## iPhone sin cuota de Apple

Acuerdos vigentes.

- Instalación desde Safari como PWA.
- Miralín corporativo como única fuente permitida.
- Seguimiento automático y manual como objetivo funcional.
- Sin mini PC, servidor personal ni Apple Developer Program de pago.
- Integración desde la miniapp sin perjudicar Android.

Investigación ya realizada.

- El prototipo Swift antiguo usa `tmb-servei-proves.onrender.com` y la fuente
  antigua `miralin.cat`. Queda archivado y no puede reutilizarse como motor actual.
- La petición previa desde GitHub Pages a la URL corporativa devuelve autenticación
  Basic y no devuelve `Access-Control-Allow-Origin` ni autorización de credenciales.
- Con esa respuesta, Safari no permite a una PWA leer la tabla corporativa desde
  otro dominio, aunque el trabajador haya abierto antes Miralín en una pestaña.
- Las notificaciones Web Push instaladas en iOS 16.4 o posterior necesitan un
  servidor de envío. El navegador no garantiza sondeos periódicos con la pantalla
  bloqueada.

Trabajo realizado.

- PWA aislada en `public/proves/servei/iphone/`, instalable desde Safari.
- Comprueba dispositivo, instalación, permiso y notificación local.
- Abre y consulta exclusivamente la URL de Miralín corporativo.
- Si el acceso directo funciona, habilita seguimiento manual en primer plano con
  refresco de la tabla corporativa; acepta tren, estación y vía enviados por la
  miniapp.
- Si CORS lo bloquea, lo muestra sin sustituirlo por datos teóricos o `miralin.cat`.
- La miniapp dirige iPhone a este piloto y ya no abre `tmbservei-ios://` ni llama
  al antiguo servidor Render para iniciar o detener seguimientos.
- Las pruebas antiguas `connexio-directa` y `diagnostic-eta` redirigen al piloto.
- El proyecto Swift y su workflow quedan archivados, no eliminados.

Punto de reanudación exacto. Probar
`https://roberfernandez.github.io/tmb-agent/proves/servei/iphone/` en el iPhone
real de un compañero: añadir a inicio, abrir Miralín corporativo, conceder avisos
y pulsar «Comprova la connexió». Si Safari informa de bloqueo entre dominios, el
siguiente desarrollo necesita una vía corporativa con CORS autorizado o una app
nativa firmada; una PWA sin servidor no puede eludir esa política.

## Pendiente de validación humana

- La 0.14.29 deberá probarse sobre una 0.14.28 instalada, sin desinstalar.
- La PWA iPhone deberá probarse en Safari real de un compañero.
