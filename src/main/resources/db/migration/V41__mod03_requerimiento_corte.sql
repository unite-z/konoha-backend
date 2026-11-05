-- AV-263: al programar un corte con proveedor se registra su requerimiento en MOD-05 (CU-010 paso 7).
-- La programación guarda la atención creada o, si no se pudo crear, el motivo. Solo añade (expand).
ALTER TABLE mod03_programacion_servicio
    ADD COLUMN atencion_id bigint REFERENCES mod05_atenciontercerizada (id),
    ADD COLUMN motivo_sin_requerimiento varchar(200);

CREATE INDEX mod03_programacion_atencion ON mod03_programacion_servicio (atencion_id);
