-- Unicidad del catálogo administrable, sin reescribir nombres ni decisiones de V3/V27.
CREATE UNIQUE INDEX mod03_tipo_restriccion_nombre_normalizado
ON mod03_tipo_restriccion (lower(regexp_replace(btrim(nombre), '[[:space:]]+', ' ', 'g')));
