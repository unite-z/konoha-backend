-- AV-277 / RF-INC-003: incidencia derivada a un encargo directo de MOD-04. Cada intento es una
-- fila; un encargo pertenece a una sola incidencia. El estado del encargo decide el de la incidencia.
CREATE TABLE mod03_encargo_incidencia (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incidencia_id bigint NOT NULL REFERENCES mod03_incidencia(id),
    encargo_id bigint NOT NULL UNIQUE REFERENCES mod04_encargo(id),
    usuario_id bigint NOT NULL REFERENCES core_usuario(id),
    fecha_hora timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX mod03_encargo_incidencia_incidencia ON mod03_encargo_incidencia (incidencia_id, id);
