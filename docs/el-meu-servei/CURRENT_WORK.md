# Trabajo en curso

Actualizado el 26 de noviembre de 2026.

## Android 0.14.29

Estado actual. En desarrollo.

Base comprobada.

- Fuente exacta de 0.14.28 recuperada del commit local histórico `83a45fd`.
- APK y compilación local idénticos con SHA-256 `f1dbaaec…fbcd`.
- Paquete `cat.tmbagent.elmeuservei.motor`.
- Certificado compatible con SHA-256 `8892fa0f…7f56`.
- La corrección de reanudar un servicio ya iniciado está incluida y tiene prueba.

Objetivos de 0.14.29.

- Eliminar automáticamente avisos de piezas ya terminadas.
- Mantener la notificación permanente del seguimiento vigente.
- Generar los avisos que implican un tren desde las lecturas y previsiones reales
  de Miralín corporativo.
- Mantener el horario del servicio únicamente para traslados sin tren implicado.
- Añadir regresiones que protejan todo lo anterior.

Punto de reanudación exacto. La fuente 0.14.28 se está consolidando sobre `main`.
El siguiente cambio de código debe empezar en `CorporateServicePlan.noticesAt`
y `CorporateEyeService.tickServiceAlerts`, acompañado de pruebas.

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

Siguiente paso exacto. Crear una PWA de prueba solo para iPhone dentro de
`public/proves/servei/iphone/` que compruebe instalación, permiso de notificaciones
y conexión directa a la URL corporativa. Debe mostrar un fallo real si CORS la
bloquea, no usar `miralin.cat`, no pedir credenciales y no presentar horarios como
datos en directo. Integrar el acceso condicional en la miniapp después de probarlo
sin regresiones en Android.

## Pendiente de validación humana

- La 0.14.29 deberá probarse sobre una 0.14.28 instalada, sin desinstalar.
- La PWA iPhone deberá probarse en Safari real de un compañero.
