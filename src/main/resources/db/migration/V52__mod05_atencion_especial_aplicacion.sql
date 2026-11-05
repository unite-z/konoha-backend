-- RF-FIT-006: vínculo entre una atención y la marca de atención especial (RF-INV-011) que la originó,
-- cuando es una de sus aplicaciones adicionales programadas. Columna opcional: las atenciones normales
-- no la usan.
ALTER TABLE mod05_atenciontercerizada
    ADD COLUMN atencion_especial_id bigint NULL REFERENCES mod05_atencion_especial(id);

CREATE INDEX mod05_atenciontercerizada_atencion_especial ON mod05_atenciontercerizada (atencion_especial_id);
