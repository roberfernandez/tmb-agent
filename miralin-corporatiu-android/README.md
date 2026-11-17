# Miralín corporatiu · Android (prototip independent)

Aplicació Android separada de **Miralín Tren Ull** i de `miralin.cat`. Reprodueix el contingut de les dues taules del cartell de l'L4 sense recalcular hores. No incorpora seguiment del tren, mapa, ni previsió pròpia.

## Connexió

- HTTPS directe a `https://aplicacions.tmb.cat/miralin/routes/4/stops/{stop}`, estacions 413–434.
- Autenticació HTTP Basic amb les credencials **del mateix treballador**; no es comparteixen amb TMBAgent, GitHub, Render ni cap altre servidor.
- Les credencials només es guarden al dispositiu, xifrades amb AES-GCM i una clau no exportable d'Android Keystore. Botó per esborrar-les.
- No segueix redireccions d'autenticació ni reintenta automàticament després de 401 o 403. No registra les dades rebudes ni les credencials.
- Llegeix `indexForm:tableArrivals1` i `indexForm:tableArrivals2` del document HTML i mostra les cel·les tal com apareixen. El parser està aïllat.
- Refresc cada 20 s **només mentre la pantalla de l'aplicació és visible**. Si falla, manté les darreres dades en memòria indicant-ne l'antiguitat; no confon una fallada amb l'absència de trens.
- `applicationId`: `cat.tmbagent.miralincorporatiu`; independent de `com.tmbagent.traineye`.

## Estat i limitacions

**Prototip no verificat contra un HTML corporatiu real.** Les proves automatitzades validen exemples d'HTML amb l'estructura comunicada per un company, no una resposta autenticada de TMB. Cal confirmar des d'un dispositiu autoritzat que la pàgina respon i que la taula real té aquesta estructura. La ruta no és una API pública documentada i pot canviar.

Cada persona ha de tenir permís per consultar les dades amb el seu compte; s'han de respectar les normes d'accés corporatiu de TMB. Aquesta aplicació és una prova independent i no és una aplicació oficial de TMB. No s'han d'enviar credencials al xat ni incloure-les al repositori.

## Compilació

`gradle -p miralin-corporatiu-android :app:testDebugUnitTest :app:assembleDebug`

La GitHub Action del prototip publica un APK directe de prova. Les APK debug no tenen signatura estable entre compilacions: les actualitzacions futures podrien requerir desinstal·lar i tornar a instal·lar, cosa que esborraria les credencials del dispositiu. Caldrà una signatura privada estable abans d'un desplegament general.

## Beta 0.2.0 · Ull flotant corporatiu
- Nova app independent «Miralín Corp Ull», identificador `cat.tmbagent.miralincorporatiu.ull`, per poder conviure amb la v0.1.1 i amb «Miralín Tren Ull». Això comporta introduir-hi les credencials una sola vegada a la nova app.
- Toca una fila de tren o escriu-ne el número (p. ex. 406 / P09) i prem «Activa l'ull».
- Permisos de superposició i notificacions: seguiment de primer pla Android amb una notificació persistent, consulta oficial cada 20 s fins que es premi × o «Atura l'ull».
- Mostra tren, estació, via, temps respecte del tren precedent (`T.ANT.`) i segons transcorreguts des de l'última lectura oficial. **No** presenta `T.ANT.` com una ETA a la parada; ni interpreta `SORTIDA` com a arribada.
- El servei només llegeix el HTML corporatiu de l'estació triada, i usa les credencials xifrades localment. Cap petició a miralin.cat, Render, TMBAgent ni cap servidor intermedi.
- La beta 0.2.0 només inclou seguiment manual d'un tren. El traspàs automàtic del servei de TMBAgent, els avisos i els temps de pas/arribada no s'hi han incorporat encara.
- Canviar d'estació a la pantalla principal no canvia el seguiment flotant fins a tornar a prémer «Activa l'ull».
- Els errors d'autenticació aturen les noves consultes del servei. Les fallades temporals mostren la informació antiga diferenciada. La font és un web corporatiu no documentat i pot canviar.

**Important:** aquesta beta 0.2.0 utilitza un identificador d'app diferent de la 0.1.1 per evitar conflictes de signatura debug; les credencials locals no es transfereixen entre APK.
