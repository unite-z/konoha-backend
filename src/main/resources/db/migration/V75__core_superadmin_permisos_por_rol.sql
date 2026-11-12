-- AV-381: rol superadmin oculto y matriz de permisos editable desde la aplicación.
-- Sustituye el supuesto ACC-S8 (matriz fija): el superadmin edita los permisos de cada rol.

INSERT INTO core_permiso (codigo, modulo, descripcion, es_sensible) VALUES
    ('CORE.ROL.GESTIONAR', 'MOD-01', 'Gestionar los permisos de cada rol', true)
ON CONFLICT (codigo) DO NOTHING;

-- Oculto: no se lista ni se asigna desde la API. Siempre tiene todos los permisos (RolesRepository).
INSERT INTO core_rol (codigo, nombre, descripcion, activo) VALUES
    ('superadmin', 'Superadministrador', 'Acceso total. Gestiona los permisos de cada rol. No se asigna desde la aplicación', true)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO core_rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM core_rol r CROSS JOIN core_permiso p WHERE r.codigo = 'superadmin'
ON CONFLICT DO NOTHING;

-- La cuenta nace con contraseña temporal (solo se guarda su hash) y debe cambiarla al entrar.
-- id fijo y alto: no choca con las cuentas copiadas de mod05_usuario con su mismo id ni mueve la secuencia.
INSERT INTO core_usuario (id, nombre_usuario, correo, nombre, credencial_hash, rol_id, activo, debe_cambiar_contrasena)
SELECT 999999, 'superadmin', 'superadmin@pucp.edu.pe', 'Superadministrador',
       '$2a$10$NdfqY.ZygnsYBsPBPrT7KuNPKIp0LTfNnGnUsjNz6SDKvEW41dO7a', r.id, true, true
FROM core_rol r
WHERE r.codigo = 'superadmin'
  AND NOT EXISTS (SELECT 1 FROM core_usuario u WHERE u.nombre_usuario = 'superadmin' OR lower(u.correo) = 'superadmin@pucp.edu.pe');
