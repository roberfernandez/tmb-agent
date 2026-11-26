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

La 0.14.28 se recuperó del commit local histórico
`83a45fd2091656dfe43db7fed26a3bc55580242e`. El APK archivado coincide byte por
byte con el producto de esa fuente. La referencia GitHub inmutable se rellenará
en `STATE.json` al publicar el commit de consolidación.

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

La firma compatible está fuera del repositorio. La clave no se copia a GitHub,
al chat, a los artefactos de Actions ni a la documentación. El certificado
público esperado tiene la huella SHA-256 indicada arriba.

La compilación local debe recibir por variables de entorno la ruta del almacén,
el alias y las contraseñas. El script de compilación comprueba el paquete, la
versión y la huella antes de aceptar el APK. GitHub Actions ejecuta pruebas y
una compilación técnica, pero no publica un APK actualizable si no dispone de la
firma protegida.

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
cuota de Apple es una PWA instalada desde Safari. Safari no puede leer la web
corporativa desde el dominio de GitHub Pages porque TMB no devuelve autorización
CORS para esa lectura. Una PWA tampoco puede mantener una consulta periódica
fiable con el iPhone bloqueado. El prototipo antiguo basado en `miralin.cat` queda
descartado. El siguiente paso conservado es una prueba iPhone aislada que valide
instalación, permisos y acceso corporativo directo sin recoger credenciales ni
simular datos en directo.

## Cierre obligatorio de cada sesión

Antes de interrumpir el trabajo se debe actualizar `CURRENT_WORK.md` con el último
resultado comprobado, las pruebas ejecutadas, el siguiente paso exacto y cualquier
bloqueo. Después se hace commit y se publica la rama en GitHub. Ninguna tarea se
considera transferible entre conversaciones mientras el commit no sea visible en
GitHub.
