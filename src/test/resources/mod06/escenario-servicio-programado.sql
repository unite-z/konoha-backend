-- Escenario de RF-REP-008 y RF-REP-010. Se carga después de escenario-gestion.sql (tipos, contratos, áreas y
-- atenciones AT-001 a AT-007). Usuario 1 = jefe (core_usuario y mod05_usuario).
--
-- RF-REP-008, tipo_servicio_id=2 (Control fitosanitario), octubre de 2026:
-- * Atenciones AT-001 (Reporte adjunto, con informe y evidencia) y AT-004 (Requerimiento registrado, por su
--   fecha de creación). Avance: 2 programadas, 1 ejecutada, 1 pendiente → "50.00".
-- * Conformidad: AT-001 Conforme sobre la revisión 0 → conformes 1.
-- * Plagas: «Pulgón de prueba» en AT-001.
--
-- RF-REP-010, octubre de 2026:
-- * P1 (Control fitosanitario, 1–10/10) con AT-001 sin fin → EnCurso.
-- * P2 (Corte de césped, 1–3/10, Completada, real 1–5/10, sin atención) → EjecutadoConDesviacion, 2 días.
-- * P3 (Poda mayor, 20–25/10, Programada) → Pendiente. P4 (Corte, Cancelada) → Cancelado.
-- * Sin programación: AT-002, AT-003, AT-007 y AT-006 (31/10 22:00 en Lima). Cumplimiento 1 / (4 − 1) → "33.33".
INSERT INTO mod05_plaga (nombre, activo) VALUES ('Pulgón de prueba', true) ON CONFLICT DO NOTHING;
INSERT INTO mod05_atencion_plaga (atencion_id, plaga_id) SELECT 1, id FROM mod05_plaga WHERE nombre = 'Pulgón de prueba';

INSERT INTO mod05_conformidad (atencion_id, reporte_id, resultado, fecha_conformidad, usuario_id)
VALUES (1, 1, 'Conforme', '2026-10-07', 1);

INSERT INTO mod03_programacion_servicio (id, codigo, tipo_servicio_id, proveedor_id, estacion,
    fecha_inicio_prevista, fecha_fin_prevista, fecha_inicio_real, fecha_fin_real, ventana_mes, estado,
    atencion_id, creado_por_id, usuario_creacion_id, usuario_modificacion_id) VALUES
    (901, 'CF-2026-901', 2, 1, 'Primavera', '2026-10-01', '2026-10-10', NULL, NULL, '2026-10', 'EnCurso', 1, 1, 1, 1),
    (902, 'CC-2026-902', 1, 1, 'Primavera', '2026-10-01', '2026-10-03', '2026-10-01', '2026-10-05', '2026-10',
        'Completada', NULL, 1, 1, 1),
    (903, 'PM-2026-903', 3, 1, 'Primavera', '2026-10-20', '2026-10-25', NULL, NULL, '2026-10', 'Programada',
        NULL, 1, 1, 1),
    (904, 'CC-2026-904', 1, 1, 'Primavera', '2026-10-12', '2026-10-14', NULL, NULL, '2026-10', 'Cancelada',
        NULL, 1, 1, 1);
