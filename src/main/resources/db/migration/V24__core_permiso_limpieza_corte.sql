-- AV-252 (RF-COR-006): registrar la limpieza del ciclo de corte es de jefe, ingeniero y practicante,
-- no del capataz, que sí tiene PLA.CORTE.REGISTRAR para marcar jardines. Lo usa AV-262.

INSERT INTO core_permiso (codigo, modulo, descripcion, es_sensible) VALUES
    ('PLA.CORTE.LIMPIEZA', 'MOD-03', 'Registrar la limpieza del ciclo de corte', false);

INSERT INTO core_rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM core_rol r, core_permiso p
WHERE p.codigo = 'PLA.CORTE.LIMPIEZA' AND r.codigo IN ('jefe-seccion', 'ingeniero-campo', 'practicante');
