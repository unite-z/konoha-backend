-- AV-443 / RD-12: código automático e inmutable de cada área verde (AREA-0001, AREA-0002...).
-- Aditiva: la versión anterior de la api inserta áreas sin código y el valor por defecto lo asigna.
CREATE SEQUENCE mod02_area_verde_codigo_seq;

CREATE FUNCTION mod02_codigo_area_verde(numero bigint) RETURNS varchar(20)
    LANGUAGE sql IMMUTABLE
    AS $$ SELECT 'AREA-' || repeat('0', greatest(4 - length(numero::text), 0)) || numero::text $$;

ALTER TABLE mod02_area_verde ADD COLUMN codigo varchar(20);

-- Las áreas existentes reciben su código por orden de alta.
UPDATE mod02_area_verde a
SET codigo = mod02_codigo_area_verde(orden.n)
FROM (SELECT id, row_number() OVER (ORDER BY id) AS n FROM mod02_area_verde) orden
WHERE a.id = orden.id;

SELECT setval('mod02_area_verde_codigo_seq', greatest(count(*), 1), count(*) > 0)
FROM mod02_area_verde;

ALTER TABLE mod02_area_verde
    ALTER COLUMN codigo SET DEFAULT mod02_codigo_area_verde(nextval('mod02_area_verde_codigo_seq')),
    ALTER COLUMN codigo SET NOT NULL,
    ADD CONSTRAINT mod02_area_verde_codigo_unico UNIQUE (codigo);

ALTER SEQUENCE mod02_area_verde_codigo_seq OWNED BY mod02_area_verde.codigo;

CREATE FUNCTION mod02_area_verde_codigo_inmutable() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.codigo IS DISTINCT FROM OLD.codigo THEN
        RAISE EXCEPTION 'El código de un área verde no se puede cambiar';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER mod02_area_verde_codigo_inmutable
    BEFORE UPDATE OF codigo ON mod02_area_verde
    FOR EACH ROW EXECUTE FUNCTION mod02_area_verde_codigo_inmutable();
