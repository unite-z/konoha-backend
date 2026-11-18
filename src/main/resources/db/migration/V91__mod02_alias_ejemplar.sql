-- Alias (nombre) del ejemplar: un nombre fácil de recordar junto al código EJ-nnnnn.
ALTER TABLE mod02_ejemplar
    ADD COLUMN alias varchar(40)
        CHECK (alias IS NULL OR btrim(alias) <> '');

CREATE UNIQUE INDEX mod02_ejemplar_alias_uq
    ON mod02_ejemplar (lower(alias))
    WHERE alias IS NOT NULL;

-- La vista de lectura de MOD-05 expone también el alias (columna añadida al final).
CREATE OR REPLACE VIEW mod05_ejemplar AS
    SELECT id, codigo, area_verde_id, alias FROM mod02_ejemplar;
