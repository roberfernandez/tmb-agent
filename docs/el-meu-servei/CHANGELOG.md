# Changelog de El meu servei · motor

## 0.14.29 — en desarrollo

- Consolidación del código completo sobre GitHub partiendo de la 0.14.28 validada.
- Sistema de continuidad entre Work y conversaciones normales.
- Pendiente en este punto. Limpieza automática de avisos caducados.
- Pendiente en este punto. Avisos de tren basados en Miralín corporativo.
- Pendiente en este punto. Piloto iPhone desde Safari con fuente corporativa.

## 0.14.28 — validada por Róber

- Al iniciar el motor durante un servicio ya empezado, recupera la pieza horaria
  vigente y evita reproducir transiciones anteriores.
- El cambio EST → TRA y TRA → conducción usa la llegada real del tren implicado.
- Seguimiento de retrasos sin abandonar una conducción o una toma pendiente por
  el mero paso de la hora teórica.
- Traslados en el mismo sentido y por vía contraria con alternativas corporativas.
- Seguimiento automático y manual, alias M/P y trenes 401 a 425.
- Intervalo con el tren anterior y uno o dos galones según MDM, también en manual.
- Motor con tres zonas táctiles para Miralín, El meu servei y visor.
- Visor nativo del servicio con selección de fila sin detener el seguimiento.
- Notificación permanente del estado y avisos diferenciados de tren y traslado.
- Credenciales cifradas localmente y consultas directas a Miralín corporativo.

## 0.14.0 y anteriores

El historial técnico completo permanece en Git. Las primeras versiones incorporaron
progresivamente autenticación corporativa, parser de ambas vías, motor flotante,
cuenta atrás de SORTIDA, servicio automático, visor, persistencia y seguimiento de
relevos. Los APK conservados se enumeran en `artifacts/el-meu-servei-motor/APK_INDEX.md`.
