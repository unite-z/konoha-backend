-- AV-384: estado propio y anulación auditable de actividades de campo.
ALTER TABLE mod04_actividad
    ADD COLUMN estado_actividad varchar(12),
    ADD COLUMN anulada_en timestamptz,
    ADD COLUMN anulada_por_usuario_id bigint REFERENCES core_usuario(id),
    ADD COLUMN anulacion_motivo varchar(1000);

UPDATE mod04_actividad
SET estado_actividad = CASE
    WHEN fecha_verificacion IS NULL THEN 'Registrada'
    ELSE 'Verificada'
END;

ALTER TABLE mod04_actividad
    ALTER COLUMN estado_actividad SET DEFAULT 'Registrada',
    ALTER COLUMN estado_actividad SET NOT NULL,
    ADD CONSTRAINT mod04_actividad_estado_valido CHECK (
        (estado_actividad = 'Registrada'
            AND verificacion_observaciones IS NULL
            AND verificado_por_usuario_id IS NULL
            AND fecha_verificacion IS NULL
            AND anulada_en IS NULL
            AND anulada_por_usuario_id IS NULL
            AND anulacion_motivo IS NULL)
        OR
        (estado_actividad = 'Verificada'
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

CREATE INDEX mod04_actividad_estado_idx ON mod04_actividad (estado_actividad, id);

ALTER TABLE core_registro_auditoria
    DROP CONSTRAINT core_registro_auditoria_accion_check,
    ADD CONSTRAINT core_registro_auditoria_accion_check CHECK (
        accion IN ('Alta', 'Modificacion', 'CambioEstado', 'CambioRol', 'BajaLogica', 'Anulacion')
    );

INSERT INTO core_permiso (codigo, modulo, descripcion, es_sensible) VALUES
    ('CAM.PARTE.ANULAR', 'MOD-04', 'Anular actividades de campo pendientes de verificación', false)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO core_rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM core_rol r
JOIN core_permiso p ON p.codigo = 'CAM.PARTE.ANULAR'
WHERE r.codigo IN ('jefe-seccion', 'superadmin')
ON CONFLICT DO NOTHING;
