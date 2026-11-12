-- AV-320 (RF-SER-009, CU-MOD05-006 y CU-MOD05-009): datos del informe final según el formato estándar vigente
-- del tipo de servicio (V73). Cada revisión guarda el formato con el que se registró, así que un cambio de formato
-- posterior no cambia cómo se interpretan los informes ya registrados.
--
-- Expand: columnas nulas. Las revisiones anteriores y los clientes que no envían datos quedan con NULL.
ALTER TABLE mod05_reporteproveedor
    ADD COLUMN formato_id bigint REFERENCES mod05_formato_reporte_servicio (id),
    -- Objeto {nombre_campo: valor}, en el orden del formato. Solo con formato_id.
    ADD COLUMN datos_formato jsonb,
    ADD CONSTRAINT mod05_reporte_datos_con_formato CHECK (datos_formato IS NULL OR formato_id IS NOT NULL),
    ADD CONSTRAINT mod05_reporte_datos_objeto CHECK (datos_formato IS NULL OR jsonb_typeof(datos_formato) = 'object');

CREATE INDEX mod05_reporte_formato ON mod05_reporteproveedor (formato_id) WHERE formato_id IS NOT NULL;
