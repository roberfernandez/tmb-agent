# Trabajo en curso

Actualizado el 26 de noviembre de 2026.

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

APK candidata: `artifacts/el-meu-servei-motor/0.14.29/El-meu-servei-motor-0.14.29.apk`.
SHA-256 `c8f296fd7ca6fc82ae548f603d0b8e6fc271dea04bd907c9cfd65d6a1eac49fc`.
Paquete `cat.tmbagent.elmeuservei.motor`, `versionCode 46`, certificado
`8892fa0f…7f56`. Actualiza la 0.14.28 sin desinstalar.
Commit de implementación: `df8d3f1752263c6a4a2a68cdb281d1e3b3959524`.

Pruebas ejecutadas: compilación limpia, tests Java del motor, verificación de
paquete/firma y 139 pruebas Node de TMBAgent.

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
