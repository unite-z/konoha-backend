-- AV-364: código legible de cada ciclo (CC-2026-11): iniciales del servicio, año del inicio previsto
-- y número correlativo. Solo añade (expand): la versión anterior de la api inserta sin código.

-- Iniciales del servicio sin artículos ni preposiciones, hasta tres letras: «Corte de césped» es CC.
CREATE FUNCTION mod03_prefijo_servicio(nombre text) RETURNS text LANGUAGE sql IMMUTABLE AS $$
    SELECT coalesce(nullif(left(string_agg(upper(left(palabra, 1)), '' ORDER BY orden), 3), ''), 'SV')
    FROM regexp_split_to_table(translate(lower(nombre), 'áéíóúüñ', 'aeiouun'), '[^a-z0-9]+')
        WITH ORDINALITY AS t(palabra, orden)
    WHERE palabra <> ''
      AND palabra NOT IN ('a', 'al', 'con', 'de', 'del', 'e', 'el', 'en', 'la', 'las', 'los',
          'para', 'por', 'y')
$$;

ALTER TABLE mod03_programacion_servicio ADD COLUMN codigo varchar(20);

UPDATE mod03_programacion_servicio p SET codigo = x.codigo
FROM (
    SELECT p.id, mod03_prefijo_servicio(t.nombre) || '-'
        || extract(year FROM p.fecha_inicio_prevista)::int || '-'
        || lpad(row_number() OVER (
            PARTITION BY mod03_prefijo_servicio(t.nombre), extract(year FROM p.fecha_inicio_prevista)
            ORDER BY p.id)::text, 2, '0') codigo
    FROM mod03_programacion_servicio p JOIN mod05_tiposervicio t ON t.id = p.tipo_servicio_id
) x
WHERE x.id = p.id;

CREATE UNIQUE INDEX mod03_programacion_codigo ON mod03_programacion_servicio (codigo);
