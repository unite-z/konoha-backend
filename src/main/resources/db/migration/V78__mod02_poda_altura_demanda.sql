-- AV-312 · MOD-02: poda de altura a demanda vinculada al ejemplar (HU-MOD02-024, RF-POD-005).
-- Modelo de BD v1, 4.2.19: motivo y altura de referencia de la intervención AlturaDemanda. La
-- justificación es obligatoria (en el servicio) cuando el ejemplar no llega a 5 m (RN-005).
-- La solicitud se registra sin fecha de ejecución: la fecha y la foto llegan al ejecutarse.
ALTER TABLE mod02_intervencion_arbolado
    ADD COLUMN motivo varchar(16)
        CHECK (motivo IN ('Riesgo', 'Obstruccion', 'PlantaMuerta', 'Reduccion')),
    ADD COLUMN altura_referencia_m numeric(6, 2) CHECK (altura_referencia_m > 0),
    ADD COLUMN justificacion varchar(300);

CREATE INDEX mod02_intervencion_atencion ON mod02_intervencion_arbolado (atencion_id)
    WHERE atencion_id IS NOT NULL;
