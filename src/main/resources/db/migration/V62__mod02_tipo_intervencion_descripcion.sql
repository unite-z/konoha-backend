-- AV-313 · MOD-02: gestión del catálogo de tipos de intervención tercerizada (HU-MOD02-026,
-- RF-POD-006). Añade el alcance de cada tipo y evita duplicados que solo difieran en mayúsculas o
-- espacios. Los tipos no se borran: se desactivan y conservan sus intervenciones.
ALTER TABLE mod02_tipo_intervencion ADD COLUMN descripcion varchar(300);

CREATE UNIQUE INDEX mod02_tipo_intervencion_nombre_unico
    ON mod02_tipo_intervencion (lower(trim(nombre)));
