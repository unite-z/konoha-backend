-- El área histórica de tercerizados conserva su columna y clave foránea.
ALTER TABLE mod02_ejemplar
    ADD COLUMN area_verde_inventario_id bigint REFERENCES mod02_area_verde(id) ON DELETE RESTRICT;

-- Conserva los vínculos que ya resolvía el inventario antes de separar los catálogos.
UPDATE mod02_ejemplar e
SET area_verde_inventario_id = a.id
FROM mod02_area_verde a
WHERE a.id = e.area_verde_id;

CREATE INDEX mod02_ejemplar_area_inventario ON mod02_ejemplar (area_verde_inventario_id);
