-- AV-317 (RF-ACT-011): verificación del jefe separada de la ejecución del capataz.
ALTER TABLE mod04_actividad
    ADD COLUMN verificacion_observaciones varchar(1000),
    ADD COLUMN verificado_por_usuario_id bigint REFERENCES core_usuario(id),
    ADD COLUMN fecha_verificacion timestamptz;

ALTER TABLE mod04_actividad ADD CONSTRAINT mod04_actividad_verificacion_completa CHECK (
    (verificacion_observaciones IS NULL AND verificado_por_usuario_id IS NULL AND fecha_verificacion IS NULL)
    OR
    (verificacion_observaciones IS NOT NULL AND verificado_por_usuario_id IS NOT NULL AND fecha_verificacion IS NOT NULL)
);
