-- AV-280 (RF-INC-008, RN-019): aviso empujado al jefe cuando una incidencia sencilla de personal
-- propio supera el objetivo (24 h) o vence (48 h). Una fila por incidencia y nivel garantiza que
-- el aviso no se repita aunque el proceso periódico se ejecute varias veces.

CREATE TABLE mod03_aviso_plazo (
    incidencia_id bigint NOT NULL REFERENCES mod03_incidencia (id) ON DELETE CASCADE,
    nivel varchar(20) NOT NULL CHECK (nivel IN ('SuperaObjetivo', 'Vencida')),
    fecha_hora timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (incidencia_id, nivel)
);
