-- AV-391: apariencia elegida por cada usuario en Configuración (tema y color de acento), para que se
-- cargue al iniciar sesión en cualquier dispositivo. Nulas: sin elegir, la interfaz usa la predeterminada.
ALTER TABLE core_usuario
    ADD COLUMN apariencia_tema varchar(10)
        CONSTRAINT core_usuario_apariencia_tema_check CHECK (apariencia_tema IN ('claro', 'oscuro', 'sistema')),
    ADD COLUMN apariencia_acento varchar(15)
        CONSTRAINT core_usuario_apariencia_acento_check
            CHECK (apariencia_acento IN ('institucional', 'azul', 'verde', 'morado', 'magenta'));
