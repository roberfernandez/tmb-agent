# Gestió de peticions

La funció `tmb-agent-bustia` comprova la sessió amb `auth.getUser()` i l’aprovació existent abans de qualsevol operació. Només permet `set_status` si `app_metadata.bustia_admin` és exactament `true`. Aquesta marca es gestiona al servidor; no s’utilitzen metadades editables per l’usuari ni permisos enviats pel navegador.

`list` retorna `can_manage`. La interfície només mostra el selector i «Desar estat» quan aquest valor és `true`. Cada desament torna a verificar els permisos al servidor i només actualitza `estat`, conservant text i suports. Els estats admesos són `oberta`, `en_estudi`, `acceptada`, `en_desenvolupament`, `feta` i `descartada`.

Les taules continuen amb RLS activat i sense polítiques d’accés directe per als clients. No s’afegeixen claus privades al frontend ni al repositori. La nova acció no envia avisos de Telegram. La gestió no inclou editar ni eliminar peticions.
