-- AV-287: MOD-02 pasa a ser dueño del inventario de ejemplares.
-- La vista conserva el contrato de lectura que MOD-05 usa durante la transición.
ALTER TABLE mod05_ejemplar RENAME TO mod02_ejemplar;

CREATE VIEW mod05_ejemplar AS
    SELECT id, codigo, area_verde_id FROM mod02_ejemplar;
