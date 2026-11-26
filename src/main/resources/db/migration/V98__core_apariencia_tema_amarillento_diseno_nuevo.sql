-- AV-414 (RD-02): la apariencia admite el tema «amarillento» y la cuenta guarda si quiere el diseño
-- nuevo. disenoNuevo es nulo = nunca eligió, true o false. Solo aditiva: se amplía el CHECK del tema
-- (y su longitud, porque «amarillento» no cabe en varchar(10)) y se añade una columna nullable sin
-- valor por defecto. Compatible con la versión anterior de la api, que nunca escribe el valor nuevo.
ALTER TABLE core_usuario
    DROP CONSTRAINT core_usuario_apariencia_tema_check,
    ALTER COLUMN apariencia_tema TYPE varchar(15),
    ADD CONSTRAINT core_usuario_apariencia_tema_check
        CHECK (apariencia_tema IN ('claro', 'oscuro', 'calido', 'amarillento', 'sistema')),
    ADD COLUMN apariencia_diseno_nuevo boolean;
