-- Escenario de MOD-04 para el panel (RF-REP-001) y el material vegetal (RF-REP-005). Se carga después de
-- escenario-gestion.sql, que crea los espacios de mod05_areaverde: 1 Jardín Central, 2 Plaza de Armas.
-- Usuario 1 = jefe (lo crea ApiGestion).
--
-- Resultados esperados con desde=2026-10-01&hasta=2026-10-31 (mes 2026-10):
-- * Actividades: la 1 (parte cerrado, Riego 06, Jardín Central) y la 2 (parte abierto, Propagación 06,
--   Plaza de Armas). La 3 es de septiembre y no cuenta. Total "2".
-- * Operarios: Luis Ramos en la 1 y en la 2, Ana Torres en la 1.
-- * Material: Fertilizante 2.5 und consumido en Jardín Central; 10 Plantas propagadas en Plaza de Armas;
--   5 Esquejes trasladados de Jardín Central a Plaza de Armas.
TRUNCATE mod04_personal RESTART IDENTITY CASCADE;

INSERT INTO mod04_tipo_actividad (id, nombre, clase_id, activo) VALUES
    (106, 'Riego 06', 6, true),
    (105, 'Propagación 06', 5, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO mod04_personal (id, nombres, apellidos, tipo_personal, activo) VALUES
    (1, 'Rosa', 'Quispe', 'Capataz', true),
    (2, 'Luis', 'Ramos', 'Operario', true),
    (3, 'Ana', 'Torres', 'Operario', true);

INSERT INTO mod04_cuadrilla (id, nombre, capataz_id, activa, usuario_creacion_id, usuario_modificacion_id)
VALUES (1, 'Cuadrilla Norte', 1, true, 1, 1);

INSERT INTO mod04_parte_diario (id, cuadrilla_id, capataz_id, fecha, estado, usuario_creacion_id) VALUES
    (1, 1, 1, '2026-10-05', 'Cerrado', 1),
    (2, 1, 1, '2026-10-06', 'Abierto', 1),
    (3, 1, 1, '2026-09-20', 'Cerrado', 1);

INSERT INTO mod04_actividad (id, parte_id, tipo_actividad_id, area_verde_id, fecha_hora_actividad,
    tiempo_minutos, usuario_creacion_id) VALUES
    (1, 1, 106, 1, '2026-10-05 08:00-05', 120, 1),
    (2, 2, 105, 2, '2026-10-06 09:00-05', 60, 1),
    (3, 3, 106, 1, '2026-09-20 08:00-05', 30, 1);

INSERT INTO mod04_participacion_actividad (actividad_id, operario_id) VALUES (1, 2), (1, 3), (2, 2);

INSERT INTO mod04_material_actividad (id, actividad_id, material_id, cantidad, unidad_medida_id, origen,
    destino, usuario_creacion_id) VALUES
    (1, 1, 4, 2.5, 1, NULL, NULL, 1),
    (2, 2, 2, 10, 1, NULL, NULL, 1),
    (3, 1, 1, 5, 1, 'Jardín Central', 'Plaza de Armas', 1);

SELECT setval(pg_get_serial_sequence('mod04_personal', 'id'), 3);
SELECT setval(pg_get_serial_sequence('mod04_cuadrilla', 'id'), 1);
SELECT setval(pg_get_serial_sequence('mod04_parte_diario', 'id'), 3);
SELECT setval(pg_get_serial_sequence('mod04_actividad', 'id'), 3);
SELECT setval(pg_get_serial_sequence('mod04_material_actividad', 'id'), 3);
