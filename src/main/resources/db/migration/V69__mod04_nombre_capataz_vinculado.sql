ALTER TABLE mod04_personal
    ALTER COLUMN nombres TYPE varchar(200),
    DROP CONSTRAINT mod04_personal_apellidos_check,
    ADD CONSTRAINT mod04_personal_apellidos_check
        CHECK (usuario_id IS NOT NULL OR length(trim(apellidos)) > 0);
