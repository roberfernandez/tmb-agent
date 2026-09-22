# Autenticación reutilizada de Incidencias L4

## Procedencia y alcance de la primera fase

`auth/lib/acceso_screen.dart` procede de la implementación Flutter existente
`Documents/incidencias/lib/acceso_screen.dart`. Se conserva el formulario, su
diseño, textos de validación, contraseña, solicitud de acceso, tratamiento de
errores y restauración de sesión. El título identifica ahora TMB Agent.
Antes de consultar la aprobación se valida también la identidad en Supabase
Auth mediante `getUser()` en lugar de confiar en el usuario guardado en disco.

Se reutilizan, sin modificar su configuración ni datos:

- El proyecto Supabase `hhenkvendzengggrgook` y su clave publishable.
- Supabase Flutter 2.17.2, persistencia y renovación de sesión del SDK.
- La tabla `solicitudes_acceso`, filtrada por la identidad autenticada.
- La Edge Function `solicitar-acceso`, invocada con sesión y cuerpo vacío.
- La función `telegram-bot`, el aviso y los botones de autorización existentes.

Las funciones se inspeccionaron en el panel, sin desplegarlas ni leer secretos.
El backend valida la identidad y el correo permitido; el aviso usa secretos
que permanecen exclusivamente en Supabase. No hay nuevas cuentas ni proyectos.

Los estados existentes son `aprobado`, `pendiente` y `rechazado`. Una aprobación
en cualquiera de las solicitudes prevalece sobre las demás, tal como ocurre
en Incidencias y en `solicitar-acceso`. No se ha inventado un estado `blocked`
ni se ha cambiado esa precedencia. Pendientes y rechazados no entran.

## Sesión compartida

Las dos aplicaciones usan el origen `https://roberfernandez.github.io`.
Supabase Flutter Web guarda la sesión con la clave
`sb-hhenkvendzengggrgook-auth-token`; la ruta de Pages no separa localStorage.
No se pasan tokens en URLs, query strings, mensajes ni parámetros de navegación.

El launcher lee esa misma sesión, valida el usuario contra Auth y consulta
de nuevo la aprobación remota. No hay aprobación persistida ni acceso offline.
La renovación sigue siendo responsabilidad de Supabase Flutter en `auth/`.
Sin sesión válida se abre la pantalla existente. Los errores de red bloquean
el launcher y permiten reintentar. Se revalida al volver a la ventana, cambiar
de ruta, restaurar la página, cambiar la sesión en otra pestaña y periódicamente.

`returnTo` solo permite las rutas de TMB Agent e Incidencias en el mismo origen.
No constituye prueba de identidad ni autorización.

El formulario es un bundle Flutter dentro de TMB Agent; no se ha reimplementado
el login en JavaScript. `src/session.js` es solo la comprobación de acceso del
launcher. Las credenciales se introducen en el formulario Flutter y las gestiona
el mismo SDK que Incidencias.

## Validación y límite entre fases

Pruebas focalizadas: cinco pruebas Flutter reutilizadas de restauración y once
pruebas del control de acceso del launcher. Son pruebas controladas; no equivalen
a una aprobación real por Telegram ni al cierre físico de una PWA instalada.

Antes de retirar el formulario redundante en Incidencias deben verificarse con
el usuario el acceso aprobado y la solicitud pendiente mediante Telegram.
La primera fase no modificó Incidencias, DEA, Bobines ni otros módulos.

## Integración de Incidencias

Tras confirmar el acceso aprobado, la restauración en el navegador y la solicitud
real por Telegram, la tarjeta Incidencias abre `/incidencias-l4/`. Allí se reutiliza
este mismo `src/session.js` antes de arrancar Flutter. El retorno desde el login
estaba ya limitado a ambas rutas. No se modifican los restantes módulos.

La prueba controlada generó la solicitud #5, aprobada desde Telegram y confirmada
por el usuario al entrar al launcher. La solicitud histórica #3 permanece rechazada;
no se cambió la cuenta Auth ni la cuenta principal. El cierre completo de la PWA
instalada sigue necesitando comprobación en el dispositivo del usuario.

Limitaciones: navegadores/perfiles distintos no comparten sesión. El aislamiento
de almacenamiento de una PWA depende del navegador y debe comprobarse en el
dispositivo final. Las comprobaciones del frontend no sustituyen las políticas
RLS y validaciones del backend existente.
