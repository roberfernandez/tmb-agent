# Convenis · FMB

Implementació interna de TMB Agent, en català, amb la mateixa ruta protegida i retorn a l'inici que les altres miniapps. No requereix una base de dades nova.

## Contingut i fonts

Catàleg: `public/assets/convenis/index.json`. 15 documents individuals i un inventari històric oficial. Els enllaços provenen del BOPB, TMB, CGT i CCOO. Fonts sindicals addicionals accessibles des de la pantalla Fonts: CIM, UGT i Solidaritat Obrera.

Revisió documental: 18/10/2026. No hi ha actualització automàtica. Els textos dels PDF públics s'han extret amb PyMuPDF, conservant el número físic de pàgina del PDF i normalitzant només els espais. L'original continua accessible al servidor de la font; excepte les còpies dels convenis XX i XVI descrites al final.

Hi ha 6 escanejos sense text extraïble: XXII, XXIII, XXV i pròrrogues 2012–2013, 2013–2014, 2014–2015. Es poden consultar, però no generen resultats de cerca ni fragments inventats. No s'ha fet OCR. La resta té text extraïble, que pot no reproduir perfectament la disposició visual o totes les taules.

L'inventari d'abril de 2020 només s'indexa a la secció FMB (pàgines 34–38). Inclou antecedents des de 1961 i altres pactes, però no equival als textos individuals ni acredita vigència. La discrepància 1999–2000 / 1999–2001 queda indicada; no es duplica com dos convenis confirmats. El pla d'igualtat localitzat correspon a 2022–2024.

## Funcions

- Documents: filtre per tipus.
- On està escrit?: cerca accent-insensible per paraules; temes suggerits amb equivalents català/castellà. Resultats per pàgina, no respostes jurídiques generades.
- Compara: dues fonts independents, fragments i enllaços a les pàgines originals. Sense afirmacions automàtiques sobre derogació, substitució o evolució de drets.
- Cronologia: períodes documentals i accés a antecedents.
- Fonts: abast, procedència, data de revisió i enllaç a Permisos TMB.

La pàgina indicada al fragment és física; el visor extern pot ignorar `#page`. Els PDF externs requereixen connexió. El catàleg i els fragments s'inclouen a la memòria cau de la PWA. L'índex es demana només en entrar a Convenis (~604 KB sense compressió); l'icona WebP de 256 píxels ocupa 7.012 bytes.

## Validació

Proves de cerca, accents, escanejos, pàgines, inventari FMB i integració amb el catàleg general. Revisió visual a 390 píxels i comprovació de la comparació de vacances entre XXVI i XXVII. Compilació estàtica amb el procés existent. Sense publicació ni migracions.

## Disponibilitat dels convenis XX i XVI

Els enllaços originals de CCOO han deixat de respondre en les comprovacions del 18/10/2026. Les còpies públiques descarregades durant la recerca s’inclouen sense modificacions a assets/convenis (14 i 8 pàgines), amb l’enllaç original conservat. La cerca, la comparació i la cronologia obren aquestes còpies. Només aquests dos PDF estan disponibles també a la memòria cau de la PWA.

El conveni XXI (1997–1998, 9 pàgines) també disposa de còpia local íntegra. Tots els documents del catàleg procedents de CCOO obren ara còpies locals. Els enllaços originals es mantenen com a referència secundària.
