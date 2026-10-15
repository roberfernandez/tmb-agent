# Administració del launcher

La targeta privada es decideix amb `capabilities`. Cada consulta administrativa torna a validar el JWT amb Auth `getUser()`, consulta l’aprovació actual de `solicitudes_acceso` i exigeix `app_metadata.bustia_admin === true`. Les metadades de l’usuari i els permisos enviats pel client no autoritzen res. El frontend només conté la clau pública.

`POST /functions/v1/tmb-agent-admin`: `capabilities`, `open` (module permès), `heartbeat`/`leave` (UUID de pestanya), `dashboard` (days: 7, 30 o 90). Les tres primeres operacions requereixen usuari aprovat; `dashboard` també administrador. `verify_jwt=false` permet les claus públiques noves: la funció fa explícitament la validació del bearer amb Auth abans de qualsevol consulta de dades. Les respostes són `no-store` i no es guarden al service worker.

Es registra només UUID d’usuari, identificador de MiniApp i hora del servidor; màxim 30 obertures per minut i usuari. Un clic d’obertura no confirma que la MiniApp s’hagi carregat ni utilitzat. No s’instrumenten les MiniApps ni es llegeixen calendaris, nòmines o continguts privats.

Presència: heartbeat cada 25 segons mentre el launcher és visible, eliminació en ocultar/sortir i caducitat de 75 segons. Cada pestanya té UUID propi. El panell mostra hora de verificació i últim heartbeat; cal actualitzar per renovar la instantània. Una desconnexió sobtada pot continuar apareixent fins al límit de caducitat. Cap afirmació de presència dins MiniApps independents.

La llista usa només correu, alta, estat d’autorització i últim inici de sessió d’Auth (no equival a última activitat). Les estadístiques compten obertures i usuaris diferents dins el període mòbil seleccionat. Mostra com a màxim les 100 últimes obertures.

Les taules noves tenen RLS sense polítiques de client i permisos revocats a PUBLIC/anon/authenticated; les funcions RPC només són executables per service_role. El dashboard té un definer restringit per projectar camps seleccionats d’Auth. Les claus privilegiades només s’obtenen de l’entorn de Supabase. El cron horari elimina obertures anteriors a 90 dies i heartbeats d’un dia; pot haver-hi fins a una hora de marge de purga. L’eliminació d’un usuari elimina en cascada la seva telemetria. Les dades no s’exporten a Telegram.

Proves: `node --test tests/*.test.js`, proves d’autorització i permisos SQL amb transacció revertida, comprovacions HTTP sense JWT i amb JWT invàlid, build complet i desplegament de Pages. La comprovació visual amb dades reals necessita sessió de l’administrador; no es suplanta cap usuari ni es creen sessions d’Auth.
