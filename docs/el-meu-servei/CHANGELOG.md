# Changelog de El meu servei · motor

## 0.14.29 — candidata pendiente de validar por Róber

- Consolidación del código completo sobre GitHub partiendo de la 0.14.28 validada.
- Sistema de continuidad entre Work y conversaciones normales.
- Los avisos de piezas anteriores se eliminan al terminar la pieza, confirmar la
  toma del tren, cambiar de servicio, desactivar los avisos o detener el motor.
- La notificación permanente del seguimiento queda separada de esa limpieza.
- Los avisos que implican un tren se crean y actualizan con la previsión o la
  llegada observada en Miralín corporativo; ya no se generan desde la hora teórica.
- Los traslados sin tren implicado conservan la hora validada del servicio y se
  identifican como «segons servei».
- Los avisos usan etiquetas estables por servicio y pieza; una migración única
  elimina los avisos aleatorios que pudieran quedar de la 0.14.28 o anteriores.
- Pruebas de regresión específicas para fuente real, caducidad, cambio de servicio
  y separación entre aviso permanente y avisos de pieza.
- Piloto iPhone instalable desde Safari, enlazado desde El meu servei y limitado a
  Miralín corporativo. Sustituye las rutas activas del prototipo de `miralin.cat`.
- Los workflows del antiguo Miralin Tren Ull y del prototipo iOS quedan archivados
  y sin publicación automática.
- Compilación, firma y publicación reproducibles mediante GitHub Actions, con la
  clave original protegida por Secrets y validación automática de paquete, versión,
  `versionCode` y certificado antes de ofrecer el APK.
- Manifiesto de solicitud textual para que Work o ChatGPT normal puedan iniciar la
  misma compilación sin depender de archivos locales ni aumentar la versión.
- Prueba real completa de GitHub Actions superada: restauración protegida de la
  firma original, compilación, verificación, artefacto y prerelease directa. La
  descarga se volvió a comprobar fuera del runner y conserva paquete, versión,
  `versionCode` y certificado compatibles con la 0.14.28 instalada.

## 0.14.28 — validada por Róber

- Al iniciar el motor durante un servicio ya empezado, recupera la pieza horaria
  vigente y evita reproducir transiciones anteriores.
- El cambio EST → TRA y TRA → conducción usa la llegada real del tren implicado.
- Seguimiento de retrasos sin abandonar una conducción o una toma pendiente por
  el mero paso de la hora teórica.
- Traslados en el mismo sentido y por vía contraria con alternativas corporativas.
- Seguimiento automático y manual, alias M/P y trenes 401 a 425.
- Intervalo con el tren anterior y uno o dos galones según MDM, también en manual.
- Motor con tres zonas táctiles para Miralín, El meu servei y visor.
- Visor nativo del servicio con selección de fila sin detener el seguimiento.
- Notificación permanente del estado y avisos diferenciados de tren y traslado.
- Credenciales cifradas localmente y consultas directas a Miralín corporativo.

## 0.14.0 y anteriores

El historial técnico completo permanece en Git. Las primeras versiones incorporaron
progresivamente autenticación corporativa, parser de ambas vías, motor flotante,
cuenta atrás de SORTIDA, servicio automático, visor, persistencia y seguimiento de
relevos. Los APK conservados se enumeran en `artifacts/el-meu-servei-motor/APK_INDEX.md`.
