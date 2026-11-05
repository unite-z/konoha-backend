-- AV-287 (fase A, parte de MOD-03): las tablas de planificación de MOD-03 apuntan a core_usuario,
-- el dueño de los usuarios, en lugar de a la copia mod05_usuario. Una cuenta creada solo en MOD-01
-- ya puede configurar, registrar restricciones y programar.
--
-- V6 copió mod05_usuario en core_usuario con los mismos id, así que las filas antiguas siguen
-- apuntando a la misma persona. Las restricciones nuevas se crean NOT VALID para no revalidar el
-- histórico al desplegar; solo se comprueban las filas nuevas. Compatible con la api anterior.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.conname, c.conrelid::regclass AS tabla, a.attname AS columna
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
        WHERE c.contype = 'f'
          AND c.confrelid = 'mod05_usuario'::regclass
          AND c.conrelid::regclass::text LIKE 'mod03\_%'
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tabla, r.conname);
        EXECUTE format(
            'ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES core_usuario (id) NOT VALID',
            r.tabla, r.conname, r.columna);
    END LOOP;
END $$;
