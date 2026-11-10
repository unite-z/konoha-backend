-- AV-172: usa en el catálogo los nombres de uso del GeoJSON y conserva las
-- asociaciones históricas de las áreas y de las equivalencias existentes.
DO $$
DECLARE
    par record;
    id_anterior bigint;
    id_nuevo bigint;
BEGIN
    FOR par IN
        SELECT * FROM (VALUES
            ('Recreativo', 'Áreas de uso recreativo/descanso'),
            ('Institucional', 'Uso Institucional'),
            ('Deportivo', 'Áreas deportivas y recreación activa')
        ) AS equivalencias(anterior, nuevo)
    LOOP
        SELECT id INTO id_anterior
        FROM mod02_tipo_uso
        WHERE lower(nombre) = lower(par.anterior)
          AND activo = true
        ORDER BY id
        LIMIT 1;

        IF id_anterior IS NULL THEN
            CONTINUE;
        END IF;

        SELECT id INTO id_nuevo
        FROM mod02_tipo_uso
        WHERE lower(nombre) = lower(par.nuevo) AND activo = true AND id <> id_anterior
        ORDER BY id
        LIMIT 1;

        IF id_nuevo IS NULL THEN
            UPDATE mod02_tipo_uso
            SET nombre = par.nuevo, activo = true, fecha_modificacion = now()
            WHERE id = id_anterior;
        ELSE
            UPDATE mod02_area_verde
            SET tipo_uso_id = id_nuevo
            WHERE tipo_uso_id = id_anterior;

            UPDATE mod02_mapa_uso_fuente
            SET tipo_uso_id = id_nuevo, fecha_modificacion = now()
            WHERE tipo_uso_id = id_anterior;

            UPDATE mod02_tipo_uso
            SET activo = false, fecha_modificacion = now()
            WHERE id = id_anterior;
        END IF;
    END LOOP;
END $$;

INSERT INTO mod02_tipo_uso (nombre)
SELECT valores.nombre
FROM (VALUES
    ('Áreas de conservación'),
    ('Áreas de manejo sostenible y reducción de consumo de agua'),
    ('Áreas de uso administrativo'),
    ('Áreas de uso recreativo/descanso'),
    ('Áreas deportivas y recreación activa'),
    ('Uso Institucional')
) AS valores(nombre)
WHERE NOT EXISTS (
    SELECT 1 FROM mod02_tipo_uso existente
    WHERE lower(existente.nombre) = lower(valores.nombre)
);
