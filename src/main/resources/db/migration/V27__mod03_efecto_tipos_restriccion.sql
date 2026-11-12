-- AV-251 (RF-PLA-004 v6, RN-003 y CU-003 paso 4): solo el examen de admisión y las evaluaciones son
-- prohibición. Decisión de Marcos (31/10): feriado = Favorable y evento masivo = Advertencia.
-- Cierre del campus y paro: Favorable, como supuesto a la espera de que Marcos lo confirme. Fuentes:
-- RN-03 de la entrevista con Áreas Verdes (01:20:18, «los servicios ruidosos se programan
-- preferentemente en feriados, fines de semana, días sin clases o paros») y RN-08 (52:19, la poda
-- mayor se ejecuta durante el cierre del campus, de diciembre a enero). Si se decide Advertencia,
-- basta con otra migración que cambie este valor.

UPDATE mod03_tipo_restriccion SET efecto = 'Favorable'
WHERE nombre IN ('Feriado', 'Cierre del campus', 'Paro');

UPDATE mod03_tipo_restriccion SET efecto = 'Advertencia' WHERE nombre = 'Evento masivo';
