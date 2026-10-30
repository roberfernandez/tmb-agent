# Correcciones de la auditoría del 30 de octubre de 2026

## Verificado y corregido

- La página 19 tenía cero bytes. Se ha generado de nuevo desde la página 19 del PDF original, conservando contenido y orientación.
- La caché pedía la versión v2 del CSS de CQuadre mientras index.html usa v3. Se sincroniza y el build incluye todos los módulos JS/CSS del launcher, también dependencias importadas.
- El registro del service worker se ejecuta antes de la comprobación de acceso y también desde auth/. La caché es de archivos públicos, nunca de respuestas Auth/REST ni autorizaciones. Los fallos de red siguen cerrando el acceso: el README ya no promete acceso sin conexión.
- La versión de caché incorpora el bundle Flutter para que los cambios de autenticación cambien también la versión publicada.
- Se añade solicitud de recuperación de contraseña, espera mínima entre envíos, gestión de errores, validación del callback por el SDK y formulario de nueva contraseña. Se eliminan tokens/códigos de la barra de direcciones al procesar el enlace. Recuperar una contraseña no aprueba el acceso.
- El alta admite respuesta sin sesión cuando Supabase exige confirmar el correo. Informa de que debe confirmarse y volver a solicitar autorización. El administrador existente puede seguir iniciando sesión; la excepción de su Gmail ya no está en el formulario de alta.

## Base de datos

Se reprodujo `42P17 infinite recursion` en una inserción bajo el rol authenticated dentro de una transacción revertida. La app y la app hermana crean solicitudes mediante `solicitar-acceso`, y Telegram las resuelve en servidor. Se eliminó la política INSERT sin uso y se retiraron las escrituras directas del navegador. Se conserva SELECT de la propia solicitud y los privilegios del servidor.

No se añadió UPDATE para usuarios: permitir cambiar `estado` podría autorizar al propio usuario. Este resultado sustituye la recomendación genérica de permitir modificar su solicitud.

Verificaciones posteriores: RLS activo, política SELECT propia conservada, 25 aprobaciones conservadas y administrador aprobado. Una identidad no aprobada no puede insertar, actualizar ni borrar solicitudes, ni leer filas de trenes, calendario o movimientos. Los avisos del asesor de seguridad sobre otras funciones existentes quedan fuera de este cambio.

SQL aplicado: `scripts/security/harden-solicitudes.sql`; registro remoto `solicitudes_server_writes_only`.

## Pendiente de configuración y prueba de correo real

El conector no expone ajustes Auth/SMTP y el panel web requiere iniciar sesión. No se ha activado la confirmación ni cambiado SMTP.

Antes de dar por operativa la recuperación y activar confirmaciones:

1. En Authentication > URL Configuration, permitir exactamente `https://roberfernandez.github.io/tmb-agent/auth/` y `https://roberfernandez.github.io/tmb-agent/auth/?recovery=1`. Comprobar Site URL y que las plantillas utilizan la URL de confirmación del servicio.
2. Revisar Authentication > Email / SMTP. El proveedor de pruebas puede restringir destinatarios; para enviar a buzones de compañeros hay que configurar un proveedor que admita esos destinatarios. No basta con aumentar el límite.
3. Probar un único correo propio: enlace, cambio y posterior inicio de sesión. Probar enlace caducado y cuenta pendiente sin autorización.
4. Activar Confirm email en Authentication > Providers > Email después de tener verificado el envío. Probar alta, confirmación y solicitud por Telegram.

Las nueve cuentas de prueba del anexo no se han eliminado: su borrado definitivo es una operación separada. No se han creado nuevas cuentas ni enviado correos o mensajes Telegram durante estas verificaciones.
