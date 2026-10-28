# Convenis · FMB

Implementació interna de TMB Agent, en català, amb la mateixa ruta protegida i retorn a l'inici que les altres miniapps. No requereix una base de dades nova.

## Contingut i fonts

Catàleg: `public/assets/convenis/index.json`. 15 documents individuals i un inventari històric oficial. Els enllaços provenen del BOPB, TMB, CGT i CCOO. Fonts sindicals addicionals accessibles des de la pantalla Fonts: CIM, UGT i Solidaritat Obrera.

Revisió documental: 18/10/2026. No hi ha actualització automàtica. Els textos dels PDF públics s'han extret amb PyMuPDF, conservant el número físic de pàgina del PDF i normalitzant només els espais. L'original continua accessible al servidor de la font; excepte les còpies dels convenis XX i XVI descrites al final.

Sis escanejos tenen OCR en castellà i català: XXII, XXIII, XXV, pròrrogues 2012–2013 i 2014–2015, i annex acord FMB 2013–2014. S’han processat 93 pàgines, de les quals 91 tenen text indexat i dues són fulls en blanc comprovats visualment. La resta de documents conserva el text original extret del PDF.

L'inventari d'abril de 2020 només s'indexa a la secció FMB (pàgines 34–38). Inclou antecedents des de 1961 i altres pactes, però no equival als textos individuals ni acredita vigència. La discrepància 1999–2000 / 1999–2001 queda indicada; no es duplica com dos convenis confirmats. El pla d'igualtat localitzat correspon a 2022–2024.

## Funcions

- Documents: filtre per tipus.
- On està escrit?: cerca accent-insensible per paraules; temes suggerits amb equivalents català/castellà. Resultats per pàgina, no respostes jurídiques generades.
- Compara: dues fonts independents, fragments i enllaços a les pàgines originals. Sense afirmacions automàtiques sobre derogació, substitució o evolució de drets.
- Cronologia: períodes documentals i accés a antecedents.
- Fonts: abast, procedència, data de revisió i enllaç a Permisos TMB.

La pàgina indicada al fragment és física; el visor extern pot ignorar `#page`. Els PDF externs requereixen connexió. El catàleg i els fragments s'inclouen a la memòria cau de la PWA. L'índex es demana només en entrar a Convenis (aproximadament 770 KB sense compressió); l'icona WebP de 256 píxels ocupa 7.012 bytes.

## Validació

Proves de cerca, accents, escanejos, pàgines, inventari FMB i integració amb el catàleg general. Revisió visual a 390 píxels i comprovació de la comparació de vacances entre XXVI i XXVII. Compilació estàtica amb el procés existent. Sense publicació ni migracions.

## Disponibilitat dels convenis XX i XVI

Els enllaços originals de CCOO han deixat de respondre en les comprovacions del 18/10/2026. Les còpies públiques descarregades durant la recerca s’inclouen sense modificacions a assets/convenis (14 i 8 pàgines), amb l’enllaç original conservat. La cerca, la comparació i la cronologia obren aquestes còpies. Només aquests dos PDF estan disponibles també a la memòria cau de la PWA.

El conveni XXI (1997–1998, 9 pàgines) també disposa de còpia local íntegra. Tots els documents del catàleg procedents de CCOO obren ara còpies locals. Els enllaços originals es mantenen com a referència secundària.

## Reconeixement OCR

Tesseract 5.5.0 amb models oficials tessdata_best spa+cat, renderitzat en gris a 300 dpi. Detecció d’orientació amb el model osd instal·lat i segon intent de segmentació per a lectures difícils. PDF originals intactes; només s’afegeix text al catàleg. Metadades de procedència i mostres revisades a convenis-ocr-audit.json. Les puntuacions del motor són senyals de revisió, no percentatges d’exactitud. No s’ha verificat paraula per paraula tota la transcripció ni s’usen els imports OCR per calcular nòmines. Les taules poden perdre l’ordre de columnes.

Les mostres revisades inclouen articles en prosa de cada document, pàgines de taules girades i els fulls en blanc p2013/4 i p2014/3. El PDF p2013 s’ha retitulat segons la capçalera comprovada visualment: annex a l’acta de 05/12/2012, acord FMB per a 2013–2014. No s’afirma cap canvi de vigència.

Reproducció: scripts/ocr-convenis.py genera els resultats locals i scripts/index-convenis-ocr.py els incorpora després de revisió. Els models, PNG i TSV intermedis no es publiquen. El mòbil només consulta el text i els PDF; no executa OCR.

La pròrroga 2012–2013 conté també, des de la pàgina 8, una còpia del XXV conveni 2008–2011. S’identifica aquesta part als resultats per evitar atribuir-li canvis de la pròrroga.

## Cerca amb ressaltat

On està escrit? i Compara actualitzen els resultats mentre s’escriu (espera breu de 180 ms, sense substituir el camp ni perdre el focus). Les coincidències es mostren amb mark groc, inclosos equivalents dels temes suggerits i accents. El text s’insereix com a text DOM, mai com HTML de la consulta. Les etiquetes OCR i els enllaços a la pàgina original es conserven.

## Lector amb format de lectura

El lector reconeix capítols, articles, clàusules i apartats numerats; separa passatges llargs en punts de frase i afegeix espai, sangries i un ample de lectura limitat. És una reconstrucció visual conservadora del text extret, no una reproducció de la maquetació del PDF. Les taules i els escanejos poden mantenir limitacions.

El catàleg i els PDF no es modifiquen. Cada bloc conserva els offsets exactes del text original. La cerca es calcula sobre tota la pàgina abans de repartir els ressaltats entre blocs: una coincidència que travessa un títol i un paràgraf continua comptant una sola vegada, amb totes les parts ressaltades. Es mantenen accents, navegació anterior/següent, pàgines d’origen i l’enllaç al PDF.

Validació: integritat caràcter a caràcter de tots els documents, mostres reals de títols i llistes, cerca entre blocs, navegació, neteja i retorn al document original.
