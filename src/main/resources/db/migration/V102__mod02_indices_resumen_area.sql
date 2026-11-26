-- AV-431: el resumen del área (GET /api/v1/inventario/areas-verdes/{id}/resumen) filtra las
-- actividades de campo y las incidencias por área y las ordena por fecha. Sin estos índices, cada
-- apertura del panel recorre las tablas completas. Solo añade índices (expand).
CREATE INDEX IF NOT EXISTS mod04_actividad_area_fecha_idx
    ON mod04_actividad (area_verde_id, fecha_hora_actividad DESC);

CREATE INDEX IF NOT EXISTS mod03_incidencia_area_fecha_idx
    ON mod03_incidencia (area_verde_id, fecha_registro DESC);
