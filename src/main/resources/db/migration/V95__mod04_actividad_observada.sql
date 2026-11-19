-- AV-402: «Verificada con observaciones» (`Observada`). Obliga a crear un encargo de seguimiento, que queda
-- vinculado a la actividad de origen para no perder la trazabilidad.
ALTER TABLE mod04_actividad DROP CONSTRAINT mod04_actividad_estado_valido;

ALTER TABLE mod04_actividad
    ADD CONSTRAINT mod04_actividad_estado_valido CHECK (
        (estado_actividad = 'Registrada'
            AND verificacion_observaciones IS NULL
            AND verificado_por_usuario_id IS NULL
            AND fecha_verificacion IS NULL
            AND anulada_en IS NULL
            AND anulada_por_usuario_id IS NULL
            AND anulacion_motivo IS NULL)
        OR
        (estado_actividad IN ('Verificada', 'Observada')
            AND verificacion_observaciones IS NOT NULL
            AND verificado_por_usuario_id IS NOT NULL
            AND fecha_verificacion IS NOT NULL
            AND anulada_en IS NULL
            AND anulada_por_usuario_id IS NULL
            AND anulacion_motivo IS NULL)
        OR
        (estado_actividad = 'Anulada'
            AND verificacion_observaciones IS NULL
            AND verificado_por_usuario_id IS NULL
            AND fecha_verificacion IS NULL
            AND anulada_en IS NOT NULL
            AND anulada_por_usuario_id IS NOT NULL
            AND anulacion_motivo IS NOT NULL
            AND btrim(anulacion_motivo) <> '')
    );

-- Un encargo de seguimiento nace de una sola actividad, y cada actividad observada tiene uno solo.
ALTER TABLE mod04_encargo
    ADD COLUMN actividad_origen_id bigint REFERENCES mod04_actividad (id);

CREATE UNIQUE INDEX mod04_encargo_actividad_origen_uq
    ON mod04_encargo (actividad_origen_id)
    WHERE actividad_origen_id IS NOT NULL;
