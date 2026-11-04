-- AV-254 (RF-PLA-007, HU-005): el calendario se filtra por cuadrilla, sector y lugar. La programación
-- se relaciona con la cuadrilla de MOD-04 (personal propio) y con las áreas verdes de MOD-02; el
-- sector sale del historial vigente del área (mod02_historial_area_sector). Solo añade (expand).

ALTER TABLE mod03_programacion_servicio
    ADD CONSTRAINT mod03_programacion_cuadrilla_fk FOREIGN KEY (cuadrilla_id)
    REFERENCES mod04_cuadrilla (id) NOT VALID;

CREATE TABLE mod03_programacion_area (
    programacion_id bigint NOT NULL REFERENCES mod03_programacion_servicio (id) ON DELETE CASCADE,
    area_verde_id bigint NOT NULL REFERENCES mod02_area_verde (id),
    PRIMARY KEY (programacion_id, area_verde_id)
);

CREATE INDEX mod03_programacion_area_lugar ON mod03_programacion_area (area_verde_id);
CREATE INDEX mod03_programacion_cuadrilla ON mod03_programacion_servicio (cuadrilla_id);
