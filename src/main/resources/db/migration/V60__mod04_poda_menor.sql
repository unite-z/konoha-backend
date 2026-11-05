-- AV-315 (RF-ACT-008): poda menor vinculada al catastro o pendiente de vinculación.
INSERT INTO mod04_tipo_actividad(nombre, activo, clase_id)
SELECT 'Poda menor', true, 4
WHERE NOT EXISTS (
    SELECT 1 FROM mod04_tipo_actividad
    WHERE clase_id = 4 AND lower(trim(nombre)) = 'poda menor'
);

ALTER TABLE mod04_actividad
    ADD COLUMN ejemplar_id bigint REFERENCES mod02_ejemplar(id),
    ADD COLUMN pendiente_vinculacion boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT mod04_actividad_referencia_poda_valida
        CHECK (ejemplar_id IS NULL OR NOT pendiente_vinculacion);

CREATE INDEX mod04_actividad_ejemplar_idx
    ON mod04_actividad(ejemplar_id)
    WHERE ejemplar_id IS NOT NULL;
