ALTER TABLE mod04_encargo
 ADD COLUMN estado varchar(20) NOT NULL DEFAULT 'Por completar'
 CHECK (estado IN ('Por completar','En curso','Completado','Observado','Cancelado'));

CREATE TABLE mod04_encargo_cambio_estado (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 encargo_id bigint NOT NULL REFERENCES mod04_encargo(id),
 estado_anterior varchar(20) NOT NULL,
 estado_nuevo varchar(20) NOT NULL,
 observaciones varchar(1000) NOT NULL,
 usuario_id bigint NOT NULL REFERENCES core_usuario(id),
 fecha timestamptz NOT NULL
);

CREATE INDEX mod04_encargo_cambio_estado_encargo ON mod04_encargo_cambio_estado(encargo_id,id);
