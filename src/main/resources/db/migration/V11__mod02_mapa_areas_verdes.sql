-- AV-172: correspondencia persistente entre polígonos del mapa, inventario y tipo de uso.
CREATE TABLE mod02_mapa_poligono (
    id_fuente uuid PRIMARY KEY,
    area_verde_id bigint REFERENCES mod02_area_verde(id) ON DELETE RESTRICT,
    fecha_modificacion timestamptz NOT NULL DEFAULT now(),
    usuario_modificacion_id bigint REFERENCES mod05_usuario(id)
);

CREATE TABLE mod02_mapa_uso_fuente (
    uso_fuente varchar(180) PRIMARY KEY,
    tipo_uso_id bigint REFERENCES mod02_tipo_uso(id) ON DELETE RESTRICT,
    fecha_modificacion timestamptz NOT NULL DEFAULT now(),
    usuario_modificacion_id bigint REFERENCES mod05_usuario(id),
    CONSTRAINT mod02_mapa_uso_fuente_no_vacio CHECK (length(trim(uso_fuente)) > 0)
);

-- Sembramos únicamente equivalencias directas. El resto se deja sin clasificar para revisión.
INSERT INTO mod02_mapa_uso_fuente (uso_fuente, tipo_uso_id)
SELECT valores.uso_fuente, tipo.id
FROM (VALUES
    ('Áreas de uso recreativo/descanso', 'Recreativo'),
    ('Áreas deportivas y recreación activa', 'Deportivo'),
    ('Uso Institucional', 'Institucional'),
    ('Áreas de uso administrativo', NULL),
    ('Áreas de manejo sostenible y reducción de consumo de agua', NULL),
    ('Áreas de conservación', NULL)
) AS valores(uso_fuente, nombre_tipo)
LEFT JOIN mod02_tipo_uso tipo ON lower(tipo.nombre) = lower(valores.nombre_tipo);
