# TMB Agent

Base PWA móvil, estática y sin dependencias. Única rama oficial: `main`.

## Estructura

- `index.html`: shell común y entrada de la aplicación.
- `src/main.js`: launcher, rutas y pantallas placeholder. `startApp()` es la entrada central donde se podrá incorporar un login único en otra fase.
- `src/modules.js`: registro extensible de módulos, iconos y enlaces externos.
- `src/styles.css`: diseño responsive.
- `public/`: manifest, service worker y assets.
- `scripts/build.mjs`: exportación a `dist/`.
- `.github/workflows/pages.yml`: build y publicación de main en GitHub Pages.

Rutas: `#/incidencias`, `#/dea`, `#/bobines`, `#/computo`, `#/miralin`, `#/t-mobilitat`, `#/mapa-metro`. Las rutas hash permiten abrir cada pantalla directamente en Pages sin configuración de servidor.

## Configuración

En `src/modules.js`, añade módulos al registro y asigna `icon` al archivo original cuando esté disponible. Para Miralín y T-Mobilitat, rellena `externalUrl` con su URL HTTPS pública de acceso. Cada pantalla ofrecerá entonces un enlace externo. Nunca incluyas credenciales en URLs o archivos.

El icono de aplicación y las iniciales son placeholders explícitos pendientes de los originales. No se han diseñado logotipos nuevos.

## Publicación

`npm run build` con Node 22 o posterior. No requiere instalación de paquetes.
GitHub Pages usa GitHub Actions y publica al subir a `main`.
URL: https://roberfernandez.github.io/tmb-agent/

El service worker conserva únicamente archivos públicos de la base para abrirla sin conexión después de la primera visita. No almacena perfiles ni credenciales. Los destinos externos requieren su propia conexión.

No hay autenticación, Supabase, Telegram, permisos ni integración de aplicaciones reales. El futuro login y los módulos internos se implementarán por separado; esta base no ofrece control de acceso.
