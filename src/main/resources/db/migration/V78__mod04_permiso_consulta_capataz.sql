-- AV-380: el capataz consulta los partes de su propia cuadrilla. El alcance se aplica en los
-- servicios que consumen el permiso; no se conceden la verificación ni la gestión de cuadrillas.
INSERT INTO core_rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM core_rol r
JOIN core_permiso p ON p.codigo = 'CAM.PARTE.CONSULTAR'
WHERE r.codigo = 'capataz'
ON CONFLICT DO NOTHING;
