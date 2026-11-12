-- AV-140: atributos de catastro, especie, ubicación y código histórico.
ALTER TABLE mod02_ejemplar
    ADD COLUMN referencia_catastro varchar(80),
    ADD COLUMN codigo_catastro varchar(80),
    ADD COLUMN codigo_placa_antigua varchar(80),
    ADD COLUMN especie_id bigint REFERENCES mod02_especie_vegetal(id) ON DELETE RESTRICT,
    ADD COLUMN tipo_vegetacion varchar(80),
    ADD COLUMN ubicacion varchar(200),
    ADD COLUMN ubicacion_geografica geometry(Point, 4326),
    ADD COLUMN cantidad integer NOT NULL DEFAULT 1,
    ADD CONSTRAINT mod02_ejemplar_cantidad_positiva CHECK (cantidad > 0);

CREATE SEQUENCE mod02_ejemplar_codigo_seq;
SELECT setval(
    'mod02_ejemplar_codigo_seq',
    coalesce(max(substring(codigo FROM '^EJ-([0-9]+)$')::bigint), 0) + 1,
    false
) FROM mod02_ejemplar;
ALTER TABLE mod02_ejemplar
    ALTER COLUMN codigo SET DEFAULT ('EJ-' || lpad(nextval('mod02_ejemplar_codigo_seq')::text, 5, '0'));

CREATE UNIQUE INDEX mod02_ejemplar_referencia_catastro_unica
    ON mod02_ejemplar (referencia_catastro)
    WHERE referencia_catastro IS NOT NULL AND length(trim(referencia_catastro)) > 0;
CREATE UNIQUE INDEX mod02_ejemplar_codigo_catastro_unico
    ON mod02_ejemplar (codigo_catastro)
    WHERE codigo_catastro IS NOT NULL AND length(trim(codigo_catastro)) > 0;
CREATE INDEX mod02_ejemplar_especie ON mod02_ejemplar (especie_id);
CREATE INDEX mod02_ejemplar_ubicacion_gist ON mod02_ejemplar USING GIST (ubicacion_geografica);
