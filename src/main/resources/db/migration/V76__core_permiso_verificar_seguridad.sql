-- AV-382: MOD-05 autoriza por permisos. La verificación de seguridad de una atención de riesgo (AV-377) la hacían
-- el jefe de sección y el ingeniero de campo por nombre de rol; ahora es un permiso con esos mismos roles.

INSERT INTO core_permiso (codigo, modulo, descripcion, es_sensible) VALUES
    ('SER.SEGURIDAD.VERIFICAR', 'MOD-05', 'Verificar la seguridad de una atención de riesgo', false)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO core_rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM core_rol r, core_permiso p
WHERE p.codigo = 'SER.SEGURIDAD.VERIFICAR' AND r.codigo IN ('jefe-seccion', 'ingeniero-campo')
ON CONFLICT DO NOTHING;
