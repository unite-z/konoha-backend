-- Escenario de RF-REP-001 (panel) y RF-REP-007 (mapa). Usuario 1 = jefe (lo crea ApiGestion).
-- Fechas fijas: todas las pruebas consultan del 2026-10-01 al 2026-10-31.
--
-- Resultados esperados con desde=2026-10-01&hasta=2026-10-31:
-- * Atenciones por espacio: cuentan las realizadas 1, 2, 6 y 7. Pares: 1→{1, 2}; 2→{2} (el ejemplar 1
--   está en el área 2 y no duplica); 6→{3}; 7→{sin espacio}. Total 5. Desglose: Plaza de Armas 2;
--   Campo Deportivo 1 y Jardín Central 1 (alfabético con el mismo total); Sin espacio 1. La 6 entra por
--   la zona horaria (31/10 22:00 en Lima) y la 5 queda fuera (septiembre).
-- * Cumplimiento de tercerizados: atenciones del periodo 1, 2, 3, 4, 6 y 7 (la 4 por su fecha de
--   creación). Total 6, ejecutadas 4 → "66.67". Desglose: Requerimiento registrado 1, En ejecución 1,
--   Ejecutado pendiente de reporte 2, Reporte adjunto 2.
-- * area_verde_id=2: atenciones por espacio "2" (pares 1→2 y 2→2); cumplimiento "100.00" (1 y 2).
TRUNCATE mod05_proveedor, mod05_tiposervicio, mod05_areaverde, mod05_unidadmedida, mod05_archivo
    RESTART IDENTITY CASCADE;

INSERT INTO mod05_tiposervicio (id, nombre, ejecutor, es_riesgo, activo) VALUES
    (1, 'Corte de césped', 'Tercerizado', false, true),
    (2, 'Control fitosanitario', 'Tercerizado', false, true),
    (3, 'Poda mayor programada', 'Tercerizado', false, true),
    (4, 'Poda de altura a demanda', 'Tercerizado', true, true),
    (5, 'Mantenimiento de jardines', 'PersonalPropio', false, true),
    (6, 'Poda menor', 'PersonalPropio', false, true);

INSERT INTO mod05_proveedor (id, razon_social, ruc, activo) VALUES
    (1, 'Jardines del Sur S.A.C. (prueba)', '20100000001', true);

INSERT INTO mod05_contrato (id, numero, vigencia_inicio, vigencia_fin, proveedor_id, tipo_servicio_id)
VALUES
    (1, 'CT-2026-001', '2026-01-01', '2026-12-31', 1, 1),
    (2, 'CT-2026-002', '2026-01-01', '2026-12-31', 1, 2),
    (3, 'CT-2026-003', '2026-01-01', '2026-12-31', 1, 3),
    (4, 'CT-2026-004', '2026-01-01', '2026-12-31', 1, 4);

INSERT INTO mod05_areaverde (id, nombre, activo) VALUES
    (1, 'Jardín Central', true),
    (2, 'Plaza de Armas', true),
    (3, 'Campo Deportivo', true);

INSERT INTO mod05_ejemplar (id, codigo, area_verde_id) VALUES
    (1, 'EJ-001', 2),
    (2, 'EJ-002', NULL);

INSERT INTO mod05_atenciontercerizada (id, fecha_creacion, fecha_modificacion, codigo, clase,
    descripcion, origen, numero_orden_compra, fecha_hora_inicio, estado, es_riesgo,
    comentarios_acuerdo_unidad, solicitado_por_id, usuario_creacion_id, usuario_modificacion_id,
    contrato_id, tipo_servicio_id) VALUES
    (1, '2026-10-01 08:00-05', '2026-10-01 08:00-05', 'AT-001', 'Programada', 'Prueba', 'Demanda', '',
        '2026-10-05 09:00-05', 'Reporte adjunto', false, '', 1, 1, 1, 2, 2),
    (2, '2026-10-01 08:00-05', '2026-10-01 08:00-05', 'AT-002', 'Programada', 'Prueba', 'Demanda', '',
        '2026-10-10 09:00-05', 'Ejecutado pendiente de reporte', true, '', 1, 1, 1, 3, 3),
    (3, '2026-10-01 08:00-05', '2026-10-01 08:00-05', 'AT-003', 'Programada', 'Prueba', 'Demanda', '',
        '2026-10-12 09:00-05', 'En ejecución', false, '', 1, 1, 1, 1, 1),
    (4, '2026-10-03 08:00-05', '2026-10-03 08:00-05', 'AT-004', 'Programada', 'Prueba', 'Demanda', '',
        NULL, 'Requerimiento registrado', false, '', 1, 1, 1, 2, 2),
    (5, '2026-09-15 08:00-05', '2026-09-15 08:00-05', 'AT-005', 'Programada', 'Prueba', 'Demanda', '',
        '2026-09-20 09:00-05', 'Reporte adjunto', true, '', 1, 1, 1, 4, 4),
    (6, '2026-10-20 08:00-05', '2026-10-20 08:00-05', 'AT-006', 'Programada', 'Prueba', 'Demanda', '',
        '2026-11-01 03:00:00+00', 'Reporte adjunto', false, '', 1, 1, 1, 1, 1),
    (7, '2026-10-14 08:00-05', '2026-10-14 08:00-05', 'AT-007', 'Programada', 'Prueba', 'Demanda', '',
        '2026-10-15 09:00-05', 'Ejecutado pendiente de reporte', false, '', 1, 1, 1, 1, 1);

-- Alcance (área o ejemplar, nunca ambos). La 7 no tiene alcance.
INSERT INTO mod05_alcanceatencion (id, atencion_id, area_verde_id, ejemplar_id) VALUES
    (1, 1, 1, NULL),
    (2, 1, NULL, 1),
    (3, 2, 2, NULL),
    (4, 2, NULL, 1),
    (5, 3, 3, NULL),
    (6, 4, 1, NULL),
    (7, 5, 2, NULL),
    (8, 6, 3, NULL);

-- Reporte del proveedor de AT-001 con dos archivos asociados.
INSERT INTO mod05_reporteproveedor (id, fecha_creacion, fecha_modificacion, revision, fecha_recepcion,
    resumen, atencion_id, usuario_creacion_id, usuario_modificacion_id) VALUES
    (1, '2026-10-06 10:00-05', '2026-10-06 10:00-05', 0, '2026-10-06', 'Informe de prueba', 1, 1, 1);

INSERT INTO mod05_archivo (id, nombre_original, tipo_mime, tamano_bytes, ruta_almacenamiento,
    hash_contenido) VALUES
    (1, 'informe.pdf', 'application/pdf', 100, 'pruebas/gestion/informe.pdf', repeat('a', 64)),
    (2, 'foto.jpg', 'image/jpeg', 200, 'pruebas/gestion/foto.jpg', repeat('b', 64));

INSERT INTO mod05_archivoasociado (id, categoria, descripcion, alcance_id, archivo_id, reporte_id)
VALUES
    (1, 'InformeFinal', 'Informe final', NULL, 1, 1),
    (2, 'EvidenciaEspacio', 'Evidencia del espacio', 1, 2, 1);

-- Los ids son explícitos: las secuencias siguen tras el último para que otras inserciones no choquen.
SELECT setval(pg_get_serial_sequence('mod05_tiposervicio', 'id'), 6);
SELECT setval(pg_get_serial_sequence('mod05_proveedor', 'id'), 1);
SELECT setval(pg_get_serial_sequence('mod05_contrato', 'id'), 4);
SELECT setval(pg_get_serial_sequence('mod05_areaverde', 'id'), 3);
SELECT setval(pg_get_serial_sequence('mod05_ejemplar', 'id'), 2);
SELECT setval(pg_get_serial_sequence('mod05_atenciontercerizada', 'id'), 7);
SELECT setval(pg_get_serial_sequence('mod05_alcanceatencion', 'id'), 8);
SELECT setval(pg_get_serial_sequence('mod05_reporteproveedor', 'id'), 1);
SELECT setval(pg_get_serial_sequence('mod05_archivo', 'id'), 2);
SELECT setval(pg_get_serial_sequence('mod05_archivoasociado', 'id'), 2);
