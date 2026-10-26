-- Permite que el personal responsable de sectores tenga el rol operativo Capataz.
ALTER TABLE mod05_usuario_rol DROP CONSTRAINT mod05_usuario_rol_rol_check;
ALTER TABLE mod05_usuario_rol
    ADD CONSTRAINT mod05_usuario_rol_rol_check
    CHECK (rol IN ('Jefe de sección', 'Ingeniero de campo', 'Practicante autorizado', 'Capataz'));
