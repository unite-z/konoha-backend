-- El color es parte del catálogo para que el mapa y las áreas compartan la misma clasificación visual.
ALTER TABLE mod02_tipo_uso
    ADD COLUMN color varchar(7) NOT NULL DEFAULT '#455A64',
    ADD CONSTRAINT mod02_tipo_uso_color_hex CHECK (color ~ '^#[0-9A-Fa-f]{6}$');

UPDATE mod02_tipo_uso
SET color = CASE lower(nombre)
    WHEN 'áreas de conservación' THEN '#1B5E20'
    WHEN 'áreas de manejo sostenible y reducción de consumo de agua' THEN '#1565C0'
    WHEN 'áreas de uso administrativo' THEN '#9A6700'
    WHEN 'áreas de uso recreativo/descanso' THEN '#6A1B9A'
    WHEN 'áreas deportivas y recreación activa' THEN '#00838F'
    WHEN 'uso institucional' THEN '#AD3A16'
    WHEN 'ornamental' THEN '#455A64'
    ELSE color
END;
