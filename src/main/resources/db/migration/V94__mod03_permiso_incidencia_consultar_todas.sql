-- AV-407: quien no tiene este permiso (el capataz) solo ve las incidencias suyas y las de su cuadrilla.
INSERT INTO core_permiso (codigo, modulo, descripcion, es_sensible) VALUES
    ('PLA.INCIDENCIA.CONSULTAR_TODAS', 'MOD-03', 'Consultar todas las incidencias, no solo las propias y las de su cuadrilla', false)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO core_rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM core_rol r
JOIN core_permiso p ON p.codigo = 'PLA.INCIDENCIA.CONSULTAR_TODAS'
WHERE r.codigo IN ('jefe-seccion', 'ingeniero-campo', 'practicante', 'superadmin')
ON CONFLICT DO NOTHING;
