-- Pruebas de programación: corte (ruidoso) y fitosanitario cerrados hace 30 días, 30 + 3 + 1 días;
-- poda mayor sin configurar; evento masivo mañana.
TRUNCATE mod05_tiposervicio RESTART IDENTITY CASCADE;
INSERT INTO mod05_tiposervicio (nombre, ejecutor, es_riesgo, activo, es_periodico, es_ruidoso) VALUES
    ('Corte de césped', 'Tercerizado', false, true, true, true),
    ('Control fitosanitario', 'Tercerizado', false, true, true, false),
    ('Poda mayor programada', 'Tercerizado', false, true, true, false);
INSERT INTO mod03_frecuencia_servicio (tipo_servicio_id, estacion, frecuencia_teorica_dias,
    frecuencia_practica_dias, usuario_creacion_id, usuario_modificacion_id)
SELECT s, e, 21, 30, 1, 1
FROM generate_series(1, 2) s, unnest(ARRAY['Verano', 'Otoño', 'Invierno', 'Primavera']) e;
INSERT INTO mod03_duracion_servicio (tipo_servicio_id, duracion_maxima_dias, dias_limpieza,
    usuario_creacion_id, usuario_modificacion_id)
SELECT s, 3, 1, 1, 1 FROM generate_series(1, 2) s;
INSERT INTO mod03_programacion_servicio (tipo_servicio_id, estacion, fecha_inicio_prevista,
    fecha_fin_prevista, estado, creado_por_id, usuario_creacion_id, usuario_modificacion_id)
SELECT s, 'Primavera', hoy - 33, hoy - 30, 'Completada', 1, 1, 1
FROM generate_series(1, 2) s, (SELECT (now() AT TIME ZONE 'America/Lima')::date hoy) h;
INSERT INTO mod03_restriccion_calendario (tipo_restriccion_id, fecha_inicio, fecha_fin,
    usuario_creacion_id, usuario_modificacion_id)
SELECT t.id, hoy + 1, hoy + 1, 1, 1
FROM mod03_tipo_restriccion t, (SELECT (now() AT TIME ZONE 'America/Lima')::date hoy) h
WHERE t.nombre = 'Evento masivo';
