# Prototipo nativo iOS archivado

Este directorio conserva únicamente la investigación histórica de una app nativa
con Live Activity. No es el motor vigente de **El meu servei** y no debe retomarse
como base: dependía del servidor antiguo y no consultaba Miralín corporativo del
mismo modo que Android.

La línea de desarrollo activa sin cuota de Apple está en
`public/proves/servei/iphone/`. Es una PWA instalable desde Safari que prueba el
permiso de notificaciones y el acceso directo a
`https://aplicacions.tmb.cat/miralin/`. No usa `miralin.cat`, no solicita ni guarda
credenciales y muestra explícitamente el bloqueo si la política CORS de TMB impide
leer la tabla.

El workflow del prototipo nativo queda disponible solo mediante ejecución manual
para conservar la posibilidad de compilar el archivo histórico, pero no se activa
con los cambios actuales.
