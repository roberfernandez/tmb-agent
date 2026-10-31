# Miralin Tren Ull · Live Activity inicial

App nativa de prueba para iPhone (iOS 16.2 o posterior). Recibe el modo automático/manual desde la miniapp con el esquema `tmbservei-ios://`, solicita una Live Activity de ActivityKit y muestra tren, estación, vía, estado y antigüedad de la última recepción en la pantalla bloqueada y la Dynamic Island compatible.

## Compilar para simulador

Requiere macOS con Xcode y XcodeGen:

```sh
brew install xcodegen
cd ios
xcodegen generate --spec project.yml --project .
xcodebuild -project TMBTrainLive.xcodeproj -scheme TMBTrainLive -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO APS_ENVIRONMENT=development build
```

El workflow `iOS Live Activity` compila el proyecto para simulador en macOS con cada cambio relacionado.

## Actualizaciones

Mientras la app está en primer plano, consulta Miralin y refleja los cambios significativos, como tren, estación, vía o estado; refresca la antigüedad cada cinco minutos. Para seguir actualizando la Live Activity con la app cerrada, el servidor debe enviar notificaciones ActivityKit a APNs. El sistema iOS decide cuántas acepta y cuándo las presenta; no se promete un intervalo fijo.

El servidor admite estas variables de entorno, que se configuran en Render y no se guardan en el repositorio:

- `APNS_KEY_ID`: identificador de la clave APNs.
- `APNS_TEAM_ID`: identificador del equipo Apple Developer.
- `APNS_PRIVATE_KEY`: contenido PEM de la clave APNs .p8; se permiten saltos de línea escapados como `\n`.
- `APNS_BUNDLE_ID`: identificador de la app; por defecto `cat.tmbagent.trainlive`.
- `APNS_ENVIRONMENT`: `sandbox` para desarrollo o `production` para TestFlight/App Store.

Sin esas credenciales, el tester todavía puede comprobar que iOS permite la Live Activity y ver la actualización local con la app abierta; el estado de la app indica que las actualizaciones remotas aún no están configuradas.

## Instalación para un tester

Para instalar en un iPhone ajeno, la vía prevista es TestFlight: la app debe firmarse y subirse a App Store Connect desde un equipo/cuenta Apple Developer. El tester instala TestFlight y acepta la invitación. El repositorio no contiene certificados, perfiles de firma ni claves privadas.
