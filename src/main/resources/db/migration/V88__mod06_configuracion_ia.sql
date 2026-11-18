-- AV-395: proveedor y modelo de IA elegidos desde Configuración (una sola fila).
-- Sin fila, la api usa IA_PROVEEDOR y el modelo del api.env. Las claves nunca se guardan aquí.
CREATE TABLE mod06_configuracion_ia (
    id smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    proveedor varchar(20) NOT NULL CHECK (proveedor IN ('gemini', 'deepseek')),
    modelo varchar(80) NOT NULL,
    actualizado_por bigint REFERENCES core_usuario (id),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);
