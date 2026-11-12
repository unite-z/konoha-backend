-- RF-SER-001: el contacto del proveedor se separa en nombre, teléfono y correo (sugerencia de los JP).
-- Sin usuarios reales todavía: se reemplaza directamente, sin guardar el texto libre anterior.
ALTER TABLE mod05_proveedor_detalle
 DROP COLUMN contacto,
 ADD COLUMN contacto_nombre varchar(120) NOT NULL DEFAULT '',
 ADD COLUMN contacto_telefono varchar(30) NOT NULL DEFAULT '',
 ADD COLUMN contacto_correo varchar(254) NOT NULL DEFAULT '';
