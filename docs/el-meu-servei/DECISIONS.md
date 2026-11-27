# Decisiones y reglas de funcionamiento

Estas reglas son acumulativas. Una versión nueva debe conservarlas salvo decisión
expresa de Róber registrada en este archivo y en el changelog.

## Identidad y fuente

- Producto Android `El meu servei · motor`.
- Paquete `cat.tmbagent.elmeuservei.motor`.
- Fuente de circulación exclusiva `Miralín corporativo`.
- No usar `miralin.cat`, su WebSocket, su proxy de Render ni el antiguo proyecto
  Miralin Tren Ull.
- Credenciales corporativas guardadas únicamente en el dispositivo.

## Seguimiento

- Modos `EST`, `TRA` y conducción con número de tren.
- Antes de iniciar el servicio se muestra la primera pieza.
- Al abrir el motor con el servicio iniciado se selecciona la pieza horaria vigente.
- La hora teórica no confirma por sí sola que se ha tomado un tren retrasado.
- La transición a conducción necesita evidencia corporativa o selección manual.
- La Pau y Trinitat Nova se tratan como terminales de andén único para la selección.
- El seguimiento manual admite trenes 401 a 425 y alias M/P de Miralín.
- El doble galón se aplica en automático y manual cuando el intervalo supera MDM.
- El intervalo con el tren anterior permanece visible junto al número de tren.
- Un traslado en el mismo sentido sigue el tren asignado hasta el relevo.
- Un traslado por vía contraria calcula opciones reales y el motor muestra la última
  opción válida. Las demás opciones pertenecen a los avisos.
- Tocar el tercio izquierdo abre Miralín corporativo, el centro abre El meu servei
  y el derecho abre el visor.

## Avisos

- La notificación permanente describe el seguimiento actual.
- Un aviso caducado se retira cuando termina su pieza o se confirma la transición.
- Los avisos con tren se basan en Miralín corporativo y se adaptan a adelantos,
  retrasos e irregularidades.
- Los traslados sin tren implicado usan la hora del servicio.
- Los avisos de tren y traslado conservan iconos diferenciados.
- No se presentará una hora teórica como llegada real.

## iPhone

- Sin cuota de Apple, la entrega activa es una PWA instalada desde Safari.
- La PWA sólo consulta Miralín corporativo y no pide ni conserva credenciales.
- Un bloqueo CORS se informa como bloqueo; nunca se rellena con `miralin.cat` ni
  con horarios simulados.
- El seguimiento en primer plano sólo se ofrece después de recibir una tabla
  corporativa legible.
- Safari no garantiza consultas con la pantalla bloqueada. Web Push requiere un
  emisor y una Live Activity requiere una app nativa firmada.

## Visor y servicio

- La imagen original del servicio se conserva localmente y la fila activa se
  resalta sin alterar el horario validado.
- Tocar una fila corrige la posición sin detener el seguimiento automático.
- Un servicio parcial o una renumeración impredecible no modifica el horario
  automáticamente. El seguimiento manual permite seguir el tren real.

## Regresiones mínimas

Cada candidata debe demostrar mediante pruebas que conserva el arranque a mitad
de servicio, los relevos tempranos y tardíos, los traslados en ambos sentidos,
terminales, seguimiento manual, alias M/P, MDM y doble galón, tres zonas táctiles,
visor, notificación permanente, limpieza de avisos y separación entre fuente real
y horario del servicio.

## Distribución y firma

- GitHub Actions es el método canónico para compilar, firmar y publicar APK.
- La clave original sólo existe en GitHub Actions Secrets y en copias privadas de
  custodia; nunca en Git, documentación, artefactos o registros.
- Cualquier APK entregable debe conservar el paquete
  `cat.tmbagent.elmeuservei.motor` y el certificado SHA-256 registrado.
- El trabajo que usa la clave no tiene permiso de escritura sobre el repositorio.
- ChatGPT normal inicia una compilación cambiando `requestId` en el manifiesto de
  solicitud; ningún cambio de versión se hace sólo para repetir una compilación.
- Una prerelease o un APK correcto técnicamente sigue siendo candidata hasta que
  Róber confirme expresamente su funcionamiento en servicio real.
