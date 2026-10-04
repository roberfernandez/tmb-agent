# TMB Agent

PWA móvil con launcher estático y la pantalla Flutter de acceso de Incidencias. Única rama oficial: `main`.

## Estructura

- `index.html`: shell común y entrada de la aplicación.
- `src/main.js`: launcher, rutas y pantallas placeholder. El launcher comprueba sesión y aprobación antes de mostrarse.
- `src/modules.js`: registro extensible de módulos, iconos y enlaces externos.
- `src/bustia.js`: contacto directo y peticiones comunitarias con votos.
- `src/styles.css`: diseño responsive.
- `public/`: manifest, service worker y assets.
- `scripts/build.mjs`: exportación a `dist/`.
- `.github/workflows/pages.yml`: build y publicación de main en GitHub Pages.

Rutas: `#/incidencias`, `#/dea`, `#/bobines`, `#/computo`, `#/miralin`, `#/t-mobilitat`, `#/mapa-metro`, `#/cquadre`, `#/bustia`. Las rutas hash permiten abrir cada pantalla directamente en Pages sin configuración de servidor.

## Configuración

En `src/modules.js`, añade módulos al registro y asigna `icon` al archivo original cuando esté disponible. Para Miralín y T-Mobilitat, rellena `externalUrl` con su URL HTTPS pública de acceso. Cada pantalla ofrecerá entonces un enlace externo. Nunca incluyas credenciales en URLs o archivos.

El icono de aplicación y las iniciales son placeholders explícitos pendientes de los originales. No se han diseñado logotipos nuevos.

## Publicación

Primero compila `auth/` con Flutter 3.41.9:

```sh
cd auth
flutter pub get
flutter build web --release --base-href /tmb-agent/auth/ --pwa-strategy none --no-web-resources-cdn
cd ..
npm run build
```

El launcher usa Node 22 o posterior para la exportación.
GitHub Pages usa GitHub Actions y publica al subir a `main`.
URL: https://roberfernandez.github.io/tmb-agent/

El service worker conserva únicamente archivos públicos de la base para abrirla sin conexión después de la primera visita. No almacena perfiles ni credenciales. Los destinos externos requieren su propia conexión.

La autenticación reutiliza Supabase Auth, usuarios y aprobación manual de Incidencias L4. Consulta `docs/autenticacion.md`. Los demás módulos mantienen sus placeholders.


## Bústia

La ruta `#/bustia` reutiliza la sesión aprobada de TMB Agent. Permite enviar consultas y errores por texto y mantiene un tablón de peticiones donde cada usuario autorizado puede sumar o retirar su apoyo una sola vez.

El navegador no contiene el token de Telegram. Las acciones se validan en la Edge Function `tmb-agent-bustia`, que comprueba de nuevo identidad y aprobación antes de usar los secretos del servidor. Las tablas de peticiones y votos tienen RLS activo y no exponen políticas directas al cliente.
