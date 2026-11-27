# Continuidad de El meu servei · motor

Este documento permite retomar el desarrollo desde Work o desde una conversación
normal sin depender del historial del chat ni de archivos temporales.

## Referencia canónica

| Dato | Valor |
|---|---|
| Repositorio | `https://github.com/roberfernandez/tmb-agent` |
| Rama de integración | `main` |
| Fuente Android | `miralin-corporatiu-android/` |
| Paquete Android | `cat.tmbagent.elmeuservei.motor` |
| Nombre del producto | `El meu servei · motor` |
| Fuente de circulación | Miralín corporativo de TMB |
| URL corporativa | `https://aplicacions.tmb.cat/miralin/routes/4/stops/{413..434}` |
| Última versión validada por Róber | `0.14.28` (`versionCode 45`) |
| SHA-256 del APK validado | `f1dbaaec77b190de3ce3b6145c22039c96cad28efca6441c24856f800ec6fbcd` |
| SHA-256 del certificado | `8892fa0f4f446685f24eda202b4ee5493792f064291e2bddd7e33060046b7f56` |

La candidata 0.14.29 está fijada en el commit de implementación
`5e4a6f96497d53003637d2eb6b5f8b7167040989`; continúa como candidata hasta la
confirmación expresa de Róber.

La 0.14.28 se recuperó del commit local histórico
`83a45fd2091656dfe43db7fed26a3bc55580242e`. El APK archivado coincide byte por
byte con el producto de esa fuente. La consolidación canónica está en el commit
GitHub `3f832ac469f54e6e5f5e61377bb88f9febfef74e`.

## Inicio obligatorio de cualquier sesión

1. Clonar o actualizar `roberfernandez/tmb-agent` desde GitHub.
2. Leer `AGENTS.md` y los cinco documentos indicados allí.
3. Verificar que el paquete, la versión y la huella del certificado coinciden
   mediante `scripts/verify-el-meu-servei-motor.sh`.
4. Ejecutar las pruebas Java del motor y las pruebas Node de la miniapp.
5. Crear una rama desde `main`. No partir de una carpeta temporal antigua.
6. Registrar cada decisión y cada tarea incompleta antes de interrumpir el trabajo.

## Versiones y validación

Una compilación pasa por tres estados.

- **En desarrollo**. Código y pruebas aún pueden cambiar.
- **Candidata**. APK compilado, firmado y verificado, pendiente de prueba real.
- **Validada por Róber**. Versión que Róber ha probado y confirmado expresamente.

`STATE.json` distingue los tres estados. El archivo de una candidata nunca
sustituye la referencia validada. Cada versión conserva su fuente en Git y su
APK, huella y metadatos en `artifacts/el-meu-servei-motor/`.

## Compilación y firma

La firma original compatible se conserva únicamente como cuatro secretos cifrados
del repositorio. Sus valores nunca se copian al código, documentación, artefactos
ni registros:

- `MOTOR_SIGNING_KEYSTORE_BASE64`: almacén original completo codificado en Base64.
- `MOTOR_SIGNING_STORE_PASSWORD`: contraseña del almacén.
- `MOTOR_SIGNING_KEY_ALIAS`: alias de la clave original.
- `MOTOR_SIGNING_KEY_PASSWORD`: contraseña de la clave.

No se debe generar otra clave ni sustituir ninguno de esos secretos. La identidad
pública que debe producir siempre es
`8892fa0f4f446685f24eda202b4ee5493792f064291e2bddd7e33060046b7f56`.

El workflow `.github/workflows/el-meu-servei-signed-apk.yml` reconstruye el almacén
sólo en el directorio temporal del runner, verifica su certificado antes de usarlo
y lo elimina incluso si falla la compilación. El trabajo que recibe los secretos
tiene permisos de sólo lectura. Un segundo trabajo, que nunca recibe los secretos,
publica el APK ya verificado.

El sistema quedó probado de extremo a extremo el 27 de noviembre de 2026 en la
ejecución [45934184355](https://github.com/roberfernandez/tmb-agent/actions/runs/45934184355),
iniciada desde el conector de GitHub mediante el commit de texto
`147110f7f7d3cc05930775f83b6a88def9a2730b`. Los dos trabajos terminaron en verde.
El artefacto descargado volvió a superar la comprobación independiente de paquete,
`versionName`, `versionCode` y certificado. Su SHA-256 es
`969735724a76b692e0c9b8f672a6bfab24d7776033b04729155bf087fc0d7c8b`.
El código DEX, los recursos y el manifiesto son idénticos a los de la compilación
local de control; la diferencia de bytes del APK completo procede del proceso de
empaquetado y firma realizado en otro runner, no de cambios funcionales.

### Compilación firmada desde Work o ChatGPT normal

1. Leer `.github/el-meu-servei-build.json` y comprobar que `applicationId`,
   `versionName` y `versionCode` coinciden con `app/build.gradle`.
2. Confirmar que `versionCode` es mayor que `previousVersionCode`. Esto, junto al
   mismo paquete y certificado, permite actualizar la instalación anterior sin
   desinstalar ni borrar su configuración.
3. Cambiar únicamente `requestId` por un identificador nuevo y publicar ese archivo
   en `main`. El conector de GitHub disponible en una conversación normal puede
   hacerlo con una actualización de archivo de texto; ese push inicia Actions.
4. Vigilar el workflow **El meu servei motor signed APK** hasta que sus trabajos
   `build-signed-apk` y `publish-release` estén en verde.
5. Descargar el artefacto firmado de la ejecución o el APK directo de la versión
   en GitHub Releases. Para la candidata actual:
   `https://github.com/roberfernandez/tmb-agent/releases/download/el-meu-servei-motor-v0.14.29/El-meu-servei-motor-0.14.29.apk`.
6. Antes de entregarlo, verificar con `scripts/verify-el-meu-servei-motor.sh` el
   paquete, la versión, el `versionCode` y la huella del certificado.

También se puede iniciar manualmente con **Run workflow** gracias a
`workflow_dispatch`, pero el cambio de `requestId` es el procedimiento permanente
para que ChatGPT normal pueda iniciarlo con las herramientas de GitHub disponibles.
El workflow compara la solicitud con Gradle, ejecuta las pruebas Java, firma,
comprueba paquete/versión/certificado, conserva el APK 90 días como artefacto y
crea o actualiza una prerelease con descarga directa. Recompilar una candidata no
obliga a incrementar su versión; sólo cambia `requestId`.

La prueba real confirmó además que una conversación con acceso al conector de
GitHub puede publicar el cambio de `requestId`, vigilar la ejecución, descargar el
artefacto y comprobarlo sin acceder al entorno local de Work. No necesita conocer
ni manipular los secretos de firma.

La compilación local sigue disponible mediante
`scripts/build-el-meu-servei-motor.sh`, pasando el almacén y los tres datos de firma
por variables de entorno. El mismo verificador se utiliza localmente y en Actions.

El historial fuente 0.14.1–0.14.28 y los 28 APK están enlazados, con sus huellas,
desde `artifacts/el-meu-servei-motor/APK_INDEX.md`. Esto permite recuperarlos sin
conservar una carpeta temporal de Work.

## Proyectos que no deben confundirse

- `miralin-corporatiu-android/` es el motor vigente.
- `android-overlay/` contiene el antiguo **Miralin Tren Ull** y queda fuera de
  cualquier cambio de El meu servei.
- `ios/` contiene un prototipo nativo antiguo que consultaba el servidor de
  `miralin.cat`. No es la base del motor iPhone vigente.
- `backend/miralin/` pertenece a la investigación antigua de `miralin.cat` y no
  puede utilizarse como fuente del motor actual.

## Fuente corporativa y privacidad

Android consulta directamente la web corporativa. Las credenciales del
trabajador permanecen cifradas en el dispositivo mediante Android Keystore.
Nunca se envían a GitHub, Render, TMBAgent ni a otro intermediario.

## iPhone

La investigación actual está descrita en `CURRENT_WORK.md`. La alternativa sin
cuota de Apple es la PWA `public/proves/servei/iphone/`, instalada desde Safari.
Prueba permisos y acceso directo a TMB y habilita seguimiento manual en primer
plano únicamente si Safari puede leer la tabla. La respuesta corporativa observada
no incluye autorización CORS, por lo que se espera que el iPhone real confirme el
bloqueo. Una PWA tampoco mantiene sondeos fiables con el iPhone bloqueado ni puede
crear una Live Activity. El prototipo basado en `miralin.cat` sigue archivado y no
se puede reactivar como solución vigente.

## Cierre obligatorio de cada sesión

Antes de interrumpir el trabajo se debe actualizar `CURRENT_WORK.md` con el último
resultado comprobado, las pruebas ejecutadas, el siguiente paso exacto y cualquier
bloqueo. Después se hace commit y se publica la rama en GitHub. Ninguna tarea se
considera transferible entre conversaciones mientras el commit no sea visible en
GitHub.
